package com.dlynce.fittododia.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RestTimerState(
    val isRunning: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0
)

sealed interface RestTimerEvent {
    data object Beep : RestTimerEvent
}

class RestTimerViewModel : ViewModel() {

    private val _state = MutableStateFlow(RestTimerState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<RestTimerEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var job: Job? = null

    fun start(seconds: Int) {
        if (seconds <= 0) return

        job?.cancel()
        _state.value = RestTimerState(
            isRunning = true,
            totalSeconds = seconds,
            remainingSeconds = seconds
        )

        job = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _state.value = _state.value.copy(remainingSeconds = remaining)
            }
            _state.value = _state.value.copy(isRunning = false)
            _events.tryEmit(RestTimerEvent.Beep)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = RestTimerState()
    }

    fun addSeconds(delta: Int) {
        val s = _state.value
        if (!s.isRunning) return
        val newRemaining = (s.remainingSeconds + delta).coerceAtLeast(0)
        _state.value = s.copy(remainingSeconds = newRemaining, totalSeconds = maxOf(s.totalSeconds, newRemaining))
    }
}