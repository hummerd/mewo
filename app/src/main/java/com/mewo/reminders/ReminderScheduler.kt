package com.mewo.reminders

import android.content.Context
import androidx.work.*
import com.mewo.domain.ReminderSettings
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)
    fun apply(settings: ReminderSettings) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork("word-reminders")
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setInitialDelay(settings.intervalMinutes.toLong(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork("word-reminders", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
