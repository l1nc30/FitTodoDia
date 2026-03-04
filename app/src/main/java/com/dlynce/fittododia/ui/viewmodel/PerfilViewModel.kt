package com.dlynce.fittododia.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class PerfilUiState(
    val level: Int = 1,
    val xpTotal: Int = 0,
    val levelProgress: Float = 0f,
    val streakDays: Int = 0,
    val darkTheme: Boolean = true,
    val reminderEnabled: Boolean = false
)

class PerfilViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PerfilUiState())
    val uiState: StateFlow<PerfilUiState> = _uiState

    fun setDarkTheme(enabled: Boolean) {
        _uiState.update { it.copy(darkTheme = enabled) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        _uiState.update { it.copy(reminderEnabled = enabled) }
    }

    // 🔥 Você pode depois conectar isso com ProgressoViewModel
    fun updateProgress(
        level: Int,
        xp: Int,
        progress: Float,
        streak: Int
    ) {
        _uiState.update {
            it.copy(
                level = level,
                xpTotal = xp,
                levelProgress = progress,
                streakDays = streak
            )
        }
    }
}