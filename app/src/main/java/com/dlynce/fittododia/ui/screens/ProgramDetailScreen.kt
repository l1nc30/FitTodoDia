@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.db.entities.ExerciseEntity
import com.dlynce.fittododia.data.db.entities.WeekDayEntity
import com.dlynce.fittododia.data.db.entities.WorkoutEntity
import com.dlynce.fittododia.data.db.entities.WorkoutExerciseEntity
import com.dlynce.fittododia.ui.templates.ProgramTemplatesRepo
import com.dlynce.fittododia.ui.templates.WorkoutExerciseTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class ProgramDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    fun observeWeekDays(): Flow<List<WeekDayEntity>> =
        db.weekDayDao().observeWeekDays()

    fun observeExercises(): Flow<List<ExerciseEntity>> =
        db.exerciseDao().observeAll()

    suspend fun applyProgram(
        programId: String,
        selectedDayIds: List<Int>,
        replaceExisting: Boolean
    ) {
        val program = ProgramTemplatesRepo.byId(programId)
        val workoutDao = db.workoutDao()
        val weDao = db.workoutExerciseDao()

        db.withTransaction {
            selectedDayIds.sorted().forEachIndexed { index, dayId ->
                val templateWorkout = program.workouts[index % program.workouts.size]
                val existingWorkout = workoutDao.getWorkoutByDay(dayId)

                val workoutId: Long = when {
                    existingWorkout == null -> {
                        workoutDao.insert(
                            WorkoutEntity(
                                weekDayId = dayId,
                                name = templateWorkout.name,
                                createdAtEpochDay = LocalDate.now().toEpochDay()
                            )
                        )
                    }

                    replaceExisting -> {
                        workoutDao.deleteByDay(dayId)
                        workoutDao.insert(
                            WorkoutEntity(
                                weekDayId = dayId,
                                name = templateWorkout.name,
                                createdAtEpochDay = LocalDate.now().toEpochDay()
                            )
                        )
                    }

                    else -> existingWorkout.id
                }

                val startOrderIndex = if (existingWorkout != null && !replaceExisting) {
                    weDao.getMaxOrderIndex(workoutId) + 1
                } else 0

                templateWorkout.exercises.forEachIndexed { i, ex ->
                    weDao.insert(
                        WorkoutExerciseEntity(
                            workoutId = workoutId,
                            exerciseId = ex.exerciseId,
                            orderIndex = startOrderIndex + i,
                            sets = ex.sets,
                            reps = ex.reps,
                            restSeconds = ex.restSeconds
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onAppliedGoAgenda: (() -> Unit)? = null
) {
    val vm: ProgramDetailViewModel = viewModel()
    val program = remember(programId) { ProgramTemplatesRepo.byId(programId) }

    val weekDays by vm.observeWeekDays().collectAsState(initial = emptyList())
    val exercises by vm.observeExercises().collectAsState(initial = emptyList())
    val nameByExerciseId = remember(exercises) { exercises.associate { it.id to it.name } }

    val selected = remember { mutableStateListOf<Int>() }
    var replaceExisting by remember { mutableStateOf(true) }
    var showHowToUse by remember { mutableStateOf(true) }
    var expandedWorkoutIndex by remember { mutableStateOf<Int?>(0) }

    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plano") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== Header bonito =====
            Text(program.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                program.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("${program.daysPerWeek} dias") })
                AssistChip(onClick = {}, label = { Text(program.split) })
                AssistChip(onClick = {}, label = { Text(program.level) })
                AssistChip(onClick = {}, label = { Text("${program.durationWeeks} semanas") })
            }

            // ===== Como usar (explicação) =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Como usar", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { showHowToUse = !showHowToUse }) {
                            Text(if (showHowToUse) "Ocultar" else "Mostrar")
                        }
                    }

                    AnimatedVisibility(visible = showHowToUse) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                buildHowToUseText(program.split),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Dica: mantenha 1–2 repetições “na reserva” (RIR 1–2) na maioria das séries e aumente carga/reps quando completar o topo da faixa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ===== Cards dos treinos do plano =====
            Text("Treinos do plano", style = MaterialTheme.typography.titleMedium)

            program.workouts.forEachIndexed { idx, w ->
                val isExpanded = expandedWorkoutIndex == idx
                val estimatedMin = remember(w.exercises) { estimateMinutes(w.exercises) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { expandedWorkoutIndex = if (isExpanded) null else idx }
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${idx + 1}. ${w.name}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(onClick = {}, label = { Text("${w.exercises.size} exercícios") })
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("~$estimatedMin min") },
                                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) }
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.PlaylistAddCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Divider()
                                w.exercises.forEach { ex ->
                                    val exName = nameByExerciseId[ex.exerciseId]
                                    ExerciseLine(ex, exName)
                                }
                            }
                        }
                    }
                }
            }

            // ===== Aplicar na agenda =====
            Text("Aplicar na semana", style = MaterialTheme.typography.titleMedium)
            Text(
                "Selecione os dias. O app distribui os treinos em ordem (A, B, C...).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Seleção de dias
            if (weekDays.isEmpty()) {
                Text("Carregando dias...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                weekDays.forEach { d ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(d.name, style = MaterialTheme.typography.bodyLarge)
                        Checkbox(
                            checked = selected.contains(d.id),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (!selected.contains(d.id)) selected.add(d.id)
                                } else {
                                    selected.remove(d.id)
                                }
                            }
                        )
                    }
                }
            }

            // Preview da semana (A/B/C nos dias selecionados)
            AnimatedVisibility(visible = selected.isNotEmpty()) {
                WeekPreviewCard(
                    weekDays = weekDays,
                    selectedDayIds = selected.toList(),
                    workoutNames = program.workouts.map { it.name }
                )
            }

            // Switch substituir/adicionar
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Substituir treino existente", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (replaceExisting)
                                "Apaga o treino do dia e cria o do plano."
                            else
                                "Mantém o treino do dia e adiciona os exercícios no final.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = selected.isNotEmpty(),
                onClick = {
                    scope.launch {
                        try {
                            vm.applyProgram(programId, selected.toList(), replaceExisting)
                            snack.showSnackbar("Plano aplicado na agenda ✅")
                            onAppliedGoAgenda?.invoke()
                        } catch (e: Exception) {
                            snack.showSnackbar("Erro ao aplicar: ${e.message}")
                        }
                    }
                }
            ) {
                Text("Aplicar na agenda")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ExerciseLine(ex: WorkoutExerciseTemplate, exerciseName: String?) {
    val rest = ex.restSeconds?.let { "${it}s" } ?: "-"
    val name = exerciseName ?: "Exercício #${ex.exerciseId}"

    Text(
        "$name • ${ex.sets}x ${ex.reps} • descanso $rest",
        style = MaterialTheme.typography.bodyMedium
    )
}

/** Texto curto explicando o split */
private fun buildHowToUseText(split: String): String = when {
    split.contains("Upper/Lower", ignoreCase = true) ->
        "Upper/Lower alterna treinos de superiores e inferiores. É ótimo para consistência e evolução, principalmente com frequência 2x por grupo (4 dias)."

    split.equals("PPL", ignoreCase = true) || split.contains("Push", true) ->
        "Push/Pull/Legs divide por padrões de movimento: Push (peito/ombros), Pull (costas) e Legs (pernas). Ajuda a distribuir bem o volume semanal."

    split.contains("Full", true) ->
        "Full Body trabalha o corpo todo por sessão. Excelente para iniciantes e para quem quer treinar 2–3x por semana com simplicidade."

    split.contains("ABCDE", true) ->
        "ABCDE costuma separar grupos por dia. Bom para mais volume semanal e foco em detalhes, ideal para quem treina quase todos os dias."

    else ->
        "Aplique o plano na sua semana e siga a ordem dos treinos. Progrida aos poucos aumentando reps/carga quando completar o topo da faixa."
}

/** Estimativa simples de duração: (sets * ~45s) + descansos */
private fun estimateMinutes(exercises: List<WorkoutExerciseTemplate>): Int {
    val totalSets = exercises.sumOf { it.sets }
    val workSeconds = totalSets * 45
    val restSeconds = exercises.sumOf { ex ->
        val r = ex.restSeconds ?: 60
        r * ex.sets
    }
    val total = workSeconds + restSeconds
    return (total / 60.0).roundToInt().coerceAtLeast(10)
}

@Composable
private fun WeekPreviewCard(
    weekDays: List<WeekDayEntity>,
    selectedDayIds: List<Int>,
    workoutNames: List<String>
) {
    val selectedSorted = remember(selectedDayIds) { selectedDayIds.sorted() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Prévia na semana", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ordem aplicada: A, B, C... nos dias selecionados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val nameByDayId = weekDays.associate { it.id to it.name }

            selectedSorted.forEachIndexed { idx, dayId ->
                val workoutName = workoutNames[idx % workoutNames.size]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(nameByDayId[dayId] ?: "Dia $dayId", style = MaterialTheme.typography.bodyLarge)
                    Text(workoutName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}