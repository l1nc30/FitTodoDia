package com.dlynce.fittododia.settings

import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val ds: SettingsDataStore) {
    val themeMode: Flow<ThemeMode> = ds.themeModeFlow
    val onboardingDone: Flow<Boolean> = ds.onboardingDoneFlow

    suspend fun setThemeMode(mode: ThemeMode) = ds.setThemeMode(mode)
    suspend fun setOnboardingDone() = ds.setOnboardingDone()
}
