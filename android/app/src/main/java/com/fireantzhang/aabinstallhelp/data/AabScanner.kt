package com.fireantzhang.aabinstallhelp.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object AabScanner {
    private val skipNames = setOf(
        "android",
        ".thumbnails",
        ".trash",
        ".trashed",
        "lost.dir",
        "lost+found"
    )

    fun scan(context: Context, cancel: () -> Boolean = { false }): List<AabFile> {
        val found = LinkedHashMap<String, AabFile>()
        val roots = candidateRoots(context)
        for (root in roots) {
            if (cancel()) break
            walk(root, 0, found, cancel, maxDepth = 6)
        }
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists()) {
            walk(primary, 0, found, cancel, maxDepth = 10)
        }
        collectFromMediaStore(context, found, cancel)
        return dedupe(found.values)
    }

    fun dedupe(files: Collection<AabFile>): List<AabFile> {
        val byPath = LinkedHashMap<String, AabFile>()
        for (item in files) {
            putAab(byPath, item)
        }
        val byNameSize = LinkedHashMap<String, AabFile>()
        for (item in byPath.values) {
            val contentKey = item.name.lowercase() + "|" + item.sizeBytes
            val existing = byNameSize[contentKey]
            if (existing == null || prefer(item, existing)) {
                byNameSize[contentKey] = item
            }
        }
        return byNameSize.values.sortedByDescending { it.lastModified }
    }

    fun candidateRoots(context: Context): List<File> {
        val roots = LinkedHashSet<File>()
        fun add(file: File?) {
            if (file != null && file.exists() && file.isDirectory) {
                roots.add(file.canonicalFileSafe())
            }
        }
        fun addUnder(base: File?, vararg relative: String) {
            if (base == null) return
            relative.forEach { add(File(base, it)) }
        }

        val primary = Environment.getExternalStorageDirectory()
        val volumes = linkedSetOf<File>()
        if (primary != null) volumes.add(primary)
        volumes.add(File("/sdcard"))
        volumes.add(File("/storage/emulated/0"))
        volumes.add(File("/storage/self/primary"))
        File("/storage").listFiles()?.forEach { volume ->
            if (volume.isDirectory && (volume.name.contains("-") || volume.name == "emulated")) {
                if (volume.name == "emulated") {
                    volumes.add(File(volume, "0"))
                } else {
                    volumes.add(volume)
                }
            }
        }

        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
        add(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))

        for (vol in volumes) {
            addUnder(
                vol,
                "Download", "Downloads", "download", "downloads",
                "Documents", "Document",
                "Pictures", "DCIM"
            )
            val download = File(vol, "Download")
            if (download.isDirectory) {
                download.listFiles()?.forEach { child ->
                    if (child.isDirectory) add(child)
                }
            }
            addUnder(
                vol,
                "Download/Chrome",
                "Download/chrome",
                "Download/Edge",
                "Download/Firefox",
                "Download/Brave",
                "Download/Samsung",
                "Download/UCDownloads",
                "Download/QQBrowser",
                "Download/quark",
                "Download/baidu",
                "Download/Browser",
                "Download/浏览器"
            )
            addUnder(
                vol,
                "tencent/MicroMsg/Download",
                "tencent/MicroMsg/WeiXin",
                "WeiXin",
                "WeChat",
                "Download/WeiXin",
                "Download/WeChat",
                "Download/微信",
                "Pictures/WeiXin",
                "Android/data/com.tencent.mm/MicroMsg/Download",
                "Android/data/com.tencent.mm/Download"
            )
            val wechatMsg = File(vol, "Android/data/com.tencent.mm/MicroMsg")
            if (wechatMsg.isDirectory) {
                wechatMsg.listFiles()?.forEach { hashDir ->
                    if (hashDir.isDirectory) {
                        add(File(hashDir, "Download"))
                        add(File(hashDir, "attachment"))
                    }
                }
            }
            addUnder(
                vol,
                "Lark",
                "lark",
                "Feishu",
                "feishu",
                "飞书",
                "Download/Lark",
                "Download/Feishu",
                "Download/飞书",
                "Documents/Lark",
                "Documents/Feishu",
                "Android/data/com.ss.android.lark/files",
                "Android/data/com.ss.android.lark/files/Download",
                "Android/data/com.ss.android.lark/files/download",
                "Android/data/com.ss.android.lark/files/Documents",
                "Android/data/com.larksuite.suite/files",
                "Android/data/com.larksuite.suite/files/Download",
                "Android/data/com.larksuite.suite/files/download"
            )
            addUnder(
                vol,
                "DingTalk",
                "DingDing",
                "dingtalk",
                "钉钉",
                "Download/DingTalk",
                "Download/DingDing",
                "Download/钉钉",
                "Android/data/com.alibaba.android.rimet/files",
                "Android/data/com.alibaba.android.rimet/files/Download",
                "Android/data/com.alibaba.android.rimet/files/download",
                "Android/data/com.alibaba.android.rimet/files/downloads",
                "Android/data/com.alibaba.android.rimet/files/Documents"
            )
        }

        listOf(
            "/mnt/shared",
            "/mnt/shared/Pictures",
            "/mnt/shared/Download",
            "/mnt/shared/Other",
            "/mnt/windows/BstSharedFolder",
            "/sdcard/windows/BstSharedFolder",
            "/mnt/windows/BstSharedFolder/Pictures",
            "/mnt/shell/emulated/0/Download",
            "/mnt/shell/emulated/0/Pictures",
            "/data/media/0/Download",
            "/data/media/0/Pictures",
            "/storage/emulated/0/MuMuSharedFolder"
        ).forEach { add(File(it)) }

        return roots.toList()
    }

    private fun collectFromMediaStore(
        context: Context,
        out: MutableMap<String, AabFile>,
        cancel: () -> Boolean
    ) {
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = mutableListOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            if (Build.VERSION.SDK_INT >= 29) {
                projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            @Suppress("DEPRECATION")
            projection.add(MediaStore.MediaColumns.DATA)
            val cursor = context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.aab"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            ) ?: return
            cursor.use {
                val nameIdx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIdx = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataIdx = it.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relIdx = if (Build.VERSION.SDK_INT >= 29) {
                    it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    -1
                }
                val primary = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
                while (it.moveToNext()) {
                    if (cancel()) return
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    if (name.isNullOrBlank() || !name.lowercase().endsWith(".aab")) continue
                    var path = if (dataIdx >= 0) it.getString(dataIdx) else null
                    if (path.isNullOrBlank() && relIdx >= 0) {
                        val rel = it.getString(relIdx)?.trimEnd('/') ?: ""
                        path = if (rel.isEmpty()) "$primary/$name" else "$primary/$rel/$name"
                    }
                    if (path.isNullOrBlank()) continue
                    val file = File(path)
                    val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else if (file.exists()) file.length() else 0L
                    val modified = if (dateIdx >= 0 && it.getLong(dateIdx) > 0) {
                        it.getLong(dateIdx) * 1000
                    } else if (file.exists()) {
                        file.lastModified()
                    } else {
                        0L
                    }
                    putAab(
                        out,
                        AabFile(path = path, name = name, sizeBytes = size, lastModified = modified)
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun walk(
        dir: File,
        depth: Int,
        out: MutableMap<String, AabFile>,
        cancel: () -> Boolean,
        maxDepth: Int
    ) {
        if (cancel() || depth > maxDepth) return
        val name = dir.name.lowercase()
        if (depth > 0 && (name.startsWith('.') || skipNames.contains(name))) {
            return
        }
        if (depth == 1 && name == "android") {
            return
        }
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (cancel()) return
            if (child.isDirectory) {
                walk(child, depth + 1, out, cancel, maxDepth)
            } else if (child.isFile && child.name.lowercase().endsWith(".aab")) {
                putAab(
                    out,
                    AabFile(
                        path = child.absolutePath,
                        name = child.name,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified()
                    )
                )
            }
        }
    }

    private fun putAab(out: MutableMap<String, AabFile>, item: AabFile) {
        val key = normalizeStoragePath(item.path)
        val existing = out[key]
        if (existing == null || prefer(item, existing)) {
            out[key] = item
        }
    }

    private fun prefer(candidate: AabFile, current: AabFile): Boolean {
        val candidateScore = pathScore(candidate.path)
        val currentScore = pathScore(current.path)
        if (candidateScore != currentScore) return candidateScore < currentScore
        return candidate.path.length < current.path.length
    }

    private fun pathScore(path: String): Int {
        val n = normalizeStoragePath(path).lowercase()
        return when {
            n.contains("/download/") || n.endsWith("/download") -> 0
            n.contains("/downloads/") -> 0
            n.contains("/documents/") -> 1
            n.contains("/android/data/") -> 4
            n.contains("/pictures/") || n.contains("/dcim/") -> 3
            else -> 2
        }
    }

    private fun normalizeStoragePath(path: String): String {
        var p = path.replace('\\', '/')
        while (p.contains("//")) p = p.replace("//", "/")
        val aliases = listOf(
            "/storage/self/primary/" to "/storage/emulated/0/",
            "/sdcard/" to "/storage/emulated/0/",
            "/mnt/sdcard/" to "/storage/emulated/0/",
            "/mnt/shell/emulated/0/" to "/storage/emulated/0/",
            "/data/media/0/" to "/storage/emulated/0/"
        )
        for ((from, to) in aliases) {
            if (p.startsWith(from)) {
                p = to + p.substring(from.length)
                break
            }
            if (p == from.trimEnd('/')) {
                p = to.trimEnd('/')
                break
            }
        }
        return try {
            File(p).canonicalFile.absolutePath.replace('\\', '/')
        } catch (_: Throwable) {
            p
        }
    }

    private fun File.canonicalFileSafe(): File {
        return try {
            canonicalFile
        } catch (_: Throwable) {
            absoluteFile
        }
    }
}
