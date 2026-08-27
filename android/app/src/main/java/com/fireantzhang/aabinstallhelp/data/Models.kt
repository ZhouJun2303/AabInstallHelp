package com.fireantzhang.aabinstallhelp.data

enum class InstallStep {
    Idle,
    Parse,
    DeviceSpec,
    BuildApks,
    Install,
    Launch,
    Done,
    Failed
}

enum class InstallKind {
    Install,
    Update,
    Downgrade
}

data class AabFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: String? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: String? = null,
    val installKind: InstallKind = InstallKind.Install
) {
    fun actionLabel(): String = when (installKind) {
        InstallKind.Install -> "安装"
        InstallKind.Update -> "更新"
        InstallKind.Downgrade -> "降级"
    }
}

data class AabInfo(
    val pkg: String,
    val versionName: String?,
    val versionCode: String?
) {
    fun versionLabel(): String {
        val name = versionName ?: "?"
        val code = versionCode ?: "?"
        return "$name.$code"
    }
}

data class LogLine(val text: String, val timeMs: Long = System.currentTimeMillis())

sealed class ConflictState {
    data object None : ConflictState()
    data class Pending(
        val pkg: String,
        val installedVersion: String?,
        val aabVersion: String,
        val message: String
    ) : ConflictState()
}

data class UpdateInfo(
    val tag: String,
    val version: String,
    val downloadUrl: String,
    val sha256: String?,
    val notes: String?
)
