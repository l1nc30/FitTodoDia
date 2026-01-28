@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import com.dlynce.fittododia.data.repo.WeekDayRepository
import com.dlynce.fittododia.ui.components.FtdBadge
import com.dlynce.fittododia.ui.components.FtdCard
import com.dlynce.fittododia.ui.theme.TextSecondary
import kotlinx.coroutines.flow.*
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

data class HomeUiState(
    val dayId: Int = 1,
    val dayLabel: String = "Hoje",
    val hasWorkout: Boolean = false,
    val workoutName: String = "Sem treino",
    val totalExercisesToday: Int = 0,

    val xpTotal: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,

    val streakDays: Int = 0,
    val missionDone: Boolean = false,

    val headline: String = "Hoje conta.",
    val subline: String = "Consistência > intensidade."
)

private data class TodayWorkoutInfo(
    val dayId: Int,
    val dayLabel: String,
    val hasWorkout: Boolean,
    val workoutName: String,
    val totalExercises: Int
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val weekRepo = WeekDayRepository(db.weekDayDao())
    private val zone = ZoneId.of("America/Sao_Paulo")

    private val today = LocalDate.now(zone)
    private val todayEpochDay = today.toEpochDay()
    private val todayKey = baseKeyForDayOfWeek(today.dayOfWeek)

    private val dayFlow: Flow<Pair<Int, String>> =
        weekRepo.observeDaysWithWorkout()
            .map { list ->
                val match = list.firstOrNull { normalizeDayName(it.dayName) == todayKey }
                if (match != null) match.dayId to match.dayName
                else fallbackDayId(today.dayOfWeek) to labelForDayOfWeek(today.dayOfWeek)
            }
            .distinctUntilChanged()

    private val todayWorkoutFlow: Flow<TodayWorkoutInfo> =
        dayFlow.flatMapLatest { (dayId, dayLabel) ->
            db.workoutDao().observeWorkoutByDay(dayId)
                .flatMapLatest { workout ->
                    if (workout == null) {
                        flowOf(
                            TodayWorkoutInfo(
                                dayId = dayId,
                                dayLabel = dayLabel,
                                hasWorkout = false,
                                workoutName = "Sem treino",
                                totalExercises = 0
                            )
                        )
                    } else {
                        db.workoutExerciseDao().observeRowsByWorkout(workout.id)
                            .map { rows ->
                                TodayWorkoutInfo(
                                    dayId = dayId,
                                    dayLabel = dayLabel,
                                    hasWorkout = true,
                                    workoutName = workout.name,
                                    totalExercises = rows.size
                                )
                            }
                    }
                }
        }

    private val xpTotalFlow: Flow<Int> =
        db.workoutSessionDao()
            .observeAllSummaries()
            .map { list ->
                list.sumOf { row ->
                    estimateXpSafe(
                        exercises = row.totalExercises,
                        plannedSets = row.totalSetsPlanned,
                        doneSets = row.totalSetsDone
                    )
                }
            }
            .distinctUntilChanged()

    private val streakFlow: Flow<Int> =
        db.workoutSessionDao()
            .observeAllSummaries()
            .flatMapLatest { flow { emit(db.workoutSessionDao().getDistinctDaysDesc()) } }
            .map { daysDesc -> computeCurrentStreak(todayEpochDay, daysDesc) }
            .distinctUntilChanged()

    private val missionDoneFlow: Flow<Boolean> =
        db.workoutSessionDao()
            .observeAllSummaries()
            .flatMapLatest { flow { emit(db.dailyMissionDao().getByDate(todayEpochDay)?.completed == true) } }
            .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> =
        combine(
            todayWorkoutFlow,
            xpTotalFlow,
            streakFlow,
            missionDoneFlow
        ) { w: TodayWorkoutInfo, xp: Int, streak: Int, missionDone: Boolean ->

            val (level, progress) = xpToLevel(xp)

            val headline = when {
                missionDone -> "Missão concluída ✅"
                streak >= 7 -> "Modo consistência: ON."
                streak >= 3 -> "Ritmo forte. Mantém."
                else -> "Hoje conta."
            }

            val subline = when {
                missionDone -> "Boa. Amanhã é só repetir o básico."
                w.hasWorkout -> "Sem exagero: faz o treino de hoje e acabou."
                else -> "Crie um treino na Agenda e destrave a missão."
            }

            HomeUiState(
                dayId = w.dayId,
                dayLabel = w.dayLabel,
                hasWorkout = w.hasWorkout,
                workoutName = w.workoutName,
                totalExercisesToday = w.totalExercises,
                xpTotal = xp,
                level = level,
                levelProgress = progress,
                streakDays = streak,
                missionDone = missionDone,
                headline = headline,
                subline = subline
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState()
        )
}

@Composable
fun HomeScreen(
    onGoTreino: () -> Unit = {},
    onGoAgenda: () -> Unit = {},
    onGoProgresso: () -> Unit = {}
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("FitTodoDia") },
                actions = { TextButton(onClick = onGoProgresso) { Text("Progresso") } }
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

            FtdCard(
                title = state.headline,
                subtitle = state.subline
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FtdBadge(text = "Nível ${state.level}")
                    FtdBadge(text = "${state.streakDays}d streak")
                    FtdBadge(text = if (state.missionDone) "Missão ✅" else "Missão ⏳")
                }
            }

            FtdCard(
                title = "Hoje • ${state.dayLabel}",
                subtitle = if (state.hasWorkout) "Treino do dia pronto. Foco em qualidade." else "Sem treino cadastrado."
            ) {
                Text(state.workoutName, style = MaterialTheme.typography.headlineSmall)

                val line = if (state.hasWorkout) {
                    "${state.totalExercisesToday} exercícios • sem pressa"
                } else {
                    "Abra a Agenda e monte um treino simples."
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onGoTreino,
                    enabled = state.hasWorkout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.hasWorkout) "Começar treino" else "Criar treino na Agenda")
                }

                OutlinedButton(
                    onClick = onGoAgenda,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir Agenda") }
            }

            FtdCard(
                title = "Seu progresso",
                subtitle = "XP e níveis recompensam constância (não volume)."
            ) {
                Text("${state.xpTotal} XP total", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = state.levelProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Nível ${state.level} • faltam ${xpToNextLevel(state.xpTotal)} XP para subir",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(Modifier.height(8.dp))

                Button(onClick = onGoProgresso, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver detalhes")
                }
            }
        }
    }
}

// ---------- Helpers ----------

private fun estimateXpSafe(exercises: Int, plannedSets: Int, doneSets: Int): Int {
    val base = 90
    val ex = 10 * max(0, exercises)
    val planned = max(0, plannedSets)
    val doneCapped = min(max(0, doneSets), planned)
    val perSet = 2 * doneCapped
    val completionBonus = if (planned > 0 && doneCapped == planned) 25 else 0
    return base + ex + perSet + completionBonus
}

private fun xpToLevel(totalXp: Int): Pair<Int, Float> {
    val xp = max(0, totalXp)
    val perLevel = 500
    val level = (xp / perLevel) + 1
    val inLevel = xp % perLevel
    val progress = inLevel.toFloat() / perLevel.toFloat()
    return level to progress
}

private fun xpToNextLevel(totalXp: Int): Int {
    val xp = max(0, totalXp)
    val perLevel = 500
    val inLevel = xp % perLevel
    return perLevel - inLevel
}

private fun computeCurrentStreak(todayEpochDay: Long, daysDesc: List<Long>): Int {
    if (daysDesc.isEmpty()) return 0
    val set = daysDesc.toHashSet()
    var start = todayEpochDay
    if (!set.contains(start)) start = todayEpochDay - 1
    if (!set.contains(start)) return 0
    var streak = 0
    var cur = start
    while (set.contains(cur)) {
        streak++
        cur -= 1
    }
    return streak
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

private fun fallbackDayId(d: DayOfWeek): Int = when (d) {
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    DayOfWeek.SUNDAY -> 7
}

private fun normalizeDayName(name: String): String {
    val lower = name.trim().lowercase()
    val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    val noFeira = noAccents.replace("-feira", "").replace(" feira", "")
    return noFeira.replace("\\s+".toRegex(), " ").trim()
}
