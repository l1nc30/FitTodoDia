@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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
import com.dlynce.fittododia.ui.theme.Surface2
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
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

    // ✅ Beep curto quando o descanso termina (finished vira true)
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
    DisposableEffect(Unit) {
        onDispose { toneGen.release() }
    }
    var lastFinished by remember { mutableStateOf(false) }
    LaunchedEffect(timer.finished) {
        if (timer.finished && !lastFinished) {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        }
        lastFinished = timer.finished
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (errorMsg != null) {
                SurfaceCard { Text(errorMsg!!, style = MaterialTheme.typography.bodySmall) }
            }

            if (!state.hasWorkout) {
                EmptyStateCard(
                    title = "Nenhum treino cadastrado para hoje",
                    subtitle = "Vá na Agenda, selecione o dia e adicione exercícios."
                )
                return@Column
            }

            when (mode) {
                TreinoMode.Overview -> OverviewViewHarmonized(
                    workoutName = state.workoutName,
                    rows = state.rows,
                    onStart = {
                        errorMsg = null
                        vm.startWorkout(state.rows)
                        scope.launch { snackbar.showSnackbar("Bora! Treino iniciado ✅") }
                    }
                )

                TreinoMode.Focus -> FocusViewHarmonizedFixedActions(
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
                        // ✅ detectar se está marcando como "feito" (false -> true)
                        val before = completed[rowId]?.getOrNull(setIdx) ?: false
                        val willBeChecked = !before

                        vm.toggleSet(rowId, setIdx)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        // ✅ iniciar descanso automático quando marcar a série como feita
                        if (willBeChecked) {
                            val row = state.rows.firstOrNull { it.id == rowId }
                            val rest = row?.restSeconds ?: 60
                            vm.startRest(rest)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
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

// -------------------- UI (harmonizada com imagens) --------------------

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 10.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OverviewViewHarmonized(
    workoutName: String,
    rows: List<WorkoutExerciseRow>,
    onStart: () -> Unit
) {
    val plannedSets = rows.sumOf { it.sets.coerceAtLeast(0) }

    SurfaceCard {
        Text(workoutName, style = MaterialTheme.typography.titleLarge)
        Text(
            "${rows.size} exercícios • $plannedSets séries planejadas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Button(
            onClick = onStart,
            enabled = rows.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text("Iniciar treino")
        }
    }

    Text("Exercícios do dia", style = MaterialTheme.typography.titleMedium)

    if (rows.isEmpty()) {
        EmptyStateCard(
            title = "Sem exercícios",
            subtitle = "Volte na Agenda e adicione pelo menos 1 exercício."
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rows, key = { it.id }) { r ->
            ExerciseRowPreview(
                name = r.exerciseName,
                group = r.muscleGroup,
                detail = buildString {
                    append("${r.sets}x${r.reps}")
                    r.restSeconds?.let { append(" • descanso ${it}s") }
                },
                pngAssetPath = r.pngAssetPath
            )
        }
    }
}

@Composable
private fun ExerciseRowPreview(
    name: String,
    group: String,
    detail: String,
    pngAssetPath: String
) {
    SurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExerciseThumbSlot(
                pngAssetPath = pngAssetPath,
                modifier = Modifier.size(72.dp)
            )

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (group.isNotBlank()) {
                    Text(
                        group,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * ✅ Versão com AÇÕES FIXAS embaixo (não rolam com a tela)
 * - Conteúdo rola
 * - Botões ficam sempre acessíveis
 * - Evita sobreposição e melhora usabilidade
 */
@Composable
private fun FocusViewHarmonizedFixedActions(
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

    val gifLoader = rememberGifImageLoader()

    // altura aproximada do bloco fixo de ações (pra dar padding no conteúdo rolável)
    val actionsBlockHeight = 140.dp

    Box(Modifier.fillMaxSize()) {

        // ✅ Conteúdo rolável
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = actionsBlockHeight + 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header progresso
            SurfaceCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(workoutName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Progresso do treino",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text("${(overallProgress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                }

                LinearProgressIndicator(
                    progress = overallProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    "$totalDone / $totalPlanned séries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            AnimatedContent(
                targetState = focusIndex,
                transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false)) },
                label = "exerciseSwap"
            ) { idx ->
                val current = rows[idx]
                val doneList = completedSets[current.id] ?: List(current.sets.coerceAtLeast(1)) { false }
                val doneCount = doneList.count { it }
                val suggestedRest = current.restSeconds ?: 60
                val perExProgress =
                    if (doneList.isEmpty()) 0f else doneCount.toFloat() / doneList.size.toFloat()

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // HERO
                    SurfaceCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Exercício ${idx + 1}/${rows.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                "+${estimateXpSafe(rows.size, totalPlanned, totalDone)} XP (estim.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        Text(current.exerciseName, style = MaterialTheme.typography.headlineSmall)

                        if (current.muscleGroup.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text(current.muscleGroup) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        ExerciseHeroSlot(
                            pngAssetPath = current.pngAssetPath,
                            imageLoader = gifLoader,
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )

                        val restTxt = current.restSeconds?.let { " • descanso ${it}s" } ?: ""
                        Text("${current.sets}x${current.reps}$restTxt", style = MaterialTheme.typography.bodyMedium)

                        LinearProgressIndicator(
                            progress = perExProgress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "$doneCount / ${doneList.size} séries concluídas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // CHECKLIST
                    SurfaceCard {
                        Text("Séries", style = MaterialTheme.typography.titleMedium)
                        doneList.forEachIndexed { i, checked ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Série ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                                Checkbox(checked = checked, onCheckedChange = { onToggleSet(current.id, i) })
                            }
                        }
                    }

                    // DESCANSO
                    SurfaceCard {
                        Text("Descanso", style = MaterialTheme.typography.titleMedium)

                        if (timer.initialSeconds <= 0) {
                            Text(
                                "Sugestão: ${formatMmSs(suggestedRest)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Button(
                                onClick = { onStartRest(suggestedRest) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Iniciar descanso") }
                        } else {
                            Text(formatMmSs(timer.remainingSeconds), style = MaterialTheme.typography.headlineSmall)
                            if (timer.finished) {
                                Text(
                                    "Descanso concluído ✅",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }

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
            }
        }

        // ✅ AÇÕES FIXAS (sempre visíveis)
        FixedActionBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            canPrev = focusIndex > 0,
            canNext = focusIndex < rows.lastIndex,
            onBackToList = onBackToList,
            onPrev = onPrev,
            onNext = onNext,
            onFinish = onFinish
        )
    }
}

@Composable
private fun FixedActionBar(
    modifier: Modifier = Modifier,
    canPrev: Boolean,
    canNext: Boolean,
    onBackToList: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 2.dp else 0.dp,
        shadowElevation = if (isLight) 6.dp else 16.dp,
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onBackToList,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                ) { Text("Lista", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }

                OutlinedButton(
                    onClick = onPrev,
                    enabled = canPrev,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                ) { Text("Ant<<", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }

                Button(
                    onClick = onNext,
                    enabled = canNext,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                ) { Text("Próx>>", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
            }

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Finalizar treino") }
        }
    }
}

@Composable
private fun ExerciseHeroSlot(
    pngAssetPath: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier,
        shape = shape,
        color = Surface2, // ✅ escuro fixo
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))
    ) {
        if (pngAssetPath.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Sem imagem",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data("file:///android_asset/$pngAssetPath")
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Demonstração do exercício",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ExerciseThumbSlot(
    pngAssetPath: String,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = modifier,
        shape = shape,
        color = Surface2, // ✅ escuro fixo
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))
    ) {
        if (pngAssetPath.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data("file:///android_asset/$pngAssetPath")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// -------------------- Helpers --------------------

/**
 * XP “seguro”: não incentiva exceder o planejado (cap no doneSets <= plannedSets)
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