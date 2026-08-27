package com.fireantzhang.aabinstallhelp.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.fireantzhang.aabinstallhelp.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object SplitApkInstaller {
    const val ACTION_INSTALL_RESULT = "com.fireantzhang.aabinstallhelp.INSTALL_RESULT"
    const val EXTRA_SESSION = "session_id"

    fun extractApks(apksFile: File, destDir: File): List<File> {
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        val out = ArrayList<File>()
        ZipFile(apksFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = File(entry.name).name
                if (!name.lowercase().endsWith(".apk")) continue
                val dest = File(destDir, name)
                zip.getInputStream(entry).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                out.add(dest)
            }
        }
        if (out.isEmpty()) {
            throw IllegalStateException("apks 中没有可安装的 apk")
        }
        return out
    }

    fun existingPackageInfo(context: Context, pkg: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    pkg,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else if (Build.VERSION.SDK_INT >= 28) {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isDebugSigned(context: Context, pkg: String): Boolean? {
        val info = existingPackageInfo(context, pkg) ?: return null
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return false
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        if (signatures.isNullOrEmpty()) return false
        val expected = BuildConfig.DEBUG_CERT_SHA256.uppercase()
        return signatures.any { sig ->
            sha256(sig.toByteArray()).equals(expected, ignoreCase = true)
        }
    }

    fun versionLabel(info: PackageInfo?): String? {
        if (info == null) return null
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else {
            @Suppress("DEPRECATION")
            info.versionCode.toString()
        }
        return "${info.versionName ?: "?"}.$code"
    }

    fun createSession(context: Context, apks: List<File>): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 34) {
            params.setDontKillApp(true)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apks.forEachIndexed { index, apk ->
                session.openWrite("split_$index.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_RESULT
                putExtra(EXTRA_SESSION, sessionId)
            }
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
        return sessionId
    }

    fun isSignatureMismatch(statusMessage: String?): Boolean {
        val text = statusMessage ?: return false
        return text.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
            text.contains("signatures do not match", ignoreCase = true) ||
            text.contains("UPDATE_INCOMPATIBLE", ignoreCase = true)
    }

    fun uninstall(context: Context, pkg: String) {
        val installer = context.packageManager.packageInstaller
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val intent = Intent(context, InstallResultReceiver::class.java).apply {
            action = ACTION_INSTALL_RESULT
            putExtra("uninstall_pkg", pkg)
        }
        val pending = PendingIntent.getBroadcast(context, pkg.hashCode(), intent, flags)
        installer.uninstall(pkg, pending.intentSender)
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02X".format(b) }
    }
}
