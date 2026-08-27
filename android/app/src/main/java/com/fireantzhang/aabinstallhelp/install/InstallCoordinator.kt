package com.fireantzhang.aabinstallhelp.install

import com.fireantzhang.aabinstallhelp.data.AabInfo
import com.fireantzhang.aabinstallhelp.data.ConflictState
import com.fireantzhang.aabinstallhelp.data.InstallStep
import com.fireantzhang.aabinstallhelp.data.LogLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object InstallCoordinator {
    data class State(
        val busy: Boolean = false,
        val step: InstallStep = InstallStep.Idle,
        val logs: List<LogLine> = emptyList(),
        val parsed: AabInfo? = null,
        val conflict: ConflictState = ConflictState.None,
        val resultMessage: String? = null,
        val success: Boolean? = null,
        val activePath: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    var cancelled: Boolean = false
        private set

    fun setBusy(value: Boolean) {
        _state.update { it.copy(busy = value) }
    }

    fun step(step: InstallStep) {
        _state.update { it.copy(step = step, success = if (step == InstallStep.Failed) false else it.success) }
    }

    fun log(text: String) {
        _state.update { it.copy(logs = it.logs + LogLine(text)) }
    }

    fun setParsed(info: AabInfo) {
        _state.update { it.copy(parsed = info) }
    }

    fun askConflict(pkg: String, installedVersion: String?, aabVersion: String, aabPath: String) {
        _state.update {
            it.copy(
                busy = false,
                conflict = ConflictState.Pending(pkg, installedVersion, aabVersion, "签名与 debug 测试签名不一致"),
                activePath = aabPath,
                step = InstallStep.Install
            )
        }
    }

    fun clearConflict() {
        _state.update { it.copy(conflict = ConflictState.None) }
    }

    fun finished(success: Boolean, message: String) {
        _state.update {
            it.copy(
                busy = false,
                step = if (success) InstallStep.Done else InstallStep.Failed,
                resultMessage = message,
                success = success,
                conflict = ConflictState.None
            )
        }
    }

    fun requestCancel() {
        cancelled = true
        log("正在取消…")
    }

    fun resetForNewJob(path: String) {
        cancelled = false
        _state.value = State(busy = true, step = InstallStep.Parse, activePath = path, logs = emptyList())
    }

    fun clearResult() {
        _state.update { it.copy(resultMessage = null, success = null, step = InstallStep.Idle, logs = emptyList()) }
    }
}
