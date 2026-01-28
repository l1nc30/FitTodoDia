package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.db.dao.WorkoutExerciseRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

data class EditWorkoutExerciseUiState(
    val loading: Boolean = true,
    val row: WorkoutExerciseRow? = null,
    val error: String? = null,
    val saving: Boolean = false
)

class EditWorkoutExerciseViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    fun observeRow(rowId: Long): Flow<WorkoutExerciseRow?> =
        db.workoutExerciseDao().observeRowById(rowId)

    fun save(rowId: Long, sets: Int, reps: String, restSeconds: Int?, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                db.workoutExerciseDao().updateRow(rowId, sets, reps.trim(), restSeconds)
                onDone()
            } catch (t: Throwable) {
                onError("Falha ao salvar. Detalhe: ${t.message ?: "erro desconhecido"}")
            }
        }
    }

    fun delete(rowId: Long, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                db.workoutExerciseDao().deleteById(rowId)
                onDone()
            } catch (t: Throwable) {
                onError("Falha ao remover. Detalhe: ${t.message ?: "erro desconhecido"}")
            }
        }
    }
}

@Composable
private fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
}

@Composable
fun EditWorkoutExerciseScreen(
    rowId: Long,
    onBack: () -> Unit
) {
    val vm: EditWorkoutExerciseViewModel = viewModel()
    val context = LocalContext.current
    val gifLoader = rememberGifImageLoader()

    val rowFlow = remember(rowId) { vm.observeRow(rowId) }
    val row by rowFlow.collectAsState(initial = null)

    var setsText by remember { mutableStateOf("3") }
    var repsText by remember { mutableStateOf("10") }
    var restText by remember { mutableStateOf("60") } // vazio = null

    var localError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(row) {
        row?.let {
            setsText = it.sets.toString()
            repsText = it.reps
            restText = it.restSeconds?.toString() ?: ""
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Editar exercício", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (row == null) {
            Text("Carregando...", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBack) { Text("Voltar") }
            return
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(row!!.exerciseName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (row!!.muscleGroup.isNotBlank()) {
                    Text(row!!.muscleGroup, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (row!!.pngAssetPath.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/${row!!.pngAssetPath}")
                    .crossfade(true)
                    .build(),
                imageLoader = gifLoader,
                contentDescription = "Demonstração do exercício",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = setsText,
            onValueChange = { setsText = it },
            label = { Text("Séries") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = repsText,
            onValueChange = { repsText = it },
            label = { Text("Repetições (ou tempo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = restText,
            onValueChange = { restText = it },
            label = { Text("Descanso (segundos) — opcional") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (localError != null) {
            Text(localError!!, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                localError = null

                val sets = setsText.toIntOrNull()
                if (sets == null || sets < 1) {
                    localError = "Séries deve ser um número >= 1."
                    return@Button
                }

                val reps = repsText.trim()
                if (reps.isEmpty()) {
                    localError = "Repetições/tempo não pode ficar vazio."
                    return@Button
                }

                val rest = restText.trim().let { txt ->
                    if (txt.isEmpty()) null
                    else {
                        val r = txt.toIntOrNull()
                        if (r == null || r < 0) {
                            localError = "Descanso deve ser número >= 0 (ou deixe vazio)."
                            return@Button
                        }
                        r
                    }
                }

                vm.save(
                    rowId = rowId,
                    sets = sets,
                    reps = reps,
                    restSeconds = rest,
                    onDone = onBack,
                    onError = { msg -> localError = msg }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar alterações")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Remover do treino")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voltar")
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remover exercício") },
            text = { Text("Deseja remover este exercício do treino?") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    vm.delete(
                        rowId = rowId,
                        onDone = onBack,
                        onError = { msg -> localError = msg }
                    )
                }) { Text("Remover") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
