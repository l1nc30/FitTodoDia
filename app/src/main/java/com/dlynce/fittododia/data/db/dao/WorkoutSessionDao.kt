package com.dlynce.fittododia.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dlynce.fittododia.data.db.entities.WorkoutSessionEntity
import com.dlynce.fittododia.data.db.entities.WorkoutSessionExerciseEntity
import kotlinx.coroutines.flow.Flow

data class SessionSummaryRow(
    val id: Long,
    val dateEpochDay: Long,
    val workoutNameSnapshot: String,
    val durationSeconds: Int,
    val totalExercises: Int,
    val totalSetsPlanned: Int,
    val totalSetsDone: Int
)

@Dao
interface WorkoutSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercises(items: List<WorkoutSessionExerciseEntity>)

    @Query("""
        SELECT id, dateEpochDay, workoutNameSnapshot, durationSeconds, totalExercises, totalSetsPlanned, totalSetsDone
        FROM workout_session
        ORDER BY dateEpochDay DESC, id DESC
    """)
    fun observeAllSummaries(): Flow<List<SessionSummaryRow>>

    // ✅ usado para streak (datas únicas, do mais recente pro mais antigo)
    @Query("""
        SELECT DISTINCT dateEpochDay
        FROM workout_session
        ORDER BY dateEpochDay DESC
    """)
    suspend fun getDistinctDaysDesc(): List<Long>

    @Query("DELETE FROM workout_session")
    suspend fun deleteAll()
}
