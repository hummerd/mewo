package com.mewo.reminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mewo.MewoApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MewoApplication
        return try {
            val settings = app.settings.settings.first()
            if (!settings.enabled || !app.notifications.allowed()) return Result.success()
            app.repository.initialize()
            app.repository.remind(settings, System.currentTimeMillis(), app.notifications::publish)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("MEWO", "Reminder failed", error)
            Result.retry()
        }
    }
}
