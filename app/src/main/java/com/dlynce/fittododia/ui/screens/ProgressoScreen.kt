@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

// -------------------- UI State --------------------

data class ProgressSummary(
    val xpTotal: Int = 0,
    val level: Int = 1,
    val levelProgress: Float = 0f,
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

    private val summariesFlow =
        db.workoutSessionDao()
            .observeAllSummaries()

    private val missionDoneFlow =
        db.workoutSessionDao()
            .observeAllSummaries()
            .flatMapLatest {
                flow { emit(db.dailyMissionDao().getByDate(todayEpochDay)?.completed == true) }
            }
            .distinctUntilChanged()

    private val streakFlow =
        db.workoutSessionDao()
            .observeAllSummaries()
            .flatMapLatest { flow { emit(db.workoutSessionDao().getDistinctDaysDesc()) } }
            .map { daysDesc -> computeCurrentStreak(todayEpochDay, daysDesc) }
            .distinctUntilChanged()

    val uiState: StateFlow<ProgressSummary> =
        combine(summariesFlow, missionDoneFlow, streakFlow) { list, missionDone, streak ->

            val xpTotal = list.sumOf { row ->
                estimateXpSafe(
                    exercises = row.totalExercises,
                    plannedSets = row.totalSetsPlanned,
                    doneSets = row.totalSetsDone
                )
            }

            val (level, progress) = xpToLevel(xpTotal)
            val xpToNext = xpToNextLevel(xpTotal)

            val totalSessions = list.size
            val totalExercisesDone = list.sumOf { it.totalExercises }
            val totalSetsDone = list.sumOf { it.totalSetsDone }

            val recent = list
                .sortedByDescending { it.dateEpochDay }
                .take(12)
                .map {
                    ProgressHistoryItem(
                        dateEpochDay = it.dateEpochDay,
                        workoutName = it.workoutNameSnapshot,
                        totalExercises = it.totalExercises,
                        setsDone = it.totalSetsDone,
                        setsPlanned = it.totalSetsPlanned
                    )
                }

            ProgressSummary(
                xpTotal = xpTotal,
                level = level,
                levelProgress = progress,
                xpToNext = xpToNext,
                totalSessions = totalSessions,
                totalExercisesDone = totalExercisesDone,
                totalSetsDone = totalSetsDone,
                streakDays = streak,
                missionDoneToday = missionDone,
                recent = recent
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgressSummary()
        )
}

// -------------------- Screen --------------------

@Composable
fun ProgressoScreen() {
    val vm: ProgressoViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Progresso") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                HeaderRingCard(
                    level = state.level,
                    progress = state.levelProgress,
                    xpTotal = state.xpTotal,
                    xpToNext = state.xpToNext
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.weight(1f)) {
                        MetricCard(
                            title = "Streak",
                            value = "${state.streakDays}d",
                            icon = Icons.Filled.Fireplace,
                            subtitle = if (state.streakDays >= 3) "mantendo o ritmo" else "começa leve"
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        MetricCard(
                            title = "Missão",
                            value = if (state.missionDoneToday) "✅" else "⏳",
                            icon = Icons.Filled.CheckCircle,
                            subtitle = if (state.missionDoneToday) "feita hoje" else "pendente"
                        )
                    }
                }
            }

            item {
                SurfaceCard {
                    Text("Totais", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))

                    TotalsRow(label = "Sessões", value = state.totalSessions.toString(), icon = Icons.Filled.History)
                    TotalsRow(label = "Exercícios", value = state.totalExercisesDone.toString(), icon = Icons.Filled.EmojiEvents)
                    TotalsRow(label = "Séries feitas", value = state.totalSetsDone.toString(), icon = Icons.Filled.Bolt)

                    Spacer(Modifier.height(6.dp))

                    AssistChip(
                        onClick = {},
                        label = { Text("Consistência > intensidade") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            item {
                Text("Histórico recente", style = MaterialTheme.typography.titleMedium)
            }

            if (state.recent.isEmpty()) {
                item {
                    SurfaceCard {
                        Text("Sem histórico ainda.", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Finalize um treino para registrar e ganhar XP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // ✅ versão compatível: sem "items ="
                items(
                    count = state.recent.size,
                    key = { idx -> state.recent[idx].dateEpochDay.toString() + state.recent[idx].workoutName }
                ) { idx ->
                    val item = state.recent[idx]
                    HistoryCard(item)
                }
            }

            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

// -------------------- UI Components --------------------

@Composable
private fun HeaderRingCard(
    level: Int,
    progress: Float,
    xpTotal: Int,
    xpToNext: Int
) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Seu nível", style = MaterialTheme.typography.titleMedium)
                Text("Nível $level", style = MaterialTheme.typography.headlineSmall)

                Text(
                    "$xpTotal XP total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                )
                Text(
                    "Faltam $xpToNext XP pra subir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                )
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                CircularProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    strokeWidth = 10.dp,
                    modifier = Modifier.fillMaxSize(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    subtitle: String
) {
    SurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TotalsRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HistoryCard(item: ProgressHistoryItem) {
    val dateTxt = remember(item.dateEpochDay) { formatEpochDay(item.dateEpochDay) }
    val ratio = if (item.setsPlanned <= 0) 0f else item.setsDone.toFloat() / item.setsPlanned.toFloat()

    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Text(
                        dateTxt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }

                Text(
                    item.workoutName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    "${item.totalExercises} exercícios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("${item.setsDone}/${item.setsPlanned}", style = MaterialTheme.typography.titleMedium)
                LinearMini(ratio)
            }
        }
    }
}

@Composable
private fun LinearMini(progress: Float) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth(0.35f)
            .height(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
        ) {}
    }
}

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
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
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

// -------------------- Helpers --------------------

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

private fun formatEpochDay(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val dow = when (date.dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "Seg"
        java.time.DayOfWeek.TUESDAY -> "Ter"
        java.time.DayOfWeek.WEDNESDAY -> "Qua"
        java.time.DayOfWeek.THURSDAY -> "Qui"
        java.time.DayOfWeek.FRIDAY -> "Sex"
        java.time.DayOfWeek.SATURDAY -> "Sáb"
        java.time.DayOfWeek.SUNDAY -> "Dom"
    }
    return "$dow • ${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthValue.toString().padStart(2, '0')}"
}
