@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.repo.WeekDayRepository
import com.dlynce.fittododia.ui.components.FtdBadge
import com.dlynce.fittododia.ui.theme.TextSecondary
import kotlinx.coroutines.flow.*
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.luminance


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

    val perLevel = 500
    val inLevel = (state.xpTotal.coerceAtLeast(0) % perLevel)
    val xpText = "$inLevel/$perLevel XP"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header minimalista (vibe da referência)
            Text(
                "FitTodoDia",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
                   Text(
                greetingMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(8.dp))

            // ✅ Hierarquia: anel de progresso no topo
            ProgressRing(
                progress = state.levelProgress.coerceIn(0f, 1f),
                levelText = "Level ${state.level}",
                xpText = xpText,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeChip(text = "${state.streakDays}d streak")
                Spacer(Modifier.width(8.dp))
                HomeChip(text = if (state.missionDone) "Missão ✅" else "Missão ⏳")
                Spacer(Modifier.width(8.dp))
                HomeChip(text = "XP ${state.xpTotal}")
            }


            Spacer(Modifier.height(4.dp))

            // ✅ Card com “glass” + CTA dominante
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(state.headline, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(state.subline, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                Spacer(Modifier.height(10.dp))

                Text("Hoje • ${state.dayLabel}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.hasWorkout) state.workoutName else "Sem treino cadastrado",
                    style = MaterialTheme.typography.headlineSmall
                )

                val line = if (state.hasWorkout) {
                    "${state.totalExercisesToday} exercícios • sem pressa"
                } else {
                    "Abra a Agenda e monte um treino simples."
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                Spacer(Modifier.height(12.dp))

                // ✅ CTA mais chamativo
                PrimaryCtaButton(
                    text = if (state.hasWorkout) "Iniciar Treino" else "Criar treino na Agenda",
                    enabled = state.hasWorkout,
                    onClick = { if (state.hasWorkout) onGoTreino() else onGoAgenda() }
                )


                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onGoAgenda,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) { Text("Abrir Agenda") }

                Spacer(Modifier.height(6.dp))

                TextButton(
                    onClick = onGoProgresso,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ver detalhes do progresso") }
            }
        }
    }
}

// ---------- UI Helpers (Home) ----------

private fun greetingMessage(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Bom dia! Bora treinar?"
        in 12..17 -> "Boa tarde! Bora manter o ritmo?"
        else -> "Boa noite! Um treino leve já resolve."
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    levelText: String,
    xpText: String,
    modifier: Modifier = Modifier
) {
    val ringBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val ringFg = MaterialTheme.colorScheme.secondary
    val ringAccent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)

            val inset = 18.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = ringBg,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            drawArc(
                color = ringFg,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // acento roxo sutil (dá vibe premium)
            drawArc(
                color = ringAccent.copy(alpha = 0.85f),
                startAngle = -90f,
                sweepAngle = 16f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(levelText, style = MaterialTheme.typography.headlineSmall)
            Text(xpText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val container = if (isLight) {
        // ✅ sólido no claro
        MaterialTheme.colorScheme.surface
    } else {
        // ✅ glass no escuro
        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
    }

    val border = if (isLight) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = container,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 6.dp else 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}


@Composable
private fun PrimaryCtaButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HomeChip(text: String) {
    val shape = RoundedCornerShape(999.dp)
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Surface(
        shape = shape,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Helpers (lógica) ----------

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
