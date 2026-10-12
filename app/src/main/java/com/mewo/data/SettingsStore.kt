package com.mewo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mewo.domain.LearningSchedule
import com.mewo.domain.LearningWindow
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "reminder_settings")

class SettingsStore internal constructor(
    private val store: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    private val rotation = intPreferencesKey("rotation_size")
    private val interval = intPreferencesKey("interval_minutes")
    private val batch = intPreferencesKey("words_per_notification")
    private val enabled = booleanPreferencesKey("enabled")

    val settings: Flow<ReminderSettings> =
        store.data.map { preferences ->
            ReminderSettings(
                rotationSize = preferences[rotation] ?: 10,
                intervalMinutes = preferences[interval] ?: 60,
                wordsPerNotification = preferences[batch] ?: 1,
                enabled = preferences[enabled] ?: true,
                schedule =
                    LearningSchedule(
                        workingDays = readWindow(preferences, "working", LearningSchedule().workingDays),
                        weekend = readWindow(preferences, "weekend", LearningSchedule().weekend),
                    ),
            )
        }

    suspend fun save(
        settings: ReminderSettings,
        dictionarySize: Int,
    ) {
        settings.validate(dictionarySize)
        store.edit {
            it[rotation] = settings.rotationSize
            it[interval] = settings.intervalMinutes
            it[batch] = settings.wordsPerNotification
            it[enabled] = settings.enabled
            writeWindow(it, "working", settings.schedule.workingDays)
            writeWindow(it, "weekend", settings.schedule.weekend)
        }
    }

    private fun readWindow(
        preferences: Preferences,
        name: String,
        default: LearningWindow,
    ): LearningWindow =
        LearningWindow(
            days = LearningWindow.daysFromMask(preferences[intPreferencesKey("${name}_days")] ?: default.dayMask),
            startMinute = preferences[intPreferencesKey("${name}_start")] ?: default.startMinute,
            endMinute = preferences[intPreferencesKey("${name}_end")] ?: default.endMinute,
        )

    private fun writeWindow(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        name: String,
        window: LearningWindow,
    ) {
        preferences[intPreferencesKey("${name}_days")] = window.dayMask
        preferences[intPreferencesKey("${name}_start")] = window.startMinute
        preferences[intPreferencesKey("${name}_end")] = window.endMinute
    }
}
