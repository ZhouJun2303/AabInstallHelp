package com.fireantzhang.aabinstallhelp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AabInstallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INSTALL,
                    "AAB 安装",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val CHANNEL_INSTALL = "aab_install"
    }
}
