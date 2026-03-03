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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dlynce.fittododia.ui.templates.GoalType
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CatalogHeader()

            GoalButtonsRow(
                goals = goals,
                selected = selectedGoal,
                onSelect = { selectedGoal = it }
            )

            programs.forEach { p ->
                ProgramRichCard(
                    program = p,
                    onClick = { onOpenProgram(p.id) }
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CatalogHeader() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Escolha um plano por objetivo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Você aplica na agenda em poucos toques. Dá para substituir o treino do dia ou adicionar exercícios.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Botões grandes com wrap automático */
@Composable
private fun GoalButtonsRow(
    goals: List<GoalType>,
    selected: GoalType?,
    onSelect: (GoalType) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        goals.forEach { goal ->
            GoalButton(
                goal = goal,
                selected = selected == goal,
                onClick = { onSelect(goal) }
            )
        }
    }
}

@Composable
private fun GoalButton(
    goal: GoalType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val (icon, label) = when (goal) {
        GoalType.INICIANTE -> Icons.Filled.FitnessCenter to "Iniciante"
        GoalType.HIPERTROFIA -> Icons.Filled.FitnessCenter to "Hipertrofia"
        GoalType.EMAGRECIMENTO -> Icons.Filled.LocalFireDepartment to "Emagrecimento"
        GoalType.FORCA -> Icons.Filled.Bolt to "Força"
    }

    val containerColor = if (selected) {
        colors.primary.copy(alpha = 0.15f)
    } else {
        colors.surface
    }

    val borderColor = if (selected) {
        colors.primary
    } else {
        colors.outline.copy(alpha = 0.40f)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .height(56.dp)
            .defaultMinSize(minWidth = 150.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.primary else colors.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProgramRichCard(
    program: ProgramTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(program.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    program.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tags (SEM "semanas")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = {}, label = { Text("${program.daysPerWeek} dias", maxLines = 1) })
                AssistChip(onClick = {}, label = { Text(program.split, maxLines = 1) })
                AssistChip(onClick = {}, label = { Text(program.level, maxLines = 1) })
            }

            Text(
                "Ver detalhes ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}