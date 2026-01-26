package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.repo.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PerfilUiState(
    val reminderEnabled: Boolean = false
)

class PerfilViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repo = SettingsRepository(db.settingsDao())

    val uiState: StateFlow<PerfilUiState> =
        repo.observeSettings()
            .map { PerfilUiState(reminderEnabled = it.reminderEnabled) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PerfilUiState()
            )

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.setReminderEnabled(enabled)
        }
    }
}
