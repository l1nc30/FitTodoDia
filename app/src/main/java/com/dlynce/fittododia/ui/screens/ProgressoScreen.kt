@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fireplace
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import com.dlynce.fittododia.utils.*

// -------------------- UI State --------------------

data class ProgressSummary(
    val xpTotal: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val xpInLevel: Int = 0,
    val xpToNext: Int = 0,
    val totalSessions: Int = 0,
    val totalExercisesDone: Int = 0,
    val totalSetsDone: Int = 0,
    val streakDays: Int = 0,
    val missionDoneToday: Boolean = false,
    val recent: List<ProgressHistoryItem> = emptyList()
)

data class ProgressHistoryItem(
    val dateEpochDay: Long,
    val workoutName: String,
    val totalExercises: Int,
    val setsDone: Int,
    val setsPlanned: Int
)

// -------------------- ViewModel --------------------

class ProgressoViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val todayEpochDay = LocalDate.now(zone).toEpochDay()

    private val summariesFlow = db.workoutSessionDao().observeAllSummaries()

    private val missionDoneFlow =
        summariesFlow.flatMapLatest {
            flow { emit(db.dailyMissionDao().getByDate(todayEpochDay)?.completed == true) }
        }.distinctUntilChanged()

    private val streakFlow =
        summariesFlow.flatMapLatest {
            flow { emit(db.workoutSessionDao().getDistinctDaysDesc()) }
        }.map { computeCurrentStreak(todayEpochDay, it) }
            .distinctUntilChanged()

    val uiState: StateFlow<ProgressSummary> =
        combine(summariesFlow, missionDoneFlow, streakFlow) { list, missionDone, streak ->
            val xpTotal = list.sumOf {
                estimateXpSafe(
                    exercises = it.totalExercises,
                    plannedSets = it.totalSetsPlanned,
                    doneSets = it.totalSetsDone
                )
            }
            val (level, progress) = xpToLevel(xpTotal)
            val xpToNext = xpToNextLevel(xpTotal)
            val perLevel = 500
            val xpInLevel = xpTotal % perLevel

            val recent = list
                .sortedByDescending { it.dateEpochDay }
                .take(12)
                .map {
                    ProgressHistoryItem(
                        it.dateEpochDay,
                        it.workoutNameSnapshot,
                        it.totalExercises,
                        it.totalSetsDone,
                        it.totalSetsPlanned
                    )
                }

            ProgressSummary(
                xpTotal, level, progress, xpInLevel, xpToNext,
                list.size,
                list.sumOf { it.totalExercises },
                list.sumOf { it.totalSetsDone },
                streak, missionDone, recent
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ProgressSummary()
        )
}

// -------------------- Screen --------------------

@Composable
fun ProgressoScreen() {
    val vm: ProgressoViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Text(
                "Progresso",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            LevelHeroCard(
                level = state.level,
                progress = state.levelProgress,
                xpInLevel = state.xpInLevel,
                xpToNext = state.xpToNext,
                xpTotal = state.xpTotal
            )
        }

        item {
            StatusRow(
                streakDays = state.streakDays,
                missionDone = state.missionDoneToday
            )
        }

        item {
            TotalsCard(
                sessions = state.totalSessions,
                exercises = state.totalExercisesDone,
                sets = state.totalSetsDone
            )
        }

        item {
            Text(
                "Historico recente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (state.recent.isEmpty()) {
            item { HistoryEmptyState() }
        } else {
            items(state.recent, key = { it.dateEpochDay }) { item ->
                HistoryCard(item)
            }
        }
    }
}

// -------------------- Level Hero Card --------------------

@Composable
private fun LevelHeroCard(
    level: Int,
    progress: Float,
    xpInLevel: Int,
    xpToNext: Int,
    xpTotal: Int
) {
    var prevLevel by remember { mutableStateOf(level) }
    var showLevelUp by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "xpAnim"
    )

    LaunchedEffect(level) {
        if (level > prevLevel) {
            showLevelUp = true
            delay(2500)
            showLevelUp = false
        }
        prevLevel = level
    }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)
    val ringBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val ringFg = MaterialTheme.colorScheme.secondary
    val ringAccent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 4.dp else 12.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Box(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Anel de XP grande
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        val inset = 14.dp.toPx()
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        val topLeft = Offset(inset, inset)
                        drawArc(
                            color = ringBg, startAngle = -90f, sweepAngle = 360f,
                            useCenter = false, topLeft = topLeft, size = arcSize, style = stroke
                        )
                        drawArc(
                            color = ringFg, startAngle = -90f, sweepAngle = 360f * animatedProgress,
                            useCenter = false, topLeft = topLeft, size = arcSize, style = stroke
                        )
                        if (animatedProgress > 0.02f) {
                            drawArc(
                                color = ringAccent.copy(alpha = 0.9f),
                                startAngle = -90f, sweepAngle = 12f,
                                useCenter = false, topLeft = topLeft, size = arcSize, style = stroke
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nivel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            "$level",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.width(20.dp))

                // Detalhes de XP
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "$xpInLevel / 500 XP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Faltam $xpToNext XP pro proximo nivel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = ringFg,
                        trackColor = ringBg
                    )
                    Text(
                        "$xpTotal XP acumulado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }

            // LEVEL UP com animação
            AnimatedVisibility(
                visible = showLevelUp,
                enter = fadeIn(tween(300)) + scaleIn(tween(300)),
                exit = fadeOut(tween(400)) + scaleOut(tween(400)),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        "🎉 LEVEL UP!",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        }
    }
}

// -------------------- Status Row --------------------

@Composable
private fun StatusRow(streakDays: Int, missionDone: Boolean) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 10.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Streak
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Fireplace, null,
                        tint = if (streakDays >= 3) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "$streakDays",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (streakDays >= 3) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    if (streakDays == 1) "dia seguido" else "dias seguidos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    when {
                        streakDays >= 7 -> "🔥 em chamas"
                        streakDays >= 3 -> "bom ritmo"
                        streakDays >= 1 -> "comecando"
                        else -> "sem streak"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Divider(
                modifier = Modifier.height(60.dp).width(1.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            // Missão
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle, null,
                        tint = if (missionDone) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (missionDone) "Feita" else "Pendente",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (missionDone) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Text(
                    "Missao do dia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    if (missionDone) "treino concluido" else "treine para completar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

// -------------------- Totals Card --------------------

@Composable
private fun TotalsCard(sessions: Int, exercises: Int, sets: Int) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 10.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Acumulado total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TotalStat("$sessions", "treinos", Icons.Filled.History, MaterialTheme.colorScheme.primary)
                TotalStat("$exercises", "exercicios", Icons.Filled.FitnessCenter, MaterialTheme.colorScheme.secondary)
                TotalStat("$sets", "series", Icons.Filled.Bolt, MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun TotalStat(value: String, label: String, icon: ImageVector, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

// -------------------- History --------------------

@Composable
private fun HistoryEmptyState() {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.CalendarMonth, null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Text(
                "Nenhum treino registrado ainda",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                "Complete um treino para ver o historico aqui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun HistoryCard(item: ProgressHistoryItem) {
    val dateTxt = remember(item.dateEpochDay) { formatEpochDay(item.dateEpochDay) }
    val todayEpochDay = remember { LocalDate.now(ZoneId.of("America/Sao_Paulo")).toEpochDay() }
    val relativeTxt = remember(item.dateEpochDay) { relativeDay(item.dateEpochDay, todayEpochDay) }

    val ratio = if (item.setsPlanned == 0) 1f
    else item.setsDone.toFloat() / item.setsPlanned.toFloat()
    val complete = ratio >= 1f

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = if (complete) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.primary
    val border = if (complete) accentColor.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 8.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            relativeTxt,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        Text(
                            "• $dateTxt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    Text(
                        item.workoutName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${item.totalExercises} exercicios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "${item.setsDone}/${item.setsPlanned}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    Text(
                        "series",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    if (complete) {
                        Text(
                            "completo ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = ratio.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// -------------------- Helpers --------------------

private fun relativeDay(epochDay: Long, todayEpochDay: Long): String {
    val diff = todayEpochDay - epochDay
    return when (diff) {
        0L -> "Hoje"
        1L -> "Ontem"
        in 2..6 -> "ha $diff dias"
        7L -> "ha 1 semana"
        else -> {
            val date = LocalDate.ofEpochDay(epochDay)
            "${date.dayOfMonth.toString().padStart(2,'0')}/${date.monthValue.toString().padStart(2,'0')}"
        }
    }
}