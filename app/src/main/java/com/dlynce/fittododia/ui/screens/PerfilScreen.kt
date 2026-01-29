package com.dlynce.fittododia.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.notifications.ReminderScheduler
import com.dlynce.fittododia.settings.SettingsViewModel
import com.dlynce.fittododia.settings.ThemeMode

@Composable
fun PerfilScreen(
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val vm: PerfilViewModel = viewModel()
    val state by vm.uiState.collectAsState() // ✅ mantém seu fluxo original

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isDark = themeMode == ThemeMode.DARK

    val hasNotifPermission = remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission.value = granted

        if (granted) {
            vm.setReminderEnabled(true)
            ReminderScheduler.scheduleDaily(context, hour = 18, minute = 0)
        } else {
            vm.setReminderEnabled(false)
            ReminderScheduler.cancel(context)
        }
    }

    // Aplica agendamento/cancelamento conforme o estado persistido
    LaunchedEffect(state.reminderEnabled) {
        val permissionOk =
            Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        hasNotifPermission.value = permissionOk

        if (state.reminderEnabled && permissionOk) {
            ReminderScheduler.scheduleDaily(context, hour = 18, minute = 0)
        } else if (!state.reminderEnabled) {
            ReminderScheduler.cancel(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // ✅ Card do tema (toggle simples)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Aparência", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tema escuro", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Alterna entre claro e escuro.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = isDark,
                        onCheckedChange = { checked ->
                            settingsViewModel.updateThemeMode(
                                if (checked) ThemeMode.DARK else ThemeMode.LIGHT
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Card de Notificações (seu original)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Notificações", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ativar lembretes", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Mensagens motivacionais diárias para te ajudar a manter o hábito.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = state.reminderEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val permissionOk =
                                    Build.VERSION.SDK_INT < 33 ||
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED

                                if (permissionOk) {
                                    vm.setReminderEnabled(true)
                                    ReminderScheduler.scheduleDaily(context, hour = 18, minute = 0)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                vm.setReminderEnabled(false)
                                ReminderScheduler.cancel(context)
                            }
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission.value) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Permissão de notificações está desativada. Ative para receber lembretes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
