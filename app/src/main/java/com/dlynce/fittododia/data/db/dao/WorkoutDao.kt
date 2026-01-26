package com.dlynce.fittododia.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dlynce.fittododia.data.db.entities.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // IMPORTANTE: sempre pegar um único por dia
    @Query("""
        SELECT * FROM workout
        WHERE weekDayId = :dayId
        ORDER BY id DESC
        LIMIT 1
    """)
    suspend fun getWorkoutByDay(dayId: Int): WorkoutEntity?

    @Query("""
        SELECT * FROM workout
        WHERE weekDayId = :dayId
        ORDER BY id DESC
        LIMIT 1
    """)
    fun observeWorkoutByDay(dayId: Int): Flow<WorkoutEntity?>

    // Insert SEM REPLACE (para não disparar delete/cascade)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WorkoutEntity): Long

    // Update real (não apaga filhos)
    @Update
    suspend fun update(entity: WorkoutEntity)

    /**
     * Upsert seguro:
     * - Se id == 0 -> insert e retorna novo id
     * - Se id != 0 -> update e retorna o mesmo id
     */
    suspend fun upsert(entity: WorkoutEntity): Long {
        return if (entity.id == 0L) {
            insert(entity)
        } else {
            update(entity)
            entity.id
        }
    }

    @Query("DELETE FROM workout WHERE weekDayId = :dayId")
    suspend fun deleteByDay(dayId: Int)
}
