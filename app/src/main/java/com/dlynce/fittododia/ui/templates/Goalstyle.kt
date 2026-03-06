package com.dlynce.fittododia.ui.templates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class GoalStyle(
    val icon: ImageVector,
    val label: String,
    val accentLight: Color,
    val accentDark: Color,
    val emoji: String
)

fun goalStyle(goal: GoalType): GoalStyle = when (goal) {
    GoalType.INICIANTE     -> GoalStyle(Icons.Filled.SelfImprovement, "Iniciante",     Color(0xFF4CAF50), Color(0xFF81C784), "🌱")
    GoalType.HIPERTROFIA   -> GoalStyle(Icons.Filled.FitnessCenter,   "Hipertrofia",   Color(0xFF7C3AED), Color(0xFFA78BFA), "💪")
    GoalType.EMAGRECIMENTO -> GoalStyle(Icons.Filled.DirectionsRun,   "Emagrecimento", Color(0xFFE53935), Color(0xFFEF9A9A), "🔥")
    GoalType.FORCA         -> GoalStyle(Icons.Filled.Bolt,            "Força",         Color(0xFFF57C00), Color(0xFFFFCC80), "⚡")
}