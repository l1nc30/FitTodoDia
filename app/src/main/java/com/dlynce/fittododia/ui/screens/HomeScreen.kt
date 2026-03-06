@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.repo.WeekDayRepository
import com.dlynce.fittododia.ui.components.FtdBadge
import com.dlynce.fittododia.ui.theme.TextSecondary
import com.dlynce.fittododia.utils.*
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

// -------------------- State --------------------

data class HomeUiState(
    val dayId: Int = 1,
    val dayLabel: String = "Hoje",
    val hasWorkout: Boolean = false,
    val workoutName: String = "Sem treino",
    val totalExercisesToday: Int = 0,

    val xpTotal: Int = 0,
    val xpInLevel: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,

    val streakDays: Int = 0,
    val missionDone: Boolean = false,

    val headline: String = "Hoje conta.",
    val subline: String = "Consistência > intensidade.",

    val isNewUser: Boolean = false
)

private data class TodayWorkoutInfo(
    val dayId: Int,
    val dayLabel: String,
    val hasWorkout: Boolean,
    val workoutName: String,
    val totalExercises: Int
)

// -------------------- ViewModel --------------------

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
                        flowOf(TodayWorkoutInfo(dayId, dayLabel, false, "Sem treino", 0))
                    } else {
                        db.workoutExerciseDao().observeRowsByWorkout(workout.id)
                            .map { rows ->
                                TodayWorkoutInfo(dayId, dayLabel, true, workout.name, rows.size)
                            }
                    }
                }
        }

    private val xpTotalFlow: Flow<Int> =
        db.workoutSessionDao().observeAllSummaries()
            .map { list ->
                list.sumOf { row ->
                    estimateXpSafe(
                        exercises = row.totalExercises,
                        plannedSets = row.totalSetsPlanned,
                        doneSets = row.totalSetsDone
                    )
                }
            }.distinctUntilChanged()

    private val streakFlow: Flow<Int> =
        db.workoutSessionDao().observeAllSummaries()
            .flatMapLatest { flow { emit(db.workoutSessionDao().getDistinctDaysDesc()) } }
            .map { computeCurrentStreak(todayEpochDay, it) }
            .distinctUntilChanged()

    private val missionDoneFlow: Flow<Boolean> =
        db.workoutSessionDao().observeAllSummaries()
            .flatMapLatest { flow { emit(db.dailyMissionDao().getByDate(todayEpochDay)?.completed == true) } }
            .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> =
        combine(todayWorkoutFlow, xpTotalFlow, streakFlow, missionDoneFlow
        ) { w, xp, streak, missionDone ->

            val (level, progress) = xpToLevel(xp)
            val perLevel = 500
            val xpInLevel = xp % perLevel

            val headline = when {
                missionDone        -> "Missão concluída ✅"
                streak >= 7        -> "Modo consistência: ON."
                streak >= 3        -> "Ritmo forte. Mantém."
                else               -> "Hoje conta."
            }
            val subline = when {
                missionDone        -> "Boa. Amanhã é só repetir o básico."
                w.hasWorkout       -> "Sem exagero: faz o treino de hoje e acabou."
                else               -> "Crie um treino na Agenda e destrave a missão."
            }

            HomeUiState(
                dayId = w.dayId,
                dayLabel = w.dayLabel,
                hasWorkout = w.hasWorkout,
                workoutName = w.workoutName,
                totalExercisesToday = w.totalExercises,
                xpTotal = xp,
                xpInLevel = xpInLevel,
                level = level,
                levelProgress = progress,
                streakDays = streak,
                missionDone = missionDone,
                headline = headline,
                subline = subline,
                isNewUser = xp == 0 && !w.hasWorkout
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

// -------------------- Screen --------------------

@Composable
fun HomeScreen(
    onGoTreino: () -> Unit = {},
    onGoAgenda: () -> Unit = {},
    onGoProgresso: () -> Unit = {},
    onGoPrograms: () -> Unit = {}
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ── Header ───────────────────────────────────────────────────────────
        HomeHeader(greeting = greetingMessage())

        // ── Novo usuário: onboarding suave ───────────────────────────────────
        if (state.isNewUser) {
            NewUserCard(onGoAgenda = onGoAgenda, onGoPrograms = onGoPrograms)
        } else {
            // ── Card principal: treino de hoje + status + ações ───────────────
            TodayCard(
                state = state,
                onGoTreino = onGoTreino,
                onGoAgenda = onGoAgenda
            )

            // ── Barra de XP compacta ──────────────────────────────────────────
            XpBar(
                level = state.level,
                xpInLevel = state.xpInLevel,
                progress = state.levelProgress,
                streakDays = state.streakDays,
                missionDone = state.missionDone,
                onGoProgresso = onGoProgresso
            )

            // ── Treinos prontos ───────────────────────────────────────────────
            QuickNavCard(
                title = "Treinos prontos",
                subtitle = "Escolha um plano por objetivo e aplique na sua agenda.",
                badge = "NOVO",
                icon = Icons.Filled.EmojiEvents,
                onClick = onGoPrograms
            )

            // ── Atalho para progresso ─────────────────────────────────────────
            QuickNavCard(
                title = "Ver progresso",
                subtitle = "Histórico, nível, streak e totais.",
                icon = Icons.Filled.Star,
                onClick = onGoProgresso
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

// -------------------- Header --------------------

@Composable
private fun HomeHeader(greeting: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "FitTodoDia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                greeting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        // Badge de dia da semana
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                todayShortLabel(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// -------------------- Today Card --------------------

@Composable
private fun TodayCard(
    state: HomeUiState,
    onGoTreino: () -> Unit,
    onGoAgenda: () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = if (state.missionDone)
        MaterialTheme.colorScheme.secondary
    else
        MaterialTheme.colorScheme.primary
    val border = if (state.missionDone)
        accentColor.copy(alpha = 0.40f)
    else
        MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.20f else 0.32f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 6.dp else 14.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Motivacional + dia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.headline,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        state.subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Chip de dia
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        state.dayLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            // Treino do dia
            if (state.hasWorkout) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            state.workoutName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${state.totalExercisesToday} exercícios • sem pressa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "Sem treino cadastrado",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            "Abra a Agenda e monte um treino simples.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            // Ações
            if (state.hasWorkout) {
                Button(
                    onClick = onGoTreino,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = if (state.missionDone)
                            MaterialTheme.colorScheme.onSecondary
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.missionDone) "Treinar novamente" else "Iniciar treino",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = onGoAgenda,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Abrir Agenda")
                }
            } else {
                // Sem treino: um único CTA claro
                Button(
                    onClick = onGoAgenda,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Criar treino na Agenda",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// -------------------- XP Bar --------------------

@Composable
private fun XpBar(
    level: Int,
    xpInLevel: Int,
    progress: Float,
    streakDays: Int,
    missionDone: Boolean,
    onGoProgresso: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "xp_bar"
    )

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)
    val ringFg = MaterialTheme.colorScheme.secondary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 8.dp,
        border = BorderStroke(1.dp, border),
        onClick = onGoProgresso
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mini anel de XP
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            val inset = 4.dp.toPx()
                            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                            val topLeft = Offset(inset, inset)
                            drawArc(
                                color = ringFg.copy(alpha = 0.18f),
                                startAngle = -90f, sweepAngle = 360f,
                                useCenter = false, topLeft = topLeft, size = arcSize, style = stroke
                            )
                            drawArc(
                                color = ringFg,
                                startAngle = -90f, sweepAngle = 360f * animatedProgress,
                                useCenter = false, topLeft = topLeft, size = arcSize, style = stroke
                            )
                        }
                        Text(
                            "$level",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column {
                        Text(
                            "Nível $level",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$xpInLevel / 500 XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }

                // Streak + missão compactos
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (streakDays > 0) {
                        CompactChip(
                            text = "${streakDays}d 🔥",
                            active = streakDays >= 3
                        )
                    }
                    CompactChip(
                        text = if (missionDone) "Missão ✅" else "Missão ⏳",
                        active = missionDone
                    )
                }
            }

            // Barra de XP
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = ringFg,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// -------------------- Quick Nav Card --------------------

@Composable
private fun QuickNavCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.20f else 0.32f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 8.dp,
        border = BorderStroke(1.dp, border),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (!badge.isNullOrBlank()) FtdBadge(text = badge)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

// -------------------- New User Card --------------------

@Composable
private fun NewUserCard(onGoAgenda: () -> Unit, onGoPrograms: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.20f else 0.32f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 6.dp else 14.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("👋", style = MaterialTheme.typography.displaySmall)
            Text(
                "Bem-vindo ao FitTodoDia",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Para começar, monte o treino de cada dia na Agenda — ou escolha um programa pronto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGoPrograms,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Escolher um programa pronto", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onGoAgenda,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Montar minha agenda")
                }
            }
        }
    }
}

// -------------------- Helpers --------------------

@Composable
private fun CompactChip(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active)
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (active)
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun greetingMessage(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Bom dia! Bora treinar?"
        in 12..17 -> "Boa tarde! Bora manter o ritmo?"
        else -> "Boa noite! Um treino leve já resolve."
    }
}

private fun todayShortLabel(): String {
    val zone = ZoneId.of("America/Sao_Paulo")
    return when (LocalDate.now(zone).dayOfWeek) {
        DayOfWeek.MONDAY    -> "Seg"
        DayOfWeek.TUESDAY   -> "Ter"
        DayOfWeek.WEDNESDAY -> "Qua"
        DayOfWeek.THURSDAY  -> "Qui"
        DayOfWeek.FRIDAY    -> "Sex"
        DayOfWeek.SATURDAY  -> "Sáb"
        DayOfWeek.SUNDAY    -> "Dom"
    }
}