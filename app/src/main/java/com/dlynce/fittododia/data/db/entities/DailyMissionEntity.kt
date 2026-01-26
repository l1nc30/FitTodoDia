package com.dlynce.fittododia.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_mission")
data class DailyMissionEntity(
    @PrimaryKey val dateEpochDay: Long,
    val missionKey: String,
    val completed: Boolean
)
