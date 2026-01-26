package com.dlynce.fittododia.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1, // sempre 1
    val adsRemoved: Boolean,
    val reminderEnabled: Boolean
)
