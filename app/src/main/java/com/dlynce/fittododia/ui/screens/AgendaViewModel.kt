package com.dlynce.fittododia.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlynce.fittododia.data.db.AppDatabase
import com.dlynce.fittododia.data.repo.WeekDayRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AgendaUiState(
    val days: List<DayItem> = emptyList()
)

data class DayItem(
    val id: Int,
    val name: String,
    val workoutName: String?
)

class AgendaViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repo = WeekDayRepository(db.weekDayDao())

    val uiState: StateFlow<AgendaUiState> =
        repo.observeDaysWithWorkout()
            .map { list ->
                AgendaUiState(
                    days = list.map {
                        DayItem(
                            id = it.dayId,
                            name = it.dayName,
                            workoutName = it.workoutName
                        )
                    }
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AgendaUiState()
            )
}
