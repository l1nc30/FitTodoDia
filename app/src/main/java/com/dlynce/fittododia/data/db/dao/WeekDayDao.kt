package com.dlynce.fittododia.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dlynce.fittododia.data.db.entities.WeekDayEntity
import kotlinx.coroutines.flow.Flow
import com.dlynce.fittododia.data.db.models.DayWithWorkout

@Dao
interface WeekDayDao {

    @Query("SELECT * FROM week_day ORDER BY id ASC")
    fun observeWeekDays(): Flow<List<WeekDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WeekDayEntity>)

    @Query("""
    SELECT 
        d.id AS dayId,
        d.name AS dayName,
        w.id AS workoutId,
        w.name AS workoutName
    FROM week_day d
    LEFT JOIN workout w ON w.weekDayId = d.id
    ORDER BY d.id ASC
""")
    fun observeDaysWithWorkout(): Flow<List<DayWithWorkout>>
}
