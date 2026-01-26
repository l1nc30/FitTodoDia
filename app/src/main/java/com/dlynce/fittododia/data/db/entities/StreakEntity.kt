package com.dlynce.fittododia.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streak")
data class StreakEntity(
    @PrimaryKey val id: Int = 1, // sempre 1
    val currentStreak: Int,
    val lastWorkoutEpochDay: Long? // null se nunca treinou
)
