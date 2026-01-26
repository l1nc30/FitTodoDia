package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.repo.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ExerciseItem(
    val id: Long,
    val name: String,
    val muscleGroup: String,
    val gifAssetPath: String
)

data class ExerciseLibraryUiState(
    val query: String = "",
    val selectedGroup: String = "Todos",
    val availableGroups: List<String> = listOf("Todos"),
    val items: List<ExerciseItem> = emptyList(),
    val filtered: List<ExerciseItem> = emptyList()
)

class ExerciseLibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repo = ExerciseRepository(db.exerciseDao())

    private val queryFlow = MutableStateFlow("")
    private val groupFlow = MutableStateFlow("Todos")

    val uiState: StateFlow<ExerciseLibraryUiState> =
        combine(
            repo.observeAll().map { list ->
                list.map {
                    ExerciseItem(
                        id = it.id,
                        name = it.name,
                        muscleGroup = it.muscleGroup,
                        gifAssetPath = it.gifAssetPath
                    )
                }
            },
            queryFlow,
            groupFlow
        ) { items, query, group ->
            val groups = buildList {
                add("Todos")
                addAll(items.map { it.muscleGroup }.distinct().sorted())
            }

            val q = query.trim().lowercase()
            val filtered = items
                .asSequence()
                .filter { group == "Todos" || it.muscleGroup == group }
                .filter { q.isEmpty() || it.name.lowercase().contains(q) }
                .toList()

            ExerciseLibraryUiState(
                query = query,
                selectedGroup = group,
                availableGroups = groups,
                items = items,
                filtered = filtered
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseLibraryUiState()
        )

    fun setQuery(value: String) {
        queryFlow.update { value }
    }

    fun setGroup(value: String) {
        groupFlow.update { value }
    }

    fun clearFilters() {
        queryFlow.update { "" }
        groupFlow.update { "Todos" }
    }
}
