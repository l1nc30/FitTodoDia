@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.db.dao.WorkoutExerciseRow
import com.dlynce.fittododia.data.db.entities.DailyMissionEntity
import com.dlynce.fittododia.data.db.entities.WorkoutSessionEntity
import com.dlynce.fittododia.data.db.entities.WorkoutSessionExerciseEntity
import com.dlynce.fittododia.data.repo.WeekDayRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

// ⚠️ Não deixe esses tipos "private", pois o ViewModel expõe eles publicamente
enum class TreinoMode { Overview, Focus }

data class RestTimerState(
    val initialSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false
)

data class TreinoUiState(
    val dayId: Int = 0,
    val dayLabel: String = "",
    val hasWorkout: Boolean = false,
    val workoutId: Long = 0,
    val workoutName: String = "",
    val rows: List<WorkoutExerciseRow> = emptyList()
)

private data class SelectedDay(val id: Int, val label: String)

class TreinoViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val weekRepo = WeekDayRepository(db.weekDayDao())

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.now(zone)
    private val todayDow: DayOfWeek = today.dayOfWeek
    private val todayKey: String = baseKeyForDayOfWeek(todayDow)

    private val _mode = MutableStateFlow(TreinoMode.Overview)
    val mode: StateFlow<TreinoMode> = _mode

    private val _focusIndex = MutableStateFlow(0)
    val focusIndex: StateFlow<Int> = _focusIndex

    private val _completedSets = MutableStateFlow<Map<Long, List<Boolean>>>(emptyMap())
    val completedSets: StateFlow<Map<Long, List<Boolean>>> = _completedSets

    private val _restTimer = MutableStateFlow(RestTimerState())
    val restTimer: StateFlow<RestTimerState> = _restTimer

    private var tickJob: Job? = null
    private var sessionStartEpochMs: Long? = null

    private val selectedDayFlow: Flow<SelectedDay> =
        weekRepo.observeDaysWithWorkout()
            .map { list ->
                val match = list.firstOrNull { normalizeDayName(it.dayName) == todayKey }
                if (match != null) SelectedDay(id = match.dayId, label = match.dayName)
                else SelectedDay(id = 1, label = labelForDayOfWeek(todayDow))
            }
            .distinctUntilChanged()

    val uiState: StateFlow<TreinoUiState> =
        selectedDayFlow
            .flatMapLatest { sel ->
                db.workoutDao().observeWorkoutByDay(sel.id)
                    .flatMapLatest { workout ->
                        if (workout == null) {
                            flowOf(
                                TreinoUiState(
                                    dayId = sel.id,
                                    dayLabel = sel.label,
                                    hasWorkout = false,
                                    workoutId = 0,
                                    workoutName = "",
                                    rows = emptyList()
                                )
                            )
                        } else {
                            db.workoutExerciseDao().observeRowsByWorkout(workout.id)
                                .map { rows ->
                                    TreinoUiState(
                                        dayId = sel.id,
                                        dayLabel = sel.label,
                                        hasWorkout = true,
                                        workoutId = workout.id,
                                        workoutName = workout.name,
                                        rows = rows
                                    )
                                }
                        }
                    }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                TreinoUiState(dayId = 0, dayLabel = labelForDayOfWeek(todayDow))
            )

    fun startWorkout(rows: List<WorkoutExerciseRow>) {
        if (rows.isEmpty()) return
        _mode.value = TreinoMode.Focus
        _focusIndex.value = 0
        sessionStartEpochMs = System.currentTimeMillis()

        _completedSets.value = rows.associate { row ->
            row.id to List(row.sets.coerceAtLeast(1)) { false }
        }

        resetRestTimer()
    }

    fun backToOverview() {
        _mode.value = TreinoMode.Overview
        resetRestTimer()
    }

    fun goPrev(total: Int) {
        val cur = _focusIndex.value
        if (cur > 0) {
            _focusIndex.value = cur - 1
            resetRestTimer()
        }
    }

    fun goNext(total: Int) {
        val cur = _focusIndex.value
        if (cur < total - 1) {
            _focusIndex.value = cur + 1
            resetRestTimer()
        }
    }

    fun toggleSet(rowId: Long, setIndex: Int) {
        val current = _completedSets.value.toMutableMap()
        val list = current[rowId]?.toMutableList() ?: return
        if (setIndex !in list.indices) return
        list[setIndex] = !list[setIndex]
        current[rowId] = list
        _completedSets.value = current
    }

    fun finishWorkout(
        dayId: Int,
        workoutId: Long,
        workoutName: String,
        rows: List<WorkoutExerciseRow>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val started = sessionStartEpochMs ?: System.currentTimeMillis()
                val now = System.currentTimeMillis()
                val durationSeconds = ((now - started) / 1000L).toInt().coerceAtLeast(0)
                val dateEpochDay = LocalDate.now(zone).toEpochDay()

                val totalExercises = rows.size
                val totalSetsPlanned = rows.sumOf { it.sets.coerceAtLeast(0) }
                val totalSetsDone = rows.sumOf { r ->
                    (_completedSets.value[r.id]?.count { it } ?: 0)
                }

                val sessionId = db.workoutSessionDao().insertSession(
                    WorkoutSessionEntity(
                        workoutId = workoutId,
                        weekDayId = dayId,
                        dateEpochDay = dateEpochDay,
                        startedAtEpochMs = started,
                        durationSeconds = durationSeconds,
                        workoutNameSnapshot = workoutName,
                        totalExercises = totalExercises,
                        totalSetsPlanned = totalSetsPlanned,
                        totalSetsDone = totalSetsDone
                    )
                )

                val items = rows.mapIndexed { idx, r ->
                    val done = (_completedSets.value[r.id]?.count { it } ?: 0)
                    WorkoutSessionExerciseEntity(
                        sessionId = sessionId,
                        orderIndex = idx,
                        exerciseNameSnapshot = r.exerciseName,
                        muscleGroupSnapshot = r.muscleGroup,
                        setsPlanned = r.sets,
                        setsDone = done,
                        repsSnapshot = r.reps,
                        restSecondsSnapshot = r.restSeconds
                    )
                }
                db.workoutSessionDao().insertExercises(items)

                // ✅ Missão do dia concluída ao finalizar treino
                val mission = db.dailyMissionDao().getByDate(dateEpochDay)
                if (mission != null) {
                    if (!mission.completed) db.dailyMissionDao().upsert(mission.copy(completed = true))
                } else {
                    db.dailyMissionDao().upsert(
                        DailyMissionEntity(
                            dateEpochDay = dateEpochDay,
                            missionKey = "START",
                            completed = true
                        )
                    )
                }

                sessionStartEpochMs = null
                resetRestTimer()
                _mode.value = TreinoMode.Overview

                onSuccess()
            } catch (t: Throwable) {
                onError("Falha ao salvar histórico: ${t.message ?: "erro desconhecido"}")
            }
        }
    }

    // -------- Timer descanso --------
    fun startRest(defaultSeconds: Int) {
        val seconds = max(1, defaultSeconds)
        _restTimer.value = RestTimerState(
            initialSeconds = seconds,
            remainingSeconds = seconds,
            running = true,
            finished = false
        )
        startTicking()
    }

    fun togglePauseRest() {
        val cur = _restTimer.value
        if (cur.initialSeconds <= 0) return

        val nowRunning = !cur.running
        _restTimer.value = cur.copy(running = nowRunning, finished = false)

        if (nowRunning) startTicking() else stopTicking()
    }

    fun resetRestTimer() {
        stopTicking()
        _restTimer.value = RestTimerState()
    }

    fun zeroRestTimer() {
        stopTicking()
        val cur = _restTimer.value
        _restTimer.value =
            if (cur.initialSeconds <= 0) RestTimerState()
            else cur.copy(remainingSeconds = 0, running = false, finished = true)
    }

    private fun startTicking() {
        stopTicking()
        tickJob = viewModelScope.launch {
            while (true) {
                val cur = _restTimer.value
                if (!cur.running) break
                if (cur.remainingSeconds <= 0) break

                delay(1000)

                val after = _restTimer.value
                if (!after.running) break

                val next = after.remainingSeconds - 1
                if (next <= 0) {
                    _restTimer.value = after.copy(remainingSeconds = 0, running = false, finished = true)
                    break
                } else {
                    _restTimer.value = after.copy(remainingSeconds = next, finished = false)
                }
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }
}

@Composable
private fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
}

@Composable
fun TreinoScreen(
    onNavigateToProgress: () -> Unit = {}
) {
    val vm: TreinoViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val mode by vm.mode.collectAsState()
    val focusIndex by vm.focusIndex.collectAsState()
    val completed by vm.completedSets.collectAsState()
    val timer by vm.restTimer.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    var showFinishDialog by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Treino", style = MaterialTheme.typography.titleLarge)
                        Text(state.dayLabel, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    if (mode == TreinoMode.Focus) {
                        TextButton(onClick = { showFinishDialog = true }) { Text("Finalizar") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (errorMsg != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(errorMsg!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (!state.hasWorkout) {
                EmptyStateCard(
                    title = "Nenhum treino cadastrado para hoje",
                    subtitle = "Vá na Agenda, selecione o dia e adicione exercícios."
                )
                return@Column
            }

            when (mode) {
                TreinoMode.Overview -> OverviewViewPolished(
                    workoutName = state.workoutName,
                    rows = state.rows,
                    onStart = {
                        errorMsg = null
                        vm.startWorkout(state.rows)
                        scope.launch { snackbar.showSnackbar("Bora! Treino iniciado ✅") }
                    }
                )

                TreinoMode.Focus -> FocusViewPolished(
                    workoutName = state.workoutName,
                    rows = state.rows,
                    focusIndex = focusIndex.coerceIn(0, (state.rows.size - 1).coerceAtLeast(0)),
                    completedSets = completed,
                    timer = timer,
                    onBackToList = {
                        vm.backToOverview()
                        scope.launch { snackbar.showSnackbar("Voltando para a lista") }
                    },
                    onToggleSet = { rowId, setIdx ->
                        vm.toggleSet(rowId, setIdx)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onPrev = { vm.goPrev(state.rows.size) },
                    onNext = { vm.goNext(state.rows.size) },
                    onStartRest = { seconds ->
                        vm.startRest(seconds)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onPauseResume = { vm.togglePauseRest() },
                    onResetRest = { vm.resetRestTimer() },
                    onZeroRest = { vm.zeroRestTimer() },
                    onFinish = { showFinishDialog = true }
                )
            }
        }
    }

    if (showFinishDialog) {
        val planned = state.rows.sumOf { it.sets.coerceAtLeast(0) }
        val done = state.rows.sumOf { r -> completed[r.id]?.count { it } ?: 0 }
        val xpNow = estimateXpSafe(rows = state.rows.size, plannedSets = planned, doneSets = done)

        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finalizar treino") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Salvar no histórico e marcar a missão do dia como concluída.")
                    Text("Séries: $done / $planned", style = MaterialTheme.typography.bodySmall)
                    Text("XP estimado: +$xpNow XP", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Foco em consistência. Se hoje foi leve, tá valendo. ✅",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showFinishDialog = false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                    vm.finishWorkout(
                        dayId = state.dayId,
                        workoutId = state.workoutId,
                        workoutName = state.workoutName,
                        rows = state.rows,
                        onSuccess = {
                            errorMsg = null
                            // snack + navega pro progresso
                            val job = scope.launch {
                                snackbar.showSnackbar("Treino salvo! +$xpNow XP ✅")
                            }
                            scope.launch {
                                job.join()
                                onNavigateToProgress()
                            }
                        },
                        onError = { msg ->
                            errorMsg = msg
                            scope.launch { snackbar.showSnackbar("Erro ao salvar") }
                        }
                    )
                }) { Text("Finalizar e salvar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFinishDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// -------------------- UI (polida) --------------------

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OverviewViewPolished(
    workoutName: String,
    rows: List<WorkoutExerciseRow>,
    onStart: () -> Unit
) {
    val plannedSets = rows.sumOf { it.sets.coerceAtLeast(0) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(workoutName, style = MaterialTheme.typography.titleLarge)
            Text(
                "${rows.size} exercícios • $plannedSets séries planejadas",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = onStart,
                enabled = rows.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar treino")
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    Text("Exercícios do dia", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    if (rows.isEmpty()) {
        EmptyStateCard(
            title = "Sem exercícios",
            subtitle = "Volte na Agenda e adicione pelo menos 1 exercício."
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rows, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.exerciseName, style = MaterialTheme.typography.titleMedium)
                    if (r.muscleGroup.isNotBlank()) Text(r.muscleGroup, style = MaterialTheme.typography.bodySmall)
                    val rest = r.restSeconds?.let { " • descanso ${it}s" } ?: ""
                    Text("${r.sets}x${r.reps}$rest", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FocusViewPolished(
    workoutName: String,
    rows: List<WorkoutExerciseRow>,
    focusIndex: Int,
    completedSets: Map<Long, List<Boolean>>,
    timer: RestTimerState,
    onBackToList: () -> Unit,
    onToggleSet: (Long, Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStartRest: (Int) -> Unit,
    onPauseResume: () -> Unit,
    onResetRest: () -> Unit,
    onZeroRest: () -> Unit,
    onFinish: () -> Unit
) {
    if (rows.isEmpty()) {
        EmptyStateCard("Sem exercícios para executar", "Volte e adicione exercícios.")
        return
    }

    val totalPlanned = rows.sumOf { it.sets.coerceAtLeast(0) }
    val totalDone = rows.sumOf { r -> completedSets[r.id]?.count { it } ?: 0 }
    val overallProgress = if (totalPlanned <= 0) 0f else totalDone.toFloat() / totalPlanned.toFloat()

    // Top summary (game-like)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(workoutName, style = MaterialTheme.typography.titleMedium)
            Text("Progresso do treino", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = overallProgress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth()
            )
            Text("$totalDone / $totalPlanned séries", style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(Modifier.height(12.dp))

    val gifLoader = rememberGifImageLoader()
    val context = LocalContext.current

    AnimatedContent(
        targetState = focusIndex,
        transitionSpec = {
            (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false))
        },
        label = "exerciseSwap"
    ) { idx ->
        val current = rows[idx]
        val doneList = completedSets[current.id] ?: List(current.sets.coerceAtLeast(1)) { false }
        val doneCount = doneList.count { it }
        val suggestedRest = current.restSeconds ?: 60
        val perExProgress = if (doneList.isEmpty()) 0f else doneCount.toFloat() / doneList.size.toFloat()

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Exercício ${idx + 1}/${rows.size}", style = MaterialTheme.typography.bodySmall)
                    Text("+${estimateXpSafe(rows.size, totalPlanned, totalDone)} XP (estim.)", style = MaterialTheme.typography.bodySmall)
                }

                Text(current.exerciseName, style = MaterialTheme.typography.headlineSmall)
                if (current.muscleGroup.isNotBlank()) Text(current.muscleGroup, style = MaterialTheme.typography.bodySmall)

                if (current.pngAssetPath.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/${current.pngAssetPath}")
                            .crossfade(true)
                            .build(),
                        imageLoader = gifLoader,
                        contentDescription = "Demonstração do exercício",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }

                val restTxt = current.restSeconds?.let { " • descanso ${it}s" } ?: ""
                Text("${current.sets}x${current.reps}$restTxt", style = MaterialTheme.typography.bodyMedium)

                // Per-exercise progress
                LinearProgressIndicator(
                    progress = perExProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("$doneCount / ${doneList.size} séries concluídas", style = MaterialTheme.typography.bodySmall)

                // Sets checklist
                doneList.forEachIndexed { i, checked ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Série ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                        Checkbox(checked = checked, onCheckedChange = { onToggleSet(current.id, i) })
                    }
                }

                // Rest timer card
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Descanso", style = MaterialTheme.typography.titleMedium)

                        if (timer.initialSeconds <= 0) {
                            Text("Sugestão: ${formatMmSs(suggestedRest)}", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onStartRest(suggestedRest) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Iniciar descanso")
                            }
                        } else {
                            Text(formatMmSs(timer.remainingSeconds), style = MaterialTheme.typography.headlineSmall)
                            if (timer.finished) Text("Descanso concluído ✅", style = MaterialTheme.typography.bodySmall)

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onPauseResume, modifier = Modifier.weight(1f)) {
                                    Text(if (timer.running) "Pausar" else "Continuar")
                                }
                                OutlinedButton(onClick = onResetRest, modifier = Modifier.weight(1f)) { Text("Resetar") }
                                OutlinedButton(onClick = onZeroRest, modifier = Modifier.weight(1f)) { Text("Zerar") }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBackToList, modifier = Modifier.weight(1f)) { Text("Lista") }
                    OutlinedButton(onClick = onPrev, enabled = idx > 0, modifier = Modifier.weight(1f)) { Text("Anterior") }
                    Button(onClick = onNext, enabled = idx < rows.lastIndex, modifier = Modifier.weight(1f)) { Text("Próximo") }
                }

                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Finalizar treino")
                }
            }
        }
    }
}

// -------------------- Helpers --------------------

/**
 * XP “seguro”: não incentiva exceder o planejado (cap no doneSets <= plannedSets)
 * (mesmo espírito do ProgressoScreen).
 */
private fun estimateXpSafe(rows: Int, plannedSets: Int, doneSets: Int): Int {
    val base = 90
    val perExercise = 10 * rows
    val planned = plannedSets.coerceAtLeast(0)
    val doneCapped = doneSets.coerceAtLeast(0).coerceAtMost(planned)
    val perSetDone = 2 * doneCapped
    val completionBonus = if (planned > 0 && doneCapped == planned) 25 else 0
    return base + perExercise + perSetDone + completionBonus
}

private fun formatMmSs(totalSeconds: Int): String {
    val s = max(0, totalSeconds)
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}

private fun baseKeyForDayOfWeek(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "segunda"
    DayOfWeek.TUESDAY -> "terca"
    DayOfWeek.WEDNESDAY -> "quarta"
    DayOfWeek.THURSDAY -> "quinta"
    DayOfWeek.FRIDAY -> "sexta"
    DayOfWeek.SATURDAY -> "sabado"
    DayOfWeek.SUNDAY -> "domingo"
}

private fun labelForDayOfWeek(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "Segunda"
    DayOfWeek.TUESDAY -> "Terça"
    DayOfWeek.WEDNESDAY -> "Quarta"
    DayOfWeek.THURSDAY -> "Quinta"
    DayOfWeek.FRIDAY -> "Sexta"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

private fun normalizeDayName(name: String): String {
    val lower = name.trim().lowercase()
    val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    val noFeira = noAccents.replace("-feira", "").replace(" feira", "")
    return noFeira.replace("\\s+".toRegex(), " ").trim()
}
