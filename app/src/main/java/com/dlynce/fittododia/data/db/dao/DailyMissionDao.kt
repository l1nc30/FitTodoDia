package com.dlynce.fittododia.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dlynce.fittododia.data.db.entities.DailyMissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMissionDao {

    @Query("SELECT * FROM daily_mission WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    fun observeByDate(dateEpochDay: Long): Flow<DailyMissionEntity?>

    @Query("SELECT * FROM daily_mission WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getByDate(dateEpochDay: Long): DailyMissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyMissionEntity)
}
