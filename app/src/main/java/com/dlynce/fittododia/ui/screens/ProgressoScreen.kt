package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.db.dao.SessionSummaryRow
import com.dlynce.fittododia.data.db.entities.DailyMissionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

data class ProgressoUiState(
    val levelInfo: LevelInfo = LevelInfo(level = 1, xpInLevel = 0, xpForNext = 200, totalXp = 0),
    val streak: ProgressoStreakInfo = ProgressoStreakInfo(current = 0, best = 0, trainedToday = false),
    val mission: MissionUi = MissionUi.empty(),
    val achievements: List<AchievementUi> = emptyList(),
    val sessions: List<SessionSummaryUi> = emptyList()
)

data class LevelInfo(
    val level: Int,
    val xpInLevel: Int,
    val xpForNext: Int,
    val totalXp: Int
)

data class ProgressoStreakInfo(
    val current: Int,
    val best: Int,
    val trainedToday: Boolean
)

data class MissionUi(
    val title: String,
    val description: String,
    val completed: Boolean
) {
    companion object { fun empty() = MissionUi("", "", false) }
}

data class AchievementUi(
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val progressText: String? = null
)

data class SessionSummaryUi(
    val id: Long,
    val dateText: String,
    val workoutName: String,
    val durationText: String,
    val setsText: String,
    val xpGained: Int
)

private object MissionKeys {
    const val START = "START"
    const val KEEP = "KEEP"
    const val RECOVER = "RECOVER"
    const val PLAN = "PLAN"
}

class ProgressoViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val zone = ZoneId.of("America/Sao_Paulo")

    private val todayEpoch: Long get() = LocalDate.now(zone).toEpochDay()

    val uiState: StateFlow<ProgressoUiState> =
        db.workoutSessionDao().observeAllSummaries()
            .combine(db.dailyMissionDao().observeByDate(todayEpoch)) { summaries, missionRow ->
                val uniqueDaysDesc = summaries.map { it.dateEpochDay }.distinct().sortedDescending()
                val streakInfo = computeStreak(uniqueDaysDesc, todayEpoch)

                val totalXp = summaries.sumOf { xpForSession(it) }
                val levelInfo = levelFromTotalXp(totalXp)

                val totalSessions = summaries.size
                val totalSetsDoneAll = summaries.sumOf { it.totalSetsDone }

                val missionKeyForDisplay =
                    if (streakInfo.trainedToday) MissionKeys.RECOVER
                    else missionRow?.missionKey ?: defaultMissionKey(streakInfo)

                val completed =
                    if (streakInfo.trainedToday) true
                    else (missionRow?.completed == true)

                val missionUi = missionFromKey(missionKeyForDisplay, completed)

                val achievements = buildAchievements(
                    totalSessions = totalSessions,
                    totalSetsDoneAll = totalSetsDoneAll,
                    currentStreak = streakInfo.current,
                    bestStreak = streakInfo.best
                )

                ProgressoUiState(
                    levelInfo = levelInfo,
                    streak = streakInfo,
                    mission = missionUi,
                    achievements = achievements,
                    sessions = summaries.map { it.toUi() }
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ProgressoUiState()
            )

    init {
        viewModelScope.launch {
            val existing = db.dailyMissionDao().getByDate(todayEpoch)
            if (existing == null) {
                db.dailyMissionDao().upsert(
                    DailyMissionEntity(
                        dateEpochDay = todayEpoch,
                        missionKey = MissionKeys.START,
                        completed = false
                    )
                )
            }
        }
    }

    fun markMissionDone() {
        viewModelScope.launch {
            val row = db.dailyMissionDao().getByDate(todayEpoch)
            if (row != null && !row.completed) {
                db.dailyMissionDao().upsert(row.copy(completed = true))
            }
        }
    }

    fun undoMissionDone() {
        viewModelScope.launch {
            val row = db.dailyMissionDao().getByDate(todayEpoch)
            if (row != null && row.completed) {
                db.dailyMissionDao().upsert(row.copy(completed = false))
            }
        }
    }

    private fun defaultMissionKey(streak: ProgressoStreakInfo): String {
        return when {
            streak.current >= 3 -> MissionKeys.KEEP
            else -> MissionKeys.START
        }
    }

    private fun missionFromKey(key: String, completed: Boolean): MissionUi {
        val (title, desc) = when (key) {
            MissionKeys.RECOVER -> "Recuperação inteligente" to
                    "Você já treinou hoje. Agora ganha quem recupera: água, comida e sono. Sem pressa."
            MissionKeys.KEEP -> "Manter o hábito" to
                    "Hoje é dia de aparecer. Comece leve, com técnica e controle. Segurança primeiro."
            MissionKeys.PLAN -> "Preparar o terreno" to
                    "Organize o próximo treino e deixe tudo pronto. Facilitar é vencer."
            else -> "Dar o primeiro passo" to
                    "Não negocie com a preguiça: abra o treino do dia e faça o aquecimento. Depois você decide o resto."
        }
        return MissionUi(title = title, description = desc, completed = completed)
    }

    private fun SessionSummaryRow.toUi(): SessionSummaryUi {
        val date = LocalDate.ofEpochDay(this.dateEpochDay)
        val dateText = "${date.dayOfMonth.toString().padStart(2, '0')}/" +
                "${date.monthValue.toString().padStart(2, '0')}/" +
                "${date.year}"

        val durationText = formatDuration(this.durationSeconds)
        val setsText = "${this.totalSetsDone}/${this.totalSetsPlanned} séries"
        val xp = xpForSession(this)

        return SessionSummaryUi(
            id = this.id,
            dateText = dateText,
            workoutName = this.workoutNameSnapshot,
            durationText = durationText,
            setsText = setsText,
            xpGained = xp
        )
    }

    private fun formatDuration(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m <= 0) "${r}s" else "${m}m ${r}s"
    }
}

// -------------------- Regras --------------------

// ✅ XP “saudável”: não premia passar do planejado
private fun xpForSession(s: SessionSummaryRow): Int {
    val base = 90
    val perExercise = 10 * s.totalExercises

    val planned = s.totalSetsPlanned.coerceAtLeast(0)
    val doneCapped = s.totalSetsDone.coerceAtLeast(0).coerceAtMost(planned)
    val perSetDone = 2 * doneCapped

    val completionBonus = if (planned > 0 && doneCapped == planned) 25 else 0

    return base + perExercise + perSetDone + completionBonus
}

private fun xpNeededForLevel(level: Int): Int = 200 + max(0, level - 1) * 100

private fun levelFromTotalXp(totalXp: Int): LevelInfo {
    var level = 1
    var remaining = max(0, totalXp)
    var need = xpNeededForLevel(level)

    while (remaining >= need) {
        remaining -= need
        level += 1
        need = xpNeededForLevel(level)
    }

    return LevelInfo(level = level, xpInLevel = remaining, xpForNext = need, totalXp = totalXp)
}

private fun computeStreak(uniqueDaysDesc: List<Long>, todayEpochDay: Long): ProgressoStreakInfo {
    val trainedToday = uniqueDaysDesc.firstOrNull() == todayEpochDay

    var current = 0
    if (trainedToday) {
        current = 1
        var expected = todayEpochDay - 1
        val set = uniqueDaysDesc.toHashSet()
        while (set.contains(expected)) {
            current += 1
            expected -= 1
        }
    }

    var best = 0
    if (uniqueDaysDesc.isNotEmpty()) {
        val asc = uniqueDaysDesc.sorted()
        var run = 1
        best = 1
        for (i in 1 until asc.size) {
            run = if (asc[i] == asc[i - 1] + 1) run + 1 else 1
            best = max(best, run)
        }
    }

    return ProgressoStreakInfo(current = current, best = best, trainedToday = trainedToday)
}

private fun buildAchievements(
    totalSessions: Int,
    totalSetsDoneAll: Int,
    currentStreak: Int,
    bestStreak: Int
): List<AchievementUi> {
    fun streakAchievement(days: Int, title: String): AchievementUi {
        val unlocked = bestStreak >= days
        val progress = "${min(bestStreak, days)}/$days dias"
        return AchievementUi(
            title = title,
            description = "Treine $days dias seguidos (com descanso quando necessário).",
            unlocked = unlocked,
            progressText = if (unlocked) "Concluído" else progress
        )
    }

    fun countAchievement(target: Int, current: Int, title: String, desc: String): AchievementUi {
        val unlocked = current >= target
        val progress = "${min(current, target)}/$target"
        return AchievementUi(
            title = title,
            description = desc,
            unlocked = unlocked,
            progressText = if (unlocked) "Concluído" else progress
        )
    }

    return listOf(
        countAchievement(1, totalSessions, "Primeiro treino", "Finalize 1 treino."),
        streakAchievement(3, "Streak 3 dias"),
        streakAchievement(7, "Streak 7 dias"),
        streakAchievement(14, "Streak 14 dias"),
        streakAchievement(30, "Streak 30 dias"),
        countAchievement(10, totalSessions, "10 treinos", "Finalize 10 treinos."),
        countAchievement(50, totalSetsDoneAll, "50 séries concluídas", "Conclua 50 séries no total (no seu ritmo)."),
        AchievementUi(
            title = "Não pulei hoje",
            description = "Treine hoje para manter a sequência (ou descanse se o corpo pedir).",
            unlocked = currentStreak > 0,
            progressText = if (currentStreak > 0) "Concluído" else "Pendente"
        )
    )
}

// -------------------- UI --------------------

@Composable
fun ProgressoScreen() {
    val vm: ProgressoViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Progresso", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        LevelCard(state.levelInfo)
        Spacer(Modifier.height(10.dp))
        StreakCard(state.streak)
        Spacer(Modifier.height(10.dp))
        MissionCard(
            mission = state.mission,
            onDone = { vm.markMissionDone() },
            onUndo = { vm.undoMissionDone() }
        )

        Spacer(Modifier.height(14.dp))
        Text("Conquistas", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        AchievementsList(state.achievements)

        Spacer(Modifier.height(14.dp))
        Text("Histórico", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (state.sessions.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Ainda não há histórico.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Finalize um treino para ele aparecer aqui.", style = MaterialTheme.typography.bodySmall)
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.sessions, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(s.dateText, style = MaterialTheme.typography.bodySmall)
                            Text(s.durationText, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(s.workoutName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(s.setsText, style = MaterialTheme.typography.bodySmall)
                            Text("+${s.xpGained} XP", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelCard(level: LevelInfo) {
    val progress = if (level.xpForNext <= 0) 0f else level.xpInLevel.toFloat() / level.xpForNext.toFloat()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Nível ${level.level}", style = MaterialTheme.typography.titleMedium)
                Text("${level.totalXp} XP", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text("${level.xpInLevel}/${level.xpForNext} XP para o próximo nível", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StreakCard(streak: ProgressoStreakInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Streak", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Atual: ${streak.current} dias", style = MaterialTheme.typography.bodyMedium)
                Text("Melhor: ${streak.best} dias", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                if (streak.trainedToday) "Você treinou hoje. Excelente. Priorize recuperação."
                else "Ainda não treinou hoje. Faça o primeiro passo — com técnica e segurança.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MissionCard(
    mission: MissionUi,
    onDone: () -> Unit,
    onUndo: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Missão de hoje", style = MaterialTheme.typography.titleMedium)
                Text(if (mission.completed) "Concluída" else "Pendente", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Text(mission.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(mission.description, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(10.dp))

            if (!mission.completed) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Marcar como concluída")
                }
            } else {
                OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                    Text("Desfazer")
                }
            }
        }
    }
}

@Composable
private fun AchievementsList(items: List<AchievementUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (a.unlocked) "Desbloqueado" else "Bloqueado", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(a.description, style = MaterialTheme.typography.bodySmall)

                    if (a.progressText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(a.progressText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
