package com.dlynce.fittododia.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val muscleGroup: String,

    //  No código vira pngAssetPath, mas no banco continua sendo a mesma coluna antiga
    @ColumnInfo(name = "gifAssetPath")
    val pngAssetPath: String
)
