package com.dlynce.fittododia.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val UNIQUE_WORK_NAME = "daily_training_reminder"
    private val zone = ZoneId.of("America/Sao_Paulo")

    /**
     * Agenda 1 notificação por dia no horário definido.
     * Padrão: 18:00
     */
    fun scheduleDaily(context: Context, hour: Int = 18, minute: Int = 0) {
        val delayMs = millisUntilNextTime(hour, minute)

        val request = OneTimeWorkRequestBuilder<TrainingReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun millisUntilNextTime(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(1)
    }
}
