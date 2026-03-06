package com.dlynce.fittododia.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        repo.themeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    val onboardingDone: StateFlow<Boolean> =
        repo.onboardingDone.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true // true evita flash de onboarding antes de carregar
        )

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { repo.setOnboardingDone() }
    }
}
