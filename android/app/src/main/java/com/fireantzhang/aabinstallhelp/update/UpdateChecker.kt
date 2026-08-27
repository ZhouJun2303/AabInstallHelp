package com.fireantzhang.aabinstallhelp.update

import com.fireantzhang.aabinstallhelp.BuildConfig
import com.fireantzhang.aabinstallhelp.data.UpdateInfo
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateChecker {
    fun check(): UpdateInfo? {
        val api = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = (api.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("User-Agent", "AabInstalllHelp/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val tag = root.optString("tag_name")
            val version = tag.trimStart('v', 'V')
            if (!isNewer(version, BuildConfig.VERSION_NAME)) return null
            val assets = root.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var sumsUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.contains("-android.apk", ignoreCase = true)) apkUrl = url
                if (name.equals("SHA256SUMS.txt", ignoreCase = true)) sumsUrl = url
            }
            if (apkUrl.isNullOrBlank()) return null
            val sha = sumsUrl?.let { readSha(it, apkUrl.substringAfterLast('/')) }
            return UpdateInfo(
                tag = tag,
                version = version,
                downloadUrl = apkUrl,
                sha256 = sha,
                notes = root.optString("body")
            )
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "AabInstalllHelp/${BuildConfig.VERSION_NAME}")
        }
        try {
            val total = conn.contentLengthLong
            var copied = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        copied += n
                        onProgress(copied, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(text: String): List<Int> =
            text.split('.').map { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun readSha(url: String, assetName: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "AabInstalllHelp/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            text.lineSequence().map { it.trim() }.firstOrNull { it.endsWith(assetName) }
                ?.substringBefore(" ")
                ?.trim()
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
