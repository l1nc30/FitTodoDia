package com.dlynce.fittododia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter

@Composable
fun ExerciseMedia(
    assetPath: String?,
    modifier: Modifier = Modifier,
    height: Int = 220
) {
    val context = LocalContext.current

    val exists = remember(assetPath) {
        val p = assetPath?.trim().orEmpty()
        if (p.isBlank()) return@remember false
        try {
            context.assets.open(p).close()
            true
        } catch (_: Throwable) {
            false
        }
    }

    if (exists) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/${assetPath!!.trim()}")
                .crossfade(true)
                .build(),
            contentDescription = "Demonstração do exercício",
            modifier = modifier
                .fillMaxWidth()
                .height(height.dp)
        )
    } else {
        // Placeholder seguro
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Imagem ainda não adicionada",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
