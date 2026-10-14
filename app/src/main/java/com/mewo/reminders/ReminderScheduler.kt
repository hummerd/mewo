package com.mewo.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.mewo.domain.ReminderSettings
import java.util.concurrent.TimeUnit

class ReminderScheduler(
    context: Context,
    private val workName: String = "word-reminders",
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun apply(
        settings: ReminderSettings,
        reschedule: Boolean = true,
    ) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(workName).await()
            return
        }
        val request =
            PeriodicWorkRequestBuilder<ReminderWorker>(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setInitialDelay(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setNextScheduleTimeOverride(
                    settings.schedule.nextAllowedAt(System.currentTimeMillis() + settings.intervalMinutes * 60_000L),
                ).build()
        workManager
            .enqueueUniquePeriodicWork(
                workName,
                if (reschedule) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            ).await()
    }
}
