package com.dlynce.fittododia.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

    val themeModeFlow: Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
        }

    val onboardingDoneFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[ONBOARDING_DONE_KEY] ?: false
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_DONE_KEY] = true
        }
    }
}
