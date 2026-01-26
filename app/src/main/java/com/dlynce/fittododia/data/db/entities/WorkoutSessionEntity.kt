package com.dlynce.fittododia.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_session",
    indices = [
        Index(value = ["dateEpochDay"]),
        Index(value = ["workoutId"]),
        Index(value = ["weekDayId"])
    ]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val weekDayId: Int,
    val dateEpochDay: Long,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val workoutNameSnapshot: String,
    val totalExercises: Int,
    val totalSetsPlanned: Int,
    val totalSetsDone: Int
)
