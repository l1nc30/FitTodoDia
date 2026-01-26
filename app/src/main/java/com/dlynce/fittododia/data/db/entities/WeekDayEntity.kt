package com.dlynce.fittododia.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "week_day")
data class WeekDayEntity(
    @PrimaryKey val id: Int, // 1..7
    val name: String
)


