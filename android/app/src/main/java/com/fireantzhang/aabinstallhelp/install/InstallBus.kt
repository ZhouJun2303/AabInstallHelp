package com.fireantzhang.aabinstallhelp.install

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object InstallBus {
    data class Event(
        val success: Boolean,
        val status: Int,
        val message: String?,
        val uninstall: Boolean
    )

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun publish(event: Event) {
        _events.tryEmit(event)
    }
}
