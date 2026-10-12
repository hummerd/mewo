package com.mewo.reminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mewo.MewoApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    // Retry failures at the worker boundary without advancing progress; cancellation is rethrown.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val app = applicationContext as MewoApplication
        return try {
            val settings = app.settings.settings.first()
            if (!settings.enabled) return Result.success()
            if (settings.schedule.allows(System.currentTimeMillis()) && app.notifications.allowed()) {
                app.repository.initialize()
                app.repository.remind(settings, System.currentTimeMillis()) { words ->
                    settings.schedule.allows(System.currentTimeMillis()) && app.notifications.publish(words)
                }
            }
            // Read again so an edit made during this run is respected by the next request.
            app.scheduler.apply(app.settings.settings.first())
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("MEWO", "Reminder failed", error)
            Result.retry()
        }
    }
}
