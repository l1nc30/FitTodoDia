package com.dlynce.fittododia.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.notifications.ReminderScheduler

@Composable
fun PerfilScreen() {
    val context = LocalContext.current
    val vm: PerfilViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    val hasNotifPermission = remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
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
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED

        hasNotifPermission.value = permissionOk

        if (state.reminderEnabled && permissionOk) {
            ReminderScheduler.scheduleDaily(context, hour = 18, minute = 0)
        } else if (!state.reminderEnabled) {
            ReminderScheduler.cancel(context)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

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
