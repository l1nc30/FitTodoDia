package com.dlynce.fittododia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.dlynce.fittododia.notifications.ReminderScheduler
import com.dlynce.fittododia.ui.nav.AppNav
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle


class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.scheduleDaily(this, hour = 18, minute = 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureNotificationPermissionAndSchedule()

        setContent {
            val repo = remember {
                val ds = com.dlynce.fittododia.settings.SettingsDataStore(applicationContext)
                com.dlynce.fittododia.settings.SettingsRepository(ds)
            }

            val settingsViewModel: com.dlynce.fittododia.settings.SettingsViewModel =
                viewModel(factory = com.dlynce.fittododia.settings.SettingsViewModelFactory(repo))

            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

            val darkTheme = when (themeMode) {
                com.dlynce.fittododia.settings.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                com.dlynce.fittododia.settings.ThemeMode.LIGHT -> false
                com.dlynce.fittododia.settings.ThemeMode.DARK -> true
            }

            com.dlynce.fittododia.ui.theme.FitTodoDiaTheme(
                darkTheme = darkTheme,
                dynamicColor = false
            ) {
                Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    AppNav(
                        settingsViewModel = settingsViewModel
                    )
                }
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

        // Android <= 12 ou permissão já concedida
        ReminderScheduler.scheduleDaily(this, hour = 18, minute = 0)
    }
}