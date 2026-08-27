package com.fireantzhang.aabinstallhelp.install

import android.content.Context
import com.fireantzhang.aabinstallhelp.data.AabFile
import com.fireantzhang.aabinstallhelp.data.InstallKind
import java.io.File

object AabInstallProbe {
    fun kind(aabVersionCode: Long?, installedVersionCode: Long?): InstallKind {
        if (installedVersionCode == null) return InstallKind.Install
        if (aabVersionCode == null) return InstallKind.Reinstall
        return when {
            aabVersionCode > installedVersionCode -> InstallKind.Update
            aabVersionCode < installedVersionCode -> InstallKind.Downgrade
            else -> InstallKind.Reinstall
        }
    }

    fun enrich(context: Context, file: AabFile): AabFile {
        return try {
            val info = BundletoolConverter.parseAab(File(file.path))
            applyInstalled(context, file.copy(
                packageName = info.pkg,
                versionName = info.versionName,
                versionCode = info.versionCode
            ))
        } catch (_: Throwable) {
            file
        }
    }

    fun applyInstalled(context: Context, file: AabFile): AabFile {
        val pkg = file.packageName ?: return file
        val existing = SplitApkInstaller.existingPackageInfo(context, pkg)
        val installedCode = SplitApkInstaller.versionCodeOf(existing)
        return file.copy(
            installedVersionName = existing?.versionName,
            installedVersionCode = installedCode?.toString(),
            installKind = kind(file.versionCode?.toLongOrNull(), installedCode)
        )
    }
}
