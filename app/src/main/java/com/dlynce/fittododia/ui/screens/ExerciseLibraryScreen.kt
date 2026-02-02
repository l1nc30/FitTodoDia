package com.dlynce.fittododia.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dlynce.fittododia.ui.theme.Surface2

@Composable
fun ExerciseLibraryScreen(
    dayId: Int,
    onBack: () -> Unit,
    onSelected: (exerciseId: Long) -> Unit
) {
    val vm: ExerciseLibraryViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    var groupExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Biblioteca", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Escolha um exercício para adicionar ao treino",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onBack) { Text("Voltar") }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = { Text("Buscar exercício") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        // ✅ Dropdown robusto: campo readOnly + overlay clicável + IconButton
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.selectedGroup,
                onValueChange = {},
                readOnly = true,
                label = { Text("Grupo muscular") },
                trailingIcon = {
                    IconButton(onClick = { groupExpanded = true }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        role = Role.Button,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { groupExpanded = true }
            )

            DropdownMenu(
                expanded = groupExpanded,
                onDismissRequest = { groupExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                state.availableGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group) },
                        onClick = {
                            vm.setGroup(group)
                            groupExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = vm::clearFilters,
                modifier = Modifier.heightIn(min = 44.dp)
            ) { Text("Limpar") }

            Button(
                onClick = { groupExpanded = false },
                modifier = Modifier.heightIn(min = 44.dp)
            ) { Text("Ok") }
        }

        Spacer(Modifier.height(14.dp))

        if (state.items.isEmpty()) {
            Text(
                "Nenhum exercício cadastrado. Se você adicionou seed agora, desinstale o app e rode novamente para recriar o banco.",
                style = MaterialTheme.typography.bodyMedium
            )
            return
        }

        Text(
            "Resultados: ${state.filtered.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(8.dp))

        if (state.filtered.isEmpty()) {
            Text("Nada encontrado com esses filtros.", style = MaterialTheme.typography.bodyMedium)
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                items = state.filtered,
                key = { it.id }
            ) { item ->
                ExerciseRowCard(
                    name = item.name,
                    group = item.muscleGroup,
                    pngAssetPath = item.pngAssetPath,
                    isLight = isLight,
                    onSelect = { onSelected(item.id) }
                )
            }
        }
    }
}

@Composable
private fun ExerciseRowCard(
    name: String,
    group: String,
    pngAssetPath: String,
    isLight: Boolean,
    onSelect: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val border = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.18f else 0.28f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 1.dp else 0.dp,
        shadowElevation = if (isLight) 2.dp else 10.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExerciseThumbFromAsset(
                pngAssetPath = pngAssetPath,
                modifier = Modifier.size(76.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    group,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }

            OutlinedButton(
                onClick = onSelect,
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text("Selecionar", maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExerciseThumbFromAsset(
    pngAssetPath: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val ctx = LocalContext.current

    Surface(
        modifier = modifier,
        shape = shape,
        color = Surface2, // ✅ slot escuro fixo: harmoniza com suas artes
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
        )
    ) {
        if (pngAssetPath.isBlank()) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ImageNotSupported,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        } else {
            val assetUrl = "file:///android_asset/$pngAssetPath"
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(assetUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
