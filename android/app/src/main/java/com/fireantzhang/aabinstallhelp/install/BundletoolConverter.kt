package com.fireantzhang.aabinstallhelp.install

import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.model.AppBundle
import com.android.tools.build.bundletool.model.Password
import com.android.tools.build.bundletool.model.SigningConfiguration
import com.fireantzhang.aabinstallhelp.data.AabInfo
import com.fireantzhang.aabinstallhelp.data.RuntimeAssets
import java.io.File
import java.util.Optional
import java.util.zip.ZipFile

object BundletoolConverter {
    fun parseAab(aab: File): AabInfo {
        ZipFile(aab).use { zip ->
            val bundle = AppBundle.buildFromZip(zip)
            val manifest = bundle.baseModule.androidManifest
            val versionName = if (manifest.versionName.isPresent) manifest.versionName.get() else null
            val versionCode = if (manifest.versionCode.isPresent) manifest.versionCode.get().toString() else null
            return AabInfo(pkg = bundle.packageName, versionName = versionName, versionCode = versionCode)
        }
    }

    fun buildApks(
        aab: File,
        specFile: File,
        outputApks: File,
        runtime: RuntimeAssets.Paths,
        log: (String) -> Unit
    ) {
        if (outputApks.exists()) {
            outputApks.delete()
        }
        outputApks.parentFile?.mkdirs()
        log("使用 debug 测试签名生成 apks")
        val ksPass: Optional<Password> = Optional.of(Password.createFromStringValue("pass:android"))
        val signing = SigningConfiguration.extractFromKeystore(
            runtime.keystore.toPath(),
            "androiddebugkey",
            ksPass,
            ksPass
        )
        val command = BuildApksCommand.builder()
            .setBundlePath(aab.toPath())
            .setOutputFile(outputApks.toPath())
            .setOverwriteOutput(true)
            .setDeviceSpec(specFile.toPath())
            .setAapt2Command(Aapt2Command.createFromExecutablePath(runtime.aapt2.toPath()))
            .setSigningConfiguration(signing)
            .build()
        command.execute()
        if (!outputApks.exists() || outputApks.length() == 0L) {
            throw IllegalStateException("未生成 apks 文件")
        }
        log("生成 apks 成功：${outputApks.absolutePath}")
    }
}
