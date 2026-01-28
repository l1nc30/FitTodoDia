package com.dlynce.fittododia.data.db.dao

data class WorkoutExerciseRow(
    val id: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val pngAssetPath: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int?,
    val orderIndex: Int
)
