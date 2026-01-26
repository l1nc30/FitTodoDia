package com.dlynce.fittododia.data.repo

import com.dlynce.fittododia.data.db.dao.ExerciseDao
import com.dlynce.fittododia.data.db.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(
    private val dao: ExerciseDao
) {
    fun observeAll(): Flow<List<ExerciseEntity>> = dao.observeAll()
}
