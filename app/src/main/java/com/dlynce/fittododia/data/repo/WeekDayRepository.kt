package com.dlynce.fittododia.data.repo

import com.dlynce.fittododia.data.db.dao.WeekDayDao
import com.dlynce.fittododia.data.db.entities.WeekDayEntity
import kotlinx.coroutines.flow.Flow
import com.dlynce.fittododia.data.db.models.DayWithWorkout

class WeekDayRepository(
    private val weekDayDao: WeekDayDao
) {
    fun observeWeekDays(): Flow<List<WeekDayEntity>> = weekDayDao.observeWeekDays()
    fun observeDaysWithWorkout(): Flow<List<DayWithWorkout>> = weekDayDao.observeDaysWithWorkout()
}
