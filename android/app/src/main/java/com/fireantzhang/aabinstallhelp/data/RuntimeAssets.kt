package com.fireantzhang.aabinstallhelp.data

import android.content.Context
import android.os.Build
import java.io.File

object RuntimeAssets {
    data class Paths(
        val aapt2: File,
        val keystore: File
    )

    fun ensure(context: Context): Paths {
        val dir = File(context.filesDir, "runtime").apply { mkdirs() }
        val abi = preferredAbi()
        val aapt2Name = if (abi.contains("x86_64")) "aapt2-x86_64" else "aapt2-arm64-v8a"
        val aapt2 = File(dir, "aapt2")
        copyAsset(context, "runtime/$aapt2Name", aapt2, executable = true)
        val keystore = File(dir, "debug.keystore")
        copyAsset(context, "runtime/debug.keystore", keystore, executable = false)
        if (!aapt2.canExecute()) {
            aapt2.setExecutable(true, true)
        }
        return Paths(aapt2 = aapt2, keystore = keystore)
    }

    private fun preferredAbi(): String {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        if (abis.any { it.contains("x86_64") } && !abis.any { it.contains("arm64") }) {
            return "x86_64"
        }
        return "arm64-v8a"
    }

    private fun copyAsset(context: Context, assetName: String, dest: File, executable: Boolean) {
        val am = context.assets
        am.open(assetName).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (executable) dest.setExecutable(true, true)
    }
}
