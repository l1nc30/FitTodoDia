package com.dlynce.fittododia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.notifications.ReminderScheduler
import com.dlynce.fittododia.settings.SettingsDataStore
import com.dlynce.fittododia.settings.SettingsRepository
import com.dlynce.fittododia.settings.SettingsViewModel
import com.dlynce.fittododia.settings.SettingsViewModelFactory
import com.dlynce.fittododia.settings.ThemeMode
import com.dlynce.fittododia.ui.nav.AppNav
import com.dlynce.fittododia.ui.theme.FitTodoDiaTheme

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.scheduleDaily(this, hour = 18, minute = 0)
        }
    }

    // SettingsDataStore e SettingsRepository criados uma vez no nível da Activity
    private val settingsDataStore by lazy { SettingsDataStore(applicationContext) }
    private val settingsRepo by lazy { SettingsRepository(settingsDataStore) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermissionAndSchedule()

        setContent {
            val settingsViewModel: SettingsViewModel =
                viewModel(factory = SettingsViewModelFactory(settingsRepo))

            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            FitTodoDiaTheme(darkTheme = darkTheme, dynamicColor = false) {
                AppNav(settingsViewModel = settingsViewModel)
            }
        }
    }

    private fun ensureNotificationPermissionAndSchedule() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        ReminderScheduler.scheduleDaily(this, hour = 18, minute = 0)
    }
}
