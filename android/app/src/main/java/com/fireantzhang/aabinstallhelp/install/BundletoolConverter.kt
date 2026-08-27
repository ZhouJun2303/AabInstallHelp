package com.fireantzhang.aabinstallhelp.install

import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.model.AppBundle
import com.android.tools.build.bundletool.model.SigningConfiguration
import com.fireantzhang.aabinstallhelp.data.AabInfo
import com.fireantzhang.aabinstallhelp.data.RuntimeAssets
import java.io.File
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
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
        val signing = loadDebugSigning(runtime)
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

    private fun loadDebugSigning(runtime: RuntimeAssets.Paths): SigningConfiguration {
        val cert = runtime.certDer.inputStream().use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }
        val key = KeyFactory.getInstance(cert.publicKey.algorithm)
            .generatePrivate(PKCS8EncodedKeySpec(runtime.keyPk8.readBytes()))
        return SigningConfiguration.builder()
            .setSignerConfig(key, cert)
            .build()
    }
}
