package com.dlynce.fittododia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.dlynce.fittododia.notifications.ReminderScheduler
import com.dlynce.fittododia.ui.nav.AppNav

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
            com.dlynce.fittododia.ui.theme.FitTodoDiaTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    AppNav()
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