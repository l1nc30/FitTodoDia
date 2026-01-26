package com.dlynce.fittododia.data.repo

import com.dlynce.fittododia.data.db.dao.SettingsDao
import com.dlynce.fittododia.data.db.entities.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dao: SettingsDao
) {
    fun observeSettings(): Flow<SettingsEntity> =
        dao.observeSettings()
            .map { it ?: SettingsEntity(id = 1, adsRemoved = false, reminderEnabled = false) }

    suspend fun setReminderEnabled(enabled: Boolean) {
        val current = dao.getSettings() ?: SettingsEntity(id = 1, adsRemoved = false, reminderEnabled = false)
        dao.upsert(current.copy(reminderEnabled = enabled))
    }
}
