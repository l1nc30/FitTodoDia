package com.dlynce.fittododia.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout",
    indices = [Index("weekDayId", unique = true)]
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val weekDayId: Int,
    val name: String,
    val createdAtEpochDay: Long
)
