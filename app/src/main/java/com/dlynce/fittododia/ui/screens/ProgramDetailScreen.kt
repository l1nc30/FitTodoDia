@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
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
import com.dlynce.fittododia.ui.templates.goalStyle
import com.dlynce.fittododia.ui.templates.GoalStyle
import com.dlynce.fittododia.ui.templates.WorkoutExerciseTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

// ── ViewModel (sem mudanças de lógica) ──────────────────────────────────────

class ProgramDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    fun observeWeekDays(): Flow<List<WeekDayEntity>> = db.weekDayDao().observeWeekDays()
    fun observeExercises(): Flow<List<ExerciseEntity>> = db.exerciseDao().observeAll()

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
                    existingWorkout == null -> workoutDao.insert(
                        WorkoutEntity(
                            weekDayId = dayId,
                            name = templateWorkout.name,
                            createdAtEpochDay = LocalDate.now().toEpochDay()
                        )
                    )
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

                val startOrderIndex = if (existingWorkout != null && !replaceExisting)
                    weDao.getMaxOrderIndex(workoutId) + 1
                else 0

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

// ── Screen ───────────────────────────────────────────────────────────────────

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

    val style = remember(program.goal) { goalStyle(program.goal) }

    val selected = remember { mutableStateListOf<Int>() }
    var replaceExisting by remember { mutableStateOf(true) }
    var expandedWorkoutIndex by remember { mutableStateOf<Int?>(0) }

    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accent = if (isLight) style.accentLight else style.accentDark

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program.title, maxLines = 1) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── ETAPA 1: Visão geral do plano ────────────────────────────────
            StepHeader(number = "1", title = "Visão geral do plano", accent = accent)

            PlanOverviewCard(program = program, style = style, accent = accent)

            // ── ETAPA 2: Treinos incluídos ────────────────────────────────────
            StepHeader(number = "2", title = "Treinos incluídos", accent = accent)

            program.workouts.forEachIndexed { idx, w ->
                val isExpanded = expandedWorkoutIndex == idx
                val estimatedMin = remember(w.exercises) { estimateMinutes(w.exercises) }

                WorkoutExpandableCard(
                    index = idx,
                    workout = w,
                    isExpanded = isExpanded,
                    estimatedMin = estimatedMin,
                    nameByExerciseId = nameByExerciseId,
                    accent = accent,
                    onToggle = { expandedWorkoutIndex = if (isExpanded) null else idx }
                )
            }

            // ── ETAPA 3: Aplicar na semana ────────────────────────────────────
            StepHeader(number = "3", title = "Aplicar na semana", accent = accent)

            Text(
                "Selecione ${program.daysPerWeek} dia${if (program.daysPerWeek > 1) "s" else ""}. " +
                        "O app distribui os treinos em ordem (${program.workouts.take(3).mapIndexed { i, _ -> ('A' + i).toString() }.joinToString(", ")}...).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Seleção de dias — chips grandes ao invés de checkboxes
            DayPickerGrid(
                weekDays = weekDays,
                selectedDayIds = selected,
                recommendedCount = program.daysPerWeek,
                accent = accent,
                onToggle = { dayId ->
                    if (selected.contains(dayId)) selected.remove(dayId)
                    else selected.add(dayId)
                }
            )

            // Preview da semana — logo abaixo dos dias, em destaque
            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                WeekPreviewCard(
                    weekDays = weekDays,
                    selectedDayIds = selected.toList(),
                    workoutNames = program.workouts.map { it.name },
                    accent = accent
                )
            }

            // Opção substituir/adicionar — mais clara
            ReplaceToggleCard(
                replaceExisting = replaceExisting,
                onToggle = { replaceExisting = it },
                accent = accent
            )

            // Contador de dias selecionados vs recomendado
            val countColor = when {
                selected.size == program.daysPerWeek -> MaterialTheme.colorScheme.secondary
                selected.size > program.daysPerWeek  -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            }
            Text(
                "${selected.size} de ${program.daysPerWeek} dias selecionados",
                style = MaterialTheme.typography.labelMedium,
                color = countColor,
                fontWeight = if (selected.size == program.daysPerWeek) FontWeight.SemiBold else FontWeight.Normal
            )

            // ── Botão final ───────────────────────────────────────────────────
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
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
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Aplicar na agenda",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Componentes internos ─────────────────────────────────────────────────────

@Composable
private fun StepHeader(number: String, title: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = accent.copy(alpha = 0.15f)
        ) {
            Text(
                number,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlanOverviewCard(
    program: com.dlynce.fittododia.ui.templates.ProgramTemplate,
    style: GoalStyle,
    accent: Color
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 3.dp else 10.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = if (isLight) 0.25f else 0.40f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Objetivo + ícone
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(style.emoji, style = MaterialTheme.typography.headlineMedium)
                Column {
                    Text(
                        style.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        program.level,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }

            Text(
                program.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            // Métricas em linha
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlanStat(value = "${program.daysPerWeek}x", label = "por semana", accent = accent)
                PlanStat(value = program.split, label = "divisão", accent = accent)
                PlanStat(value = "${program.durationWeeks}sem", label = "duração", accent = accent)
            }

            // Como usar — compacto, sempre visível
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.07f)
            ) {
                Text(
                    buildHowToUseText(program.split),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                )
            }
        }
    }
}

@Composable
private fun PlanStat(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
        )
    }
}

@Composable
private fun WorkoutExpandableCard(
    index: Int,
    workout: com.dlynce.fittododia.ui.templates.WorkoutTemplate,
    isExpanded: Boolean,
    estimatedMin: Int,
    nameByExerciseId: Map<Long, String>,
    accent: Color,
    onToggle: () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = if (isExpanded) BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f))

    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 8.dp,
        border = border
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Número do treino
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            ('A' + index).toString(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Column {
                        Text(
                            workout.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${workout.exercises.size} exercícios",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                            Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Text(
                                "~$estimatedMin min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    workout.exercises.forEachIndexed { i, ex ->
                        ExerciseRow(
                            index = i + 1,
                            ex = ex,
                            name = nameByExerciseId[ex.exerciseId] ?: "Exercício #${ex.exerciseId}",
                            accent = accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    index: Int,
    ex: WorkoutExerciseTemplate,
    name: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Número do exercício
        Text(
            "$index.",
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.7f),
            modifier = Modifier.width(18.dp)
        )

        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${ex.sets} séries × ${ex.reps} reps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }

        // Descanso como badge
        ex.restSeconds?.let { rest ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "${rest}s",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayPickerGrid(
    weekDays: List<WeekDayEntity>,
    selectedDayIds: List<Int>,
    recommendedCount: Int,
    accent: Color,
    onToggle: (Int) -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    if (weekDays.isEmpty()) {
        Text(
            "Carregando dias...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        return
    }

    // Grid 2 colunas de chips de dia
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        weekDays.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { day ->
                    val isSelected = selectedDayIds.contains(day.id)
                    val containerColor = if (isSelected) accent.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                    val borderColor = if (isSelected) accent
                    else MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.25f else 0.35f)

                    Surface(
                        onClick = { onToggle(day.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = containerColor,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                        tonalElevation = 0.dp,
                        shadowElevation = if (isSelected) 0.dp else 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                day.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accent
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                // Preencher linha ímpar
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeekPreviewCard(
    weekDays: List<WeekDayEntity>,
    selectedDayIds: List<Int>,
    workoutNames: List<String>,
    accent: Color
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val selectedSorted = remember(selectedDayIds) { selectedDayIds.sorted() }
    val nameByDayId = weekDays.associate { it.id to it.name }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isLight) 0.25f else 0.35f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Text(
                "Prévia da semana",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )

            selectedSorted.forEachIndexed { idx, dayId ->
                val workoutName = workoutNames[idx % workoutNames.size]
                val letter = ('A' + idx).toString()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                letter,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                        Text(
                            nameByDayId[dayId] ?: "Dia $dayId",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        workoutName,
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplaceToggleCard(
    replaceExisting: Boolean,
    onToggle: (Boolean) -> Unit,
    accent: Color
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (replaceExisting) "Substituir treino existente" else "Adicionar ao treino existente",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (replaceExisting)
                        "O treino atual do dia será apagado e substituído."
                    else
                        "Os exercícios serão adicionados no final do treino existente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = replaceExisting,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.4f))
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun buildHowToUseText(split: String): String = when {
    split.contains("Upper/Lower", ignoreCase = true) ->
        "Alterna treinos de superiores e inferiores. Ótimo para consistência com frequência 2x por grupo."
    split.equals("PPL", ignoreCase = true) || split.contains("Push", true) ->
        "Push (peito/ombros), Pull (costas) e Legs (pernas). Distribui bem o volume semanal."
    split.contains("Full", true) ->
        "Trabalha o corpo todo por sessão. Excelente para 2–3x por semana com simplicidade."
    split.contains("ABCDE", true) ->
        "Cada dia foca em um grupo muscular. Ideal para quem treina quase todos os dias."
    else ->
        "Siga a ordem dos treinos e progrida aumentando reps ou carga quando completar o topo da faixa."
}

private fun estimateMinutes(exercises: List<WorkoutExerciseTemplate>): Int {
    val totalSets = exercises.sumOf { it.sets }
    val workSeconds = totalSets * 45
    val restSeconds = exercises.sumOf { ex -> (ex.restSeconds ?: 60) * ex.sets }
    return ((workSeconds + restSeconds) / 60.0).roundToInt().coerceAtLeast(10)
}