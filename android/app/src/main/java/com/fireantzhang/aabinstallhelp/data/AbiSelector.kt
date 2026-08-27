package com.fireantzhang.aabinstallhelp.data

import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile

object AbiSelector {
    val knownAbis: Set<String> = linkedSetOf(
        "arm64-v8a",
        "armeabi-v7a",
        "armeabi",
        "x86_64",
        "x86",
        "mips64",
        "mips",
        "riscv64"
    )

    fun detectAbis(aab: File): Set<String> {
        val found = linkedSetOf<String>()
        ZipFile(aab).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                collectAbi(entry.name, found)
                if (!entry.isDirectory && entry.name.endsWith(".zip", ignoreCase = true)) {
                    zip.getInputStream(entry).use { input ->
                        ZipInputStream(BufferedInputStream(input)).use { inner ->
                            while (true) {
                                val nested = inner.nextEntry ?: break
                                collectAbi(nested.name, found)
                                inner.closeEntry()
                            }
                        }
                    }
                }
            }
        }
        return found
    }

    fun pickPreferred(deviceAbis: List<String>, aabAbis: Set<String>): String {
        val devices = deviceAbis.map { it.trim() }.filter { it.isNotEmpty() }
        if (aabAbis.isNotEmpty()) {
            devices.firstOrNull { it in aabAbis }?.let { return it }
        }
        return devices.firstOrNull() ?: aabAbis.firstOrNull() ?: "arm64-v8a"
    }

    internal fun collectAbi(path: String, into: MutableSet<String>) {
        val parts = path.replace('\\', '/').split('/')
        val libIdx = parts.indexOfFirst { it.equals("lib", ignoreCase = true) }
        if (libIdx < 0 || libIdx + 1 >= parts.size) return
        val abi = parts[libIdx + 1]
        if (abi in knownAbis) into.add(abi)
    }
}
