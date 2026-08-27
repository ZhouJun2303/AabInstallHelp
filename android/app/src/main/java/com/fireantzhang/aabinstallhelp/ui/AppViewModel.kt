package com.fireantzhang.aabinstallhelp.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fireantzhang.aabinstallhelp.BuildConfig
import com.fireantzhang.aabinstallhelp.data.AabFile
import com.fireantzhang.aabinstallhelp.data.AabScanner
import com.fireantzhang.aabinstallhelp.data.ConflictState
import com.fireantzhang.aabinstallhelp.data.Permissions
import com.fireantzhang.aabinstallhelp.data.UpdateInfo
import com.fireantzhang.aabinstallhelp.install.AabInstallProbe
import com.fireantzhang.aabinstallhelp.install.InstallCoordinator
import com.fireantzhang.aabinstallhelp.install.InstallService
import com.fireantzhang.aabinstallhelp.install.SplitApkInstaller
import com.fireantzhang.aabinstallhelp.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiModel(
    val hasStorage: Boolean = false,
    val hasInstall: Boolean = false,
    val files: List<AabFile> = emptyList(),
    val selected: AabFile? = null,
    val scanning: Boolean = false,
    val scanHint: String = "",
    val update: UpdateInfo? = null,
    val updateMessage: String = "",
    val checkingUpdate: Boolean = false,
    val downloadingUpdate: Boolean = false,
    val updateReceived: Long = 0L,
    val updateTotal: Long = 0L,
    val updateProgress: Float? = null,
    val installPageVisible: Boolean = false
) {
    val permissionsReady: Boolean get() = hasStorage && hasInstall
    val canScan: Boolean get() = permissionsReady && !scanning && !InstallCoordinator.state.value.busy
    val canInstall: Boolean
        get() = permissionsReady && selected != null && !scanning && !InstallCoordinator.state.value.busy
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(UiModel())
    val ui: StateFlow<UiModel> = _ui.asStateFlow()
    val install = InstallCoordinator.state
    private var scanJob: Job? = null

    init {
        refreshPermissions()
        if (Permissions.ready(app)) {
            scan()
            silentCheckUpdate()
        }
        viewModelScope.launch {
            var lastSuccess: Boolean? = null
            install.collect { state ->
                if (state.success == true && lastSuccess != true) {
                    refreshInstalledKinds()
                }
                lastSuccess = state.success
            }
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _ui.update {
            it.copy(
                hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Permissions.hasAllFilesAccess()
                } else {
                    Permissions.hasLegacyRead(ctx)
                },
                hasInstall = Permissions.canInstallPackages(ctx)
            )
        }
    }

    fun storageSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${getApplication<Application>().packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${getApplication<Application>().packageName}")
            }
        }
    }

    fun installSettingsIntent(): Intent {
        val pkg = getApplication<Application>().packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$pkg")
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun scan() {
        if (!Permissions.ready(getApplication())) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _ui.update { it.copy(scanning = true, scanHint = "正在扫描…") }
            val result = withContext(Dispatchers.IO) {
                AabScanner.scan(getApplication()) { scanJob?.isCancelled == true }
            }
            _ui.update { state ->
                val selected = state.selected?.let { cur -> result.find { it.path == cur.path } }
                    ?: result.firstOrNull()
                state.copy(
                    scanning = false,
                    files = result,
                    selected = selected,
                    scanHint = if (result.isEmpty()) {
                        "未找到 aab。已扫浏览器 Download、微信/飞书/钉钉下载目录；Android/data 若系统拦截请点「选择文件」。"
                    } else {
                        "找到 ${result.size} 个 aab，正在识别已装版本…"
                    }
                )
            }
            if (result.isNotEmpty()) {
                val selectedPath = _ui.value.selected?.path
                val ordered = if (selectedPath == null) {
                    result
                } else {
                    result.sortedBy { file -> if (file.path == selectedPath) 0 else 1 }
                }
                enrichFiles(ordered)
                if (isActive) {
                    _ui.update { state ->
                        state.copy(
                            scanHint = if (state.files.isEmpty()) state.scanHint else "找到 ${state.files.size} 个 aab"
                        )
                    }
                }
            }
        }
    }

    fun select(file: AabFile) {
        if (install.value.busy) return
        _ui.update { it.copy(selected = file) }
    }

    fun addPickedFile(path: String) {
        val file = File(path)
        if (!file.exists() || !file.name.lowercase().endsWith(".aab")) return
        val item = AabFile(
            path = file.absolutePath,
            name = file.name,
            sizeBytes = file.length(),
            lastModified = file.lastModified()
        )
        _ui.update { state ->
            val files = AabScanner.dedupe(listOf(item) + state.files)
            state.copy(files = files, selected = item, scanHint = "已选择 ${item.name}")
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val enriched = withContext(Dispatchers.IO) { AabInstallProbe.enrich(ctx, item) }
            replaceFile(enriched)
        }
    }

    fun startInstall(file: AabFile? = _ui.value.selected) {
        val target = file ?: _ui.value.selected ?: return
        if (install.value.busy || !_ui.value.permissionsReady) return
        _ui.update { it.copy(selected = target, installPageVisible = true) }
        InstallCoordinator.resetForNewJob(target.path)
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, InstallService::class.java).apply {
            action = InstallService.ACTION_START
            putExtra(InstallService.EXTRA_AAB, target.path)
        }
        ctx.startForegroundService(intent)
    }

    fun confirmUninstall() {
        val conflict = install.value.conflict as? ConflictState.Pending ?: return
        val path = install.value.activePath ?: return
        InstallCoordinator.clearConflict()
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, InstallService::class.java).apply {
            action = InstallService.ACTION_CONFIRM_UNINSTALL
            putExtra(InstallService.EXTRA_PKG, conflict.pkg)
            putExtra(InstallService.EXTRA_AAB, path)
        }
        ctx.startForegroundService(intent)
    }

    fun cancelConflict() {
        InstallCoordinator.clearConflict()
        InstallCoordinator.finished(false, "已取消安装")
    }

    fun cancelInstall() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, InstallService::class.java).apply { action = InstallService.ACTION_CANCEL })
        InstallCoordinator.requestCancel()
    }

    fun closeInstallPage() {
        if (install.value.busy) return
        InstallCoordinator.clearResult()
        _ui.update { it.copy(installPageVisible = false) }
    }

    fun checkUpdate(silent: Boolean = false) {
        if (install.value.busy || _ui.value.downloadingUpdate) return
        viewModelScope.launch {
            _ui.update { it.copy(checkingUpdate = true, updateMessage = if (silent) it.updateMessage else "正在检查…") }
            val result = withContext(Dispatchers.IO) {
                runCatching { UpdateChecker.check() }
            }
            val info = result.getOrNull()
            _ui.update {
                it.copy(
                    checkingUpdate = false,
                    update = if (result.isFailure && silent) it.update else info,
                    updateMessage = when {
                        result.isFailure && !silent -> result.exceptionOrNull()?.message ?: "检查更新失败"
                        info != null -> "有新版本 ${info.tag}"
                        silent -> it.updateMessage
                        else -> "已是最新 ${BuildConfig.VERSION_NAME}"
                    }
                )
            }
        }
    }

    private fun silentCheckUpdate() = checkUpdate(silent = true)

    fun downloadAndInstallUpdate() {
        val info = _ui.value.update ?: return
        if (install.value.busy || _ui.value.downloadingUpdate) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    downloadingUpdate = true,
                    updateReceived = 0L,
                    updateTotal = 0L,
                    updateProgress = null,
                    updateMessage = "正在下载 ${info.tag}"
                )
            }
            try {
                val dest = File(getApplication<Application>().cacheDir, "update.apk")
                withContext(Dispatchers.IO) {
                    var lastEmit = 0L
                    var lastPct = -2
                    UpdateChecker.download(info.downloadUrl, dest) { copied, total ->
                        val now = System.currentTimeMillis()
                        val pct = if (total > 0) ((copied * 100) / total).toInt() else -1
                        if (copied != total && pct == lastPct && now - lastEmit < 150) return@download
                        lastEmit = now
                        lastPct = pct
                        val sizeText = if (total > 0) {
                            "${UpdateChecker.formatBytes(copied)} / ${UpdateChecker.formatBytes(total)}"
                        } else {
                            UpdateChecker.formatBytes(copied)
                        }
                        _ui.update { state ->
                            state.copy(
                                downloadingUpdate = true,
                                updateReceived = copied,
                                updateTotal = total,
                                updateProgress = if (total > 0) copied.toFloat() / total.toFloat() else null,
                                updateMessage = "正在下载 ${info.tag}  $sizeText"
                            )
                        }
                    }
                    _ui.update {
                        it.copy(
                            updateProgress = if (it.updateTotal > 0) 1f else null,
                            updateMessage = "正在校验 SHA256…"
                        )
                    }
                    if (!info.sha256.isNullOrBlank()) {
                        val actual = UpdateChecker.sha256(dest)
                        if (!actual.equals(info.sha256, ignoreCase = true)) {
                            throw IllegalStateException("SHA256 校验失败，已保留旧版本")
                        }
                    } else {
                        throw IllegalStateException("发布包缺少 SHA256SUMS.txt，拒绝安装")
                    }
                }
                SplitApkInstaller.createSession(getApplication(), listOf(dest))
                _ui.update {
                    it.copy(
                        downloadingUpdate = false,
                        updateProgress = 1f,
                        updateMessage = "已提交安装，请在系统对话框确认"
                    )
                }
            } catch (t: Throwable) {
                _ui.update {
                    it.copy(
                        downloadingUpdate = false,
                        updateProgress = null,
                        updateMessage = t.message ?: "更新失败"
                    )
                }
            }
        }
    }

    fun copyText(text: String) {
        val ctx = getApplication<Application>()
        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("aab", text))
    }

    fun copyLogs(): Boolean {
        val state = install.value
        val lines = ArrayList<String>()
        val result = state.resultMessage
        if (!result.isNullOrBlank()) {
            lines.add(result)
        }
        state.logs.forEach { lines.add(it.text) }
        val text = lines.joinToString("\n")
        if (text.isBlank()) return false
        copyText(text)
        return true
    }

    private suspend fun enrichFiles(files: List<AabFile>) {
        val ctx = getApplication<Application>()
        val working = files.toMutableList()
        for (i in working.indices) {
            if (scanJob?.isCancelled == true) return
            val enriched = withContext(Dispatchers.IO) { AabInstallProbe.enrich(ctx, working[i]) }
            working[i] = enriched
            replaceFile(enriched)
        }
    }

    private fun refreshInstalledKinds() {
        val ctx = getApplication<Application>()
        _ui.update { state ->
            val files = state.files.map { AabInstallProbe.applyInstalled(ctx, it) }
            val selected = state.selected?.let { cur -> files.find { it.path == cur.path } } ?: state.selected
            state.copy(files = files, selected = selected)
        }
    }

    private fun replaceFile(file: AabFile) {
        _ui.update { state ->
            val files = state.files.map { if (it.path == file.path) file else it }
            val selected = if (state.selected?.path == file.path) file else state.selected
            state.copy(files = files, selected = selected)
        }
    }

    fun openProjectPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PROJECT_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
    }
}
