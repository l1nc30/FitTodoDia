@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.dlynce.fittododia.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dlynce.fittododia.ui.templates.GoalStyle
import com.dlynce.fittododia.ui.templates.GoalType
import com.dlynce.fittododia.ui.templates.goalStyle
import com.dlynce.fittododia.ui.templates.ProgramTemplate
import com.dlynce.fittododia.ui.templates.ProgramTemplatesRepo



@Composable
fun ProgramsScreen(
    onBack: () -> Unit,
    onOpenProgram: (programId: String) -> Unit
) {
    var selectedGoal by remember { mutableStateOf<GoalType?>(GoalType.HIPERTROFIA) }
    val goals = remember { ProgramTemplatesRepo.goals() }
    val programs: List<ProgramTemplate> = remember(selectedGoal) {
        selectedGoal?.let { ProgramTemplatesRepo.programsByGoal(it) } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treinos prontos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Filtros de objetivo ──────────────────────────────────────────
            Text(
                "Escolha seu objetivo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                goals.forEach { goal ->
                    GoalFilterChip(
                        goal = goal,
                        selected = selectedGoal == goal,
                        onClick = { selectedGoal = goal }
                    )
                }
            }

            // ── Lista de programas ───────────────────────────────────────────
            if (programs.isEmpty()) {
                EmptyGoalState()
            } else {
                Text(
                    "${programs.size} programa${if (programs.size > 1) "s" else ""} disponível${if (programs.size > 1) "is" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                programs.forEach { p ->
                    ProgramCard(program = p, onClick = { onOpenProgram(p.id) })
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Chip de filtro por objetivo ──────────────────────────────────────────────

@Composable
private fun GoalFilterChip(goal: GoalType, selected: Boolean, onClick: () -> Unit) {
    val style = goalStyle(goal)
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accent = if (isLight) style.accentLight else style.accentDark

    val containerColor = if (selected) accent.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surface
    val borderColor    = if (selected) accent
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val contentColor   = if (selected) accent
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (selected) 0.dp else 0.dp,
        shadowElevation = if (selected) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}

// ── Card de programa ─────────────────────────────────────────────────────────

@Composable
private fun ProgramCard(program: ProgramTemplate, onClick: () -> Unit) {
    val style = goalStyle(program.goal)
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accent = if (isLight) style.accentLight else style.accentDark

    val border = BorderStroke(1.dp, accent.copy(alpha = if (isLight) 0.30f else 0.45f))

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 3.dp else 10.dp,
        border = border
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Header: emoji + título + nível
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ícone colorido do objetivo
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        style.emoji,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        program.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        program.level,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }

                // Dias/semana em destaque
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${program.daysPerWeek}x",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            "semana",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // Descrição
            Text(
                program.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )

            // Tags informativas — Surface estático, sem onClick falso
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoTag(text = program.split, accent = accent)
                InfoTag(text = "${program.durationWeeks} semanas", accent = accent)
            }

            // CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ver plano completo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Spacer(Modifier.width(4.dp))
                Text("›", style = MaterialTheme.typography.titleMedium, color = accent)
            }
        }
    }
}

// ── Tag informativa estática (sem onClick falso) ──────────────────────────────

@Composable
private fun InfoTag(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

// ── Estado vazio ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyGoalState() {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.16f else 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🏋️", style = MaterialTheme.typography.displaySmall)
            Text(
                "Nenhum programa nessa categoria ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}