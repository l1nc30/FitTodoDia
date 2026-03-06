package com.dlynce.fittododia.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.dlynce.fittododia.ui.theme.NeonCyan
import com.dlynce.fittododia.ui.theme.TextSecondary

@Composable
fun FtdCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            content()
        }
    }
}

@Composable
fun FtdBadge(text: String, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = { },
        label = { Text(text) },
        modifier = modifier,
        border = BorderStroke(1.dp, NeonCyan),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * Card de superfície padrão do app — bordas arredondadas, sombra adaptada ao tema.
 * Use este em vez de definir SurfaceCard localmente em cada Screen.
 */
@Composable
fun FtdSurfaceCard(
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
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/**
 * Card de estado vazio padronizado — título + subtítulo descritivo.
 * Use quando uma lista ou tela não tem conteúdo ainda.
 */
@Composable
fun FtdEmptyStateCard(title: String, subtitle: String) {
    FtdSurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
