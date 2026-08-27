package com.fireantzhang.aabinstallhelp.data

import android.content.Context
import java.io.File

object RuntimeAssets {
    data class Paths(
        val aapt2: File,
        val keyPk8: File,
        val certDer: File
    )

    fun ensure(context: Context): Paths {
        val aapt2 = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
        if (!aapt2.isFile) {
            throw IllegalStateException("找不到 aapt2：${aapt2.absolutePath}")
        }
        if (!aapt2.canExecute()) {
            aapt2.setExecutable(true, true)
        }
        if (!aapt2.canExecute()) {
            throw IllegalStateException("aapt2 不可执行：${aapt2.absolutePath}")
        }
        val dir = File(context.filesDir, "runtime").apply { mkdirs() }
        val keyPk8 = File(dir, "debug-key.pk8")
        val certDer = File(dir, "debug-cert.der")
        copyAsset(context, "runtime/debug-key.pk8", keyPk8)
        copyAsset(context, "runtime/debug-cert.der", certDer)
        return Paths(aapt2 = aapt2, keyPk8 = keyPk8, certDer = certDer)
    }

    private fun copyAsset(context: Context, assetName: String, dest: File) {
        context.assets.open(assetName).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
