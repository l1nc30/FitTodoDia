package com.dlynce.fittododia.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dlynce.fittododia.data.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutExerciseDao {
    @Query("SELECT workoutId FROM workout_exercise WHERE id = :rowId LIMIT 1")
    suspend fun getWorkoutIdByRowId(rowId: Long): Long?

    @Transaction
    suspend fun deleteAndReorderByRowId(rowId: Long) {
        val workoutId = getWorkoutIdByRowId(rowId) ?: return
        deleteById(rowId)
        val ids = getIdsByWorkoutOrdered(workoutId)
        ids.forEachIndexed { idx, id ->
            updateOrderIndex(id, idx)
        }
    }


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WorkoutExerciseEntity): Long

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM workout_exercise WHERE workoutId = :workoutId")
    suspend fun getMaxOrderIndex(workoutId: Long): Int

    @Query("""
        SELECT we.id as id,
               e.name as exerciseName,
               e.muscleGroup as muscleGroup,
               e.gifAssetPath as gifAssetPath,
               we.sets as sets,
               we.reps as reps,
               we.restSeconds as restSeconds,
               we.orderIndex as orderIndex
        FROM workout_exercise we
        JOIN exercise e ON e.id = we.exerciseId
        WHERE we.workoutId = :workoutId
        ORDER BY we.orderIndex ASC
    """)
    fun observeRowsByWorkout(workoutId: Long): Flow<List<WorkoutExerciseRow>>

    @Query("DELETE FROM workout_exercise WHERE workoutId = :workoutId")
    suspend fun deleteByWorkout(workoutId: Long)

    // ✅ observar 1 item por rowId (tela de edição)
    @Query("""
        SELECT we.id as id,
               e.name as exerciseName,
               e.muscleGroup as muscleGroup,
               e.gifAssetPath as gifAssetPath,
               we.sets as sets,
               we.reps as reps,
               we.restSeconds as restSeconds,
               we.orderIndex as orderIndex
        FROM workout_exercise we
        JOIN exercise e ON e.id = we.exerciseId
        WHERE we.id = :rowId
        LIMIT 1
    """)
    fun observeRowById(rowId: Long): Flow<WorkoutExerciseRow?>

    // ✅ update dos campos editáveis
    @Query("""
        UPDATE workout_exercise
        SET sets = :sets,
            reps = :reps,
            restSeconds = :restSeconds
        WHERE id = :rowId
    """)
    suspend fun updateRow(rowId: Long, sets: Int, reps: String, restSeconds: Int?)

    // ✅ delete por rowId
    @Query("DELETE FROM workout_exercise WHERE id = :rowId")
    suspend fun deleteById(rowId: Long)

    // --- Reordenação ---
    @Query("""
        SELECT id FROM workout_exercise
        WHERE workoutId = :workoutId
        ORDER BY orderIndex ASC
    """)
    suspend fun getIdsByWorkoutOrdered(workoutId: Long): List<Long>

    @Query("UPDATE workout_exercise SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int)

    @Transaction
    suspend fun deleteAndReorder(workoutId: Long, rowId: Long) {
        deleteById(rowId)
        val ids = getIdsByWorkoutOrdered(workoutId)
        ids.forEachIndexed { idx, id ->
            updateOrderIndex(id, idx)
        }
    }
}
