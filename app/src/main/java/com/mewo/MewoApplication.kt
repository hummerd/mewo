package com.mewo

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.mewo.data.*
import com.mewo.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy

class MewoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, WordDatabase::class.java, "mewo.db").build() }
    val repository by lazy { WordRepository(this, database) }
    val settings by lazy { SettingsStore(this) }
    val notifications by lazy { ReminderNotifications(this) }
    val scheduler by lazy { ReminderScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        applicationScope.launch {
            try {
                settings.settings.distinctUntilChangedBy { it.enabled to it.intervalMinutes }
                    .collect { scheduler.apply(it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("MEWO", "Could not schedule reminders", error)
            }
        }
    }
}
