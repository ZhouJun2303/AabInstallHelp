package com.fireantzhang.aabinstallhelp.install

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fireantzhang.aabinstallhelp.AabInstallApp
import com.fireantzhang.aabinstallhelp.MainActivity
import com.fireantzhang.aabinstallhelp.R
import com.fireantzhang.aabinstallhelp.data.DeviceSpecFactory
import com.fireantzhang.aabinstallhelp.data.InstallKind
import com.fireantzhang.aabinstallhelp.data.InstallStep
import com.fireantzhang.aabinstallhelp.data.RuntimeAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class InstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                InstallCoordinator.requestCancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_CONFIRM_UNINSTALL -> {
                val pkg = intent.getStringExtra(EXTRA_PKG) ?: return START_NOT_STICKY
                val aab = intent.getStringExtra(EXTRA_AAB) ?: return START_NOT_STICKY
                startForegroundInternal("正在卸载旧应用")
                job = scope.launch { uninstallThenInstall(pkg, aab) }
            }
            else -> {
                val path = intent?.getStringExtra(EXTRA_AAB) ?: return START_NOT_STICKY
                startForegroundInternal("正在安装 AAB")
                job = scope.launch { runPipeline(File(path), retriedUninstall = false) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runPipeline(aab: File, retriedUninstall: Boolean) {
        val work = File(cacheDir, "aab-install").apply { mkdirs() }
        try {
            InstallCoordinator.setBusy(true)
            log("开始处理：${aab.name}")
            if (!aab.exists()) throw IllegalStateException("aab 文件不存在：${aab.absolutePath}")

            InstallCoordinator.step(InstallStep.Parse)
            log("1、正在解析 aab")
            val info = withContext(Dispatchers.IO) { BundletoolConverter.parseAab(aab) }
            InstallCoordinator.setParsed(info)
            log("应用包名：${info.pkg}，版本：${info.versionLabel()}")
            val existing = SplitApkInstaller.existingPackageInfo(this, info.pkg)
            val installedCode = SplitApkInstaller.versionCodeOf(existing)
            val aabCode = info.versionCode?.toLongOrNull()
            val installKind = AabInstallProbe.kind(aabCode, installedCode)
            when (installKind) {
                InstallKind.Install -> log("本机未安装 ${info.pkg}，将进行安装")
                InstallKind.Update -> log(
                    "本机已安装 ${info.pkg} ${SplitApkInstaller.versionLabel(existing)}，aab ${info.versionLabel()} 版本更高，将进行更新"
                )
                InstallKind.Reinstall -> log(
                    "本机已安装 ${info.pkg} ${SplitApkInstaller.versionLabel(existing)}，与 aab ${info.versionLabel()} 版本相同，将再次安装"
                )
                InstallKind.Downgrade -> log(
                    "本机已安装 ${info.pkg} ${SplitApkInstaller.versionLabel(existing)}，aab ${info.versionLabel()} 版本更低，将进行降级"
                )
            }

            val signed = SplitApkInstaller.isDebugSigned(this, info.pkg)
            if (signed == false && !retriedUninstall) {
                InstallCoordinator.askConflict(
                    pkg = info.pkg,
                    installedVersion = SplitApkInstaller.versionLabel(existing),
                    aabVersion = info.versionLabel(),
                    aabPath = aab.absolutePath
                )
                log("设备上的 ${info.pkg} 签名与 debug 测试签名不一致，等待确认")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            InstallCoordinator.step(InstallStep.DeviceSpec)
            log("2、生成本机设备描述文件")
            val specFile = File(work, "device-spec.json")
            val spec = withContext(Dispatchers.IO) {
                DeviceSpecFactory.write(this@InstallService, specFile, aab)
            }
            if (spec.aabAbis.isEmpty()) {
                log("AAB 未包含 native 库，设备描述使用主 ABI：${spec.chosenAbi}")
            } else {
                log(
                    "AAB 包含 ABI：${spec.aabAbis.joinToString()}，本机偏好：${spec.deviceAbis.joinToString()}，本次选用：${spec.chosenAbi}"
                )
            }
            log("设备描述：${spec.file.readText().take(400)}")

            InstallCoordinator.step(InstallStep.BuildApks)
            log("4、正在使用 debug 测试签名生成 apks")
            val runtime = withContext(Dispatchers.IO) { RuntimeAssets.ensure(this@InstallService) }
            val apks = File(work, "app_bundle.apks")
            withContext(Dispatchers.IO) {
                BundletoolConverter.buildApks(aab, spec.file, apks, runtime) { line ->
                    InstallCoordinator.log(line)
                }
            }

            InstallCoordinator.step(InstallStep.Install)
            log("5、正在安装到本机")
            val apkDir = File(work, "splits")
            val splits = withContext(Dispatchers.IO) { SplitApkInstaller.extractApks(apks, apkDir) }
            SplitApkInstaller.createSession(
                this,
                splits,
                allowDowngrade = installKind == InstallKind.Downgrade
            )
            val event = withTimeout(5 * 60_000L) { InstallBus.events.first { !it.uninstall } }
            if (!event.success) {
                if (!retriedUninstall && SplitApkInstaller.isSignatureMismatch(event.message)) {
                    InstallCoordinator.askConflict(
                        pkg = info.pkg,
                        installedVersion = SplitApkInstaller.versionLabel(
                            SplitApkInstaller.existingPackageInfo(this, info.pkg)
                        ),
                        aabVersion = info.versionLabel(),
                        aabPath = aab.absolutePath
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }
                throw IllegalStateException(event.message ?: "安装失败")
            }
            log("已成功将 aab 安装到设备")

            InstallCoordinator.step(InstallStep.Launch)
            val launch = packageManager.getLaunchIntentForPackage(info.pkg)
            if (launch == null) {
                log("无法自动识别打开应用，请手动打开")
            } else {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                log("应用已自动启动")
            }
            InstallCoordinator.finished(success = true, message = "安装完成 ${info.pkg} ${info.versionLabel()}")
        } catch (t: Throwable) {
            if (InstallCoordinator.cancelled) {
                InstallCoordinator.finished(success = false, message = "已取消")
            } else {
                val message = if (MemoryGuard.isOom(t)) MemoryGuard.OOM_MESSAGE else (t.message ?: "安装失败")
                log("失败：$message")
                log(t.stackTraceToString())
                InstallCoordinator.finished(success = false, message = message)
            }
        } finally {
            try {
                work.deleteRecursively()
            } catch (_: Throwable) {
            }
            InstallCoordinator.setBusy(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun uninstallThenInstall(pkg: String, aabPath: String) {
        try {
            InstallCoordinator.setBusy(true)
            log("正在卸载旧应用：$pkg")
            SplitApkInstaller.uninstall(this, pkg)
            val event = withTimeout(3 * 60_000L) { InstallBus.events.first { it.uninstall || it.success } }
            if (!event.success && event.uninstall) {
                throw IllegalStateException(event.message ?: "卸载失败")
            }
            log("旧应用已卸载，开始重新安装")
            runPipeline(File(aabPath), retriedUninstall = true)
        } catch (t: Throwable) {
            log("卸载失败：${t.message}")
            log(t.stackTraceToString())
            InstallCoordinator.finished(success = false, message = t.message ?: "卸载失败")
            InstallCoordinator.setBusy(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun log(text: String) {
        InstallCoordinator.log("[aab 安装]$text")
        startForegroundInternal(text.take(40))
    }

    private fun startForegroundInternal(text: String) {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, AabInstallApp.CHANNEL_INSTALL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("AAB 安装助手")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_CANCEL = "cancel"
        const val ACTION_CONFIRM_UNINSTALL = "confirm_uninstall"
        const val EXTRA_AAB = "aab"
        const val EXTRA_PKG = "pkg"
        private const val NOTIF_ID = 41
    }
}
