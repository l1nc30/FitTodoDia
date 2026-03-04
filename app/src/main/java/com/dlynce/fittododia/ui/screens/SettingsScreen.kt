package com.dlynce.fittododia.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun SettingsScreen(
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val vm: PerfilViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isDark = themeMode == ThemeMode.DARK

    // --- Lógica de Permissões ---
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
        val permissionOk = Build.VERSION.SDK_INT < 33 ||
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

    // --- Layout da Tela ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // Permite scroll se a tela for pequena
            .padding(16.dp)
    ) {
        // Título da Tela
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Seção: Aparência
        Text(
            text = "Aparência",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        SettingsOptionCard(
            title = "Tema escuro",
            description = "Alterna entre modo claro e escuro.",
            checked = isDark,
            onCheckedChange = { checked ->
                settingsViewModel.updateThemeMode(
                    if (checked) ThemeMode.DARK else ThemeMode.LIGHT
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Seção: Notificações
        Text(
            text = "Notificações",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        SettingsOptionCard(
            title = "Ativar lembretes",
            description = "Receba incentivos diários para não faltar ao treino.",
            checked = state.reminderEnabled,
            onCheckedChange = { checked ->
                if (checked) {
                    val permissionOk = Build.VERSION.SDK_INT < 33 ||
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

        // Aviso de permissão negada
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission.value) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = "A permissão de notificações está desativada. Ative nas configurações do Android para receber lembretes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * Componente reutilizável para opções de configuração com Switch
 */
@Composable
fun SettingsOptionCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Estilo flat moderno
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}