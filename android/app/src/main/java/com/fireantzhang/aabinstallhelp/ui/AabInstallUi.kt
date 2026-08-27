package com.fireantzhang.aabinstallhelp.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fireantzhang.aabinstallhelp.BuildConfig
import com.fireantzhang.aabinstallhelp.R
import com.fireantzhang.aabinstallhelp.data.AabFile
import com.fireantzhang.aabinstallhelp.data.ConflictState
import com.fireantzhang.aabinstallhelp.data.InstallStep
import com.fireantzhang.aabinstallhelp.install.InstallCoordinator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AabInstallAppScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val install by vm.install.collectAsState()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copied = copyUriToCache(context, uri)
        if (copied != null) vm.addPickedFile(copied)
    }

    AabTheme {
        if (!ui.permissionsReady) {
            PermissionPane(
                hasStorage = ui.hasStorage,
                hasInstall = ui.hasInstall,
                onStorage = { context.startActivity(vm.storageSettingsIntent()) },
                onInstall = { context.startActivity(vm.installSettingsIntent()) },
                onRefresh = { vm.refreshPermissions(); if (vm.ui.value.permissionsReady) vm.scan() },
                onOpenProject = { vm.openProjectPage() }
            )
            return@AabTheme
        }

        BoxWithConstraints {
            val tablet = maxWidth >= 600.dp
            val installPage = !tablet && ui.installPageVisible
            BackHandler(enabled = installPage) {
                if (install.busy) vm.cancelInstall() else vm.closeInstallPage()
            }
            if (installPage) {
                InstallPage(ui, install, vm)
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("AAB 安装助手", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "v${BuildConfig.VERSION_NAME} · 测试签名",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.openProjectPage() }) {
                                    Icon(AboutIcon, contentDescription = "关于")
                                }
                                IconButton(onClick = { vm.checkUpdate() }, enabled = !install.busy && !ui.checkingUpdate) {
                                    Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = "检查更新")
                                }
                                IconButton(onClick = { vm.scan() }, enabled = ui.canScan) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!tablet) {
                            BottomAppBar(tonalElevation = 3.dp) {
                                Button(
                                    onClick = { vm.startInstall() },
                                    enabled = ui.canInstall,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                ) {
                                    val selected = ui.selected
                                    Text(
                                        if (selected == null) "请先选择 AAB"
                                        else "${selected.actionLabel()} ${selected.name}"
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        Banner()
                        if (ui.update != null) {
                            UpdateBanner(
                                text = ui.updateMessage.ifBlank { "有新版本 ${ui.update?.tag}" },
                                enabled = !install.busy && !ui.downloadingUpdate,
                                onInstall = { vm.downloadAndInstallUpdate() }
                            )
                        } else if (ui.updateMessage.isNotBlank()) {
                            Text(
                                ui.updateMessage,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                        if (tablet) {
                            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                FileListPane(Modifier.weight(0.42f), ui, vm) { pick.launch("*/*") }
                                InstallLogPane(Modifier.weight(0.58f), ui, install, vm, showActions = true)
                            }
                        } else {
                            FileListPane(
                                Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                                ui,
                                vm
                            ) { pick.launch("*/*") }
                        }
                    }
                }
            }
        }

        val conflict = install.conflict
        if (conflict is ConflictState.Pending) {
            ModalBottomSheet(
                onDismissRequest = { vm.cancelConflict() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("签名不一致", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("设备上的 ${conflict.pkg} 签名与当前 debug 测试签名不一致，无法覆盖安装。")
                    Text("已装版本：${conflict.installedVersion ?: "未知"}", modifier = Modifier.padding(top = 8.dp))
                    Text("AAB 版本：${conflict.aabVersion}")
                    Text("继续将先卸载旧应用（应用数据会丢失），再重新安装。", modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.confirmUninstall() }, modifier = Modifier.fillMaxWidth()) {
                        Text("卸载并重装")
                    }
                    OutlinedButton(
                        onClick = {
                            vm.copyText(conflict.message)
                            vm.cancelConflict()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("复制错误信息") }
                    TextButton(onClick = { vm.cancelConflict() }, modifier = Modifier.fillMaxWidth()) {
                        Text("取消")
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun Banner() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = LocalContext.current.getString(R.string.banner_test_sign),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun UpdateBanner(text: String, enabled: Boolean, onInstall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f))
            Button(onClick = onInstall, enabled = enabled) { Text("下载并安装") }
        }
    }
}

@Composable
private fun PermissionPane(
    hasStorage: Boolean,
    hasInstall: Boolean,
    onStorage: () -> Unit,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onOpenProject: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AAB 安装助手", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenProject) {
                Icon(AboutIcon, contentDescription = "关于")
            }
        }
        Text("安装前需要授予存储与安装权限。未完成前不能扫描或安装。", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        PermissionCard("所有文件访问", "用于扫描 Download 等目录中的 aab", hasStorage, onStorage)
        PermissionCard("安装未知应用", "用于把拆出的 APK 装到本机", hasInstall, onInstall)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("我已授权，继续") }
    }
}

@Composable
private fun PermissionCard(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (granted) {
                StatusChip("已授权", ok = true)
            } else {
                Button(onClick = onClick) { Text("去设置") }
            }
        }
    }
}

@Composable
private fun FileListPane(modifier: Modifier, ui: UiModel, vm: AppViewModel, onPick: () -> Unit) {
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本机 AAB", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onPick) { Text("选择文件") }
        }
        Text(ui.scanHint, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
        if (ui.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (ui.files.isEmpty() && !ui.scanning) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "没有找到 aab。已扫浏览器 Download、微信 / 飞书 / 钉钉下载目录。若文件在应用私有目录被系统拦住，请点「选择文件」。",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                items(ui.files, key = { it.path }) { file ->
                    FileCard(
                        file = file,
                        selected = ui.selected?.path == file.path,
                        enabled = !vm.install.value.busy,
                        canInstall = ui.permissionsReady && !ui.scanning && !vm.install.value.busy,
                        onSelect = { vm.select(file) },
                        onInstall = { vm.startInstall(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileCard(
    file: AabFile,
    selected: Boolean,
    enabled: Boolean,
    canInstall: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    val size = Formatter.formatFileSize(context, file.sizeBytes)
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified))
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    displayPath(file.path),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2
                )
                Text("$size · $time", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                file.installStatusLine()?.let { status ->
                    Text(
                        status,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(
                onClick = onInstall,
                enabled = enabled && canInstall,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(file.actionLabel())
            }
        }
    }
}

private fun AabFile.installStatusLine(): String? {
    if (packageName.isNullOrBlank()) return null
    val aabVer = listOfNotNull(versionName, versionCode?.let { "($it)" }).joinToString(" ").ifBlank { "?" }
    val installed = if (installedVersionCode != null || !installedVersionName.isNullOrBlank()) {
        val instVer = listOfNotNull(installedVersionName, installedVersionCode?.let { "($it)" }).joinToString(" ")
        "已装 $instVer"
    } else {
        "未安装"
    }
    return "$packageName  $aabVer  ·  $installed"
}

private fun displayPath(path: String): String {
    val prefixes = listOf(
        "/storage/emulated/0/",
        "/storage/self/primary/",
        "/sdcard/",
        "/mnt/shell/emulated/0/",
        "/data/media/0/"
    )
    var shown = path
    for (prefix in prefixes) {
        if (shown.startsWith(prefix)) {
            shown = shown.removePrefix(prefix)
            break
        }
    }
    return shown
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallPage(ui: UiModel, install: InstallCoordinator.State, vm: AppViewModel) {
    val title = when {
        install.busy -> "正在安装"
        install.success == true -> "安装完成"
        install.success == false -> "安装失败"
        else -> "安装日志"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(
                            ui.selected?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (install.busy) vm.cancelInstall() else vm.closeInstallPage() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(tonalElevation = 3.dp) {
                if (install.busy) {
                    OutlinedButton(
                        onClick = { vm.cancelInstall() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    ) { Text("取消安装") }
                } else {
                    Button(
                        onClick = { vm.closeInstallPage() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    ) { Text("返回列表") }
                }
            }
        }
    ) { padding ->
        InstallLogPane(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            ui = ui,
            install = install,
            vm = vm,
            showActions = false
        )
    }
}

@Composable
private fun InstallLogPane(
    modifier: Modifier,
    ui: UiModel,
    install: InstallCoordinator.State,
    vm: AppViewModel,
    showActions: Boolean
) {
    val steps = listOf(
        InstallStep.Parse to "解析",
        InstallStep.DeviceSpec to "设备规格",
        InstallStep.BuildApks to "生成 APKS",
        InstallStep.Install to "安装",
        InstallStep.Launch to "启动"
    )
    val logState = rememberLazyListState()
    var copied by remember(install.logs.size, install.resultMessage) { mutableStateOf(false) }
    val canCopy = install.logs.isNotEmpty() || !install.resultMessage.isNullOrBlank()
    LaunchedEffect(install.logs.size) {
        if (install.logs.isNotEmpty()) {
            logState.scrollToItem(install.logs.lastIndex)
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(modifier) {
        Text(ui.selected?.name ?: "未选择文件", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            ui.selected?.path?.let { displayPath(it) } ?: "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEach { (step, label) ->
                val active = install.step == step || (install.step == InstallStep.Done && step == InstallStep.Launch)
                StatusChip(label, ok = active && install.success != false, warn = install.step == step && install.busy)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (install.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        if (install.resultMessage != null) {
            Text(install.resultMessage ?: "", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("安装日志", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { if (vm.copyLogs()) copied = true },
                enabled = canCopy
            ) {
                Text(if (copied) "已复制" else "复制日志")
            }
        }
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (install.logs.isEmpty()) {
                Text(
                    "开始安装后，步骤输出会显示在这里。",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            } else {
                LazyColumn(
                    state = logState,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                ) {
                    items(install.logs.size) { index ->
                        Text(install.logs[index].text, fontSize = 13.sp)
                    }
                }
            }
        }
        if (showActions) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startInstall() }, enabled = ui.canInstall) {
                    Text("${ui.selected?.actionLabel() ?: "安装"} AAB")
                }
                if (install.busy) {
                    OutlinedButton(onClick = { vm.cancelInstall() }) { Text("取消") }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, ok: Boolean = false, warn: Boolean = false) {
    val bg = when {
        warn -> androidx.compose.ui.graphics.Color(0xFFFEF3C7)
        ok -> androidx.compose.ui.graphics.Color(0xFFDCFCE7)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        warn -> androidx.compose.ui.graphics.Color(0xFFB45309)
        ok -> androidx.compose.ui.graphics.Color(0xFF15803D)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private fun copyUriToCache(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val name = "picked-${System.currentTimeMillis()}.aab"
        val dest = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest.absolutePath
    } catch (_: Throwable) {
        null
    }
}
