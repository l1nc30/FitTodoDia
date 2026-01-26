package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.db.dao.WorkoutExerciseRow
import com.dlynce.fittododia.data.db.entities.WorkoutEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import org.burnoutcrew.reorderable.*

class EditWorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    fun observeWorkout(dayId: Int) =
        db.workoutDao().observeWorkoutByDay(dayId)

    fun observeWorkoutRows(workoutId: Long): Flow<List<WorkoutExerciseRow>> =
        db.workoutExerciseDao().observeRowsByWorkout(workoutId)

    fun saveWorkout(dayId: Int, name: String) {
        viewModelScope.launch {
            val existing = db.workoutDao().getWorkoutByDay(dayId)
            val entity = WorkoutEntity(
                id = existing?.id ?: 0,
                weekDayId = dayId,
                name = name.trim(),
                createdAtEpochDay = existing?.createdAtEpochDay ?: LocalDate.now().toEpochDay()
            )
            db.workoutDao().upsert(entity)
        }
    }

    fun deleteWorkout(dayId: Int) {
        viewModelScope.launch {
            val existing = db.workoutDao().getWorkoutByDay(dayId)
            if (existing != null) {
                db.workoutExerciseDao().deleteByWorkout(existing.id)
            }
            db.workoutDao().deleteByDay(dayId)
        }
    }

    fun removeExerciseRow(workoutId: Long, rowId: Long) {
        viewModelScope.launch {
            db.workoutExerciseDao().deleteAndReorder(workoutId, rowId)
        }
    }

    /**
     * Persiste a ordem atual gravando orderIndex = 0..N-1 no banco.
     * Compatível com o DAO que você tem (updateOrderIndex).
     */
    fun persistOrder(orderedRowIds: List<Long>) {
        viewModelScope.launch {
            orderedRowIds.forEachIndexed { idx, id ->
                db.workoutExerciseDao().updateOrderIndex(id, idx)
            }
        }
    }
}

@Composable
fun EditWorkoutScreen(
    dayId: Int,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    // ✅ opcional: permite voltar ao “passo 1” sem tela de edição por item
    onEditRow: (rowId: Long) -> Unit = {}
) {
    val vm: EditWorkoutViewModel = viewModel()
    val scope = rememberCoroutineScope()

    val workoutFlow = remember(dayId) { vm.observeWorkout(dayId) }
    val workout by workoutFlow.collectAsState(initial = null)

    var name by remember(workout?.name) { mutableStateOf(workout?.name ?: "") }

    val rowsFlow = remember(workout?.id) { workout?.id?.let { vm.observeWorkoutRows(it) } }
    val rows by (rowsFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })

    val localRows = remember { mutableStateListOf<WorkoutExerciseRow>() }
    var syncEnabled by remember { mutableStateOf(true) }
    var persistJob by remember { mutableStateOf<Job?>(null) }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (from.index == to.index) return@rememberReorderableLazyListState
            syncEnabled = false
            localRows.add(to.index, localRows.removeAt(from.index))

            persistJob?.cancel()
            persistJob = scope.launch {
                delay(350)
                vm.persistOrder(localRows.map { it.id })
                syncEnabled = true
            }
        }
    )

    LaunchedEffect(rows) {
        if (syncEnabled) {
            localRows.clear()
            localRows.addAll(rows)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Editar treino do dia", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome do treino") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        vm.saveWorkout(dayId, name)
                        onBack()
                    }
                }
            ) { Text("Salvar") }

            OutlinedButton(
                onClick = {
                    vm.deleteWorkout(dayId)
                    onBack()
                }
            ) { Text("Remover treino") }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenLibrary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Adicionar exercícios")
        }

        Spacer(Modifier.height(16.dp))

        Text("Exercícios do treino", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        when {
            workout == null -> {
                Text(
                    "Salve um nome de treino para começar a adicionar exercícios.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            localRows.isEmpty() -> {
                Text("Nenhum exercício adicionado ainda.", style = MaterialTheme.typography.bodySmall)
            }

            else -> {
                LazyColumn(
                    state = reorderState.listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .reorderable(reorderState)
                ) {
                    items(localRows.size, key = { idx -> localRows[idx].id }) { index ->
                        val r = localRows[index]

                        ReorderableItem(reorderState, key = r.id) { isDragging ->
                            val elev by animateDpAsState(
                                if (isDragging) 10.dp else 0.dp,
                                label = "dragElev"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = elev)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .detectReorderAfterLongPress(reorderState) // arrasta na linha inteira
                                        // ✅ no “passo 1”, esse clique pode ficar neutro (onEditRow default vazio)
                                        .clickable { onEditRow(r.id) }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) { Text("≡") }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(r.exerciseName, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(4.dp))
                                        val rest = r.restSeconds?.let { " • Descanso ${it}s" } ?: ""
                                        Text("${r.sets}x${r.reps}$rest", style = MaterialTheme.typography.bodySmall)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val wId = workout!!.id
                                            vm.removeExerciseRow(wId, r.id)
                                        }
                                    ) { Text("Remover") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
