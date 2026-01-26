package com.dlynce.fittododia.data.db.models

data class DayWithWorkout(
    val dayId: Int,
    val dayName: String,
    val workoutId: Long?,
    val workoutName: String?
)
