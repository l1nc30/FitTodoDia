@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.db.dao.WorkoutExerciseRow
import kotlinx.coroutines.flow.*

data class AgendaV2DayItem(
    val dayId: Int,
    val dayLabel: String,
    val hasWorkout: Boolean,
    val workoutName: String,
    val exercisesCount: Int
)

data class AgendaV2UiState(
    val items: List<AgendaV2DayItem> = emptyList()
)

class AgendaV2ViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    private val days = listOf(
        1 to "Segunda",
        2 to "Terça",
        3 to "Quarta",
        4 to "Quinta",
        5 to "Sexta",
        6 to "Sábado",
        7 to "Domingo"
    )

    private fun observeDayItem(dayId: Int, label: String) =
        db.workoutDao().observeWorkoutByDay(dayId)
            .flatMapLatest { workout ->
                if (workout == null) {
                    flowOf(
                        AgendaV2DayItem(
                            dayId,
                            label,
                            false,
                            "Sem treino",
                            0
                        )
                    )
                } else {
                    db.workoutExerciseDao()
                        .observeRowsByWorkout(workout.id)
                        .map { rows: List<WorkoutExerciseRow> ->
                            AgendaV2DayItem(
                                dayId,
                                label,
                                true,
                                workout.name,
                                rows.size
                            )
                        }
                }
            }

    val uiState: StateFlow<AgendaV2UiState> =
        combine(days.map { (id, label) -> observeDayItem(id, label) }) { arr ->
            AgendaV2UiState(arr.toList().sortedBy { it.dayId })
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AgendaV2UiState()
        )
}

@Composable
fun AgendaScreen(
    onDayClick: (Int) -> Unit
) {

    val vm: AgendaV2ViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Icon(
                imageVector = Icons.Filled.EditCalendar,
                contentDescription = null
            )

            Spacer(Modifier.width(8.dp))

            Text(
                "Agenda",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Text(
            "Monte treinos por dia",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Toque em um dia para editar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.70f)
        )

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            items(
                items = state.items,
                key = { it.dayId }
            ) { item ->

                DayCardModernV2(
                    item = item,
                    onClick = { onDayClick(item.dayId) }
                )
            }

            item {
                Spacer(Modifier.height(90.dp))
            }
        }
    }
}

@Composable
private fun DayCardModernV2(
    item: AgendaV2DayItem,
    onClick: () -> Unit
) {

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(
        alpha = if (isLight) 0.16f else 0.24f
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 10.dp,
        border = BorderStroke(1.dp, border)
    ) {

        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = if (item.hasWorkout)
                            Icons.Filled.TaskAlt
                        else
                            Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {

                    Text(
                        item.dayLabel,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        item.workoutName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                if (item.hasWorkout) {

                    AssistChip(
                        onClick = onClick,
                        label = { Text("${item.exercisesCount} exercícios") }
                    )

                    AssistChip(
                        onClick = onClick,
                        label = { Text("Editar") }
                    )
                } else {

                    AssistChip(
                        onClick = onClick,
                        label = { Text("Criar treino") }
                    )

                    Text(
                        "Sem treino ainda",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}