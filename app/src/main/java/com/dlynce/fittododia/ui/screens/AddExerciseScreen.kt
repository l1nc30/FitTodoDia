package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.db.entities.WorkoutEntity
import com.dlynce.fittododia.data.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddExerciseUiState(
    val loading: Boolean = true,
    val exerciseName: String = "",
    val muscleGroup: String = "",
    val error: String? = null,
    val saving: Boolean = false
)

class AddExerciseViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    private val _uiState = MutableStateFlow(AddExerciseUiState())
    val uiState: StateFlow<AddExerciseUiState> = _uiState

    fun loadExercise(exerciseId: Long) {
        viewModelScope.launch {
            _uiState.value = AddExerciseUiState(loading = true)

            val e = db.exerciseDao().getById(exerciseId)

            if (e == null) {
                _uiState.value = AddExerciseUiState(
                    loading = false,
                    error = "Exercício não encontrado."
                )
            } else {
                _uiState.value = AddExerciseUiState(
                    loading = false,
                    exerciseName = e.name,
                    muscleGroup = e.muscleGroup,
                    error = null
                )
            }
        }
    }

    fun addToDayWorkout(
        dayId: Int,
        exerciseId: Long,
        sets: Int,
        reps: String,
        restSeconds: Int?,
        workoutNameIfCreate: String = "Treino do dia",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(saving = true, error = null)

                // garantir que existe workout para o dia
                val existing = db.workoutDao().getWorkoutByDay(dayId)
                val workoutId = if (existing == null) {
                    db.workoutDao().upsert(
                        WorkoutEntity(
                            id = 0,
                            weekDayId = dayId,
                            name = workoutNameIfCreate,
                            createdAtEpochDay = LocalDate.now().toEpochDay()
                        )
                    )
                } else existing.id

                val max = db.workoutExerciseDao().getMaxOrderIndex(workoutId)

                db.workoutExerciseDao().insert(
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        exerciseId = exerciseId,
                        orderIndex = max + 1,
                        sets = sets,
                        reps = reps.trim(),
                        restSeconds = restSeconds
                    )
                )

                _uiState.value = _uiState.value.copy(saving = false)
                onSuccess()
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(saving = false)
                onError("Falha ao adicionar exercício. Detalhe: ${t.message ?: "erro desconhecido"}")
            }
        }
    }
}

@Composable
fun AddExerciseScreen(
    dayId: Int,
    exerciseId: Long,
    onDone: () -> Unit
) {
    val vm: AddExerciseViewModel = viewModel()
    val ui by vm.uiState.collectAsState()

    LaunchedEffect(exerciseId) {
        vm.loadExercise(exerciseId)
    }

    var setsText by remember { mutableStateOf("3") }
    var repsText by remember { mutableStateOf("10") }
    var restText by remember { mutableStateOf("60") } // vazio = null

    var localError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configurar exercício", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        when {
            ui.loading -> {
                Text("Carregando exercício...", style = MaterialTheme.typography.bodyMedium)
                return
            }
            ui.error != null -> {
                Text(ui.error!!, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onDone) { Text("Voltar") }
                return
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(ui.exerciseName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(ui.muscleGroup, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = setsText,
            onValueChange = { setsText = it },
            label = { Text("Séries") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = repsText,
            onValueChange = { repsText = it },
            label = { Text("Repetições (ou tempo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = restText,
            onValueChange = { restText = it },
            label = { Text("Descanso (segundos) — opcional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        val errorToShow = localError ?: ui.error
        if (errorToShow != null) {
            Text(errorToShow, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                localError = null

                val sets = setsText.toIntOrNull()
                if (sets == null || sets < 1) {
                    localError = "Séries deve ser um número >= 1."
                    return@Button
                }

                val reps = repsText.trim()
                if (reps.isEmpty()) {
                    localError = "Repetições/tempo não pode ficar vazio."
                    return@Button
                }

                val rest = restText.trim().let { txt ->
                    if (txt.isEmpty()) null
                    else {
                        val r = txt.toIntOrNull()
                        if (r == null || r < 0) {
                            localError = "Descanso deve ser número >= 0 (ou deixe vazio)."
                            return@Button
                        }
                        r
                    }
                }

                vm.addToDayWorkout(
                    dayId = dayId,
                    exerciseId = exerciseId,
                    sets = sets,
                    reps = reps,
                    restSeconds = rest,
                    onSuccess = onDone,
                    onError = { msg -> localError = msg }
                )
            },
            enabled = !ui.saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ui.saving) "Salvando..." else "Adicionar ao treino")
        }
    }
}
