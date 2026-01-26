package com.dlynce.fittododia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AgendaScreen(
    onDayClick: (dayId: Int) -> Unit = {}
) {
    val vm: AgendaViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Agenda Semanal", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (state.days.isEmpty()) {
            Text("Carregando...", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.days) { day ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDayClick(day.id) }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(day.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            val subtitle = day.workoutName ?: "Sem treino"
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)

                        }
                    }
                }
            }
        }
    }
}
