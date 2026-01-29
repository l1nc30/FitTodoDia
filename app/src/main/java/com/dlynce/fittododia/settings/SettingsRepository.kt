package com.dlynce.fittododia.settings

import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val ds: SettingsDataStore) {
    val themeMode: Flow<ThemeMode> = ds.themeModeFlow
    suspend fun setThemeMode(mode: ThemeMode) = ds.setThemeMode(mode)
}
