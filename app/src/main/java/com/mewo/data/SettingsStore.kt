package com.mewo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "reminder_settings")

class SettingsStore(
    context: Context,
) {
    private val store = context.applicationContext.settingsDataStore
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
        }
    }
}
