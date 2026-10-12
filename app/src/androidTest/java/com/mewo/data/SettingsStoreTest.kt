package com.mewo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mewo.domain.LearningSchedule
import com.mewo.domain.LearningWindow
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        openStore()
    }

    private fun openStore() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        store = SettingsStore(dataStore)
    }

    @After fun tearDown() =
        runBlocking {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
            Unit
        }

    @Test fun newAndLegacySettingsUseTheChosenDefaultSchedule() =
        runBlocking {
            assertEquals(ReminderSettings(), store.settings.first())
            dataStore.edit {
                it[intPreferencesKey("rotation_size")] = 7
                it[intPreferencesKey("interval_minutes")] = 45
                it[intPreferencesKey("words_per_notification")] = 3
                it[booleanPreferencesKey("enabled")] = false
            }
            assertEquals(ReminderSettings(7, 45, 3, false), store.settings.first())
        }

    @Test fun scheduleAndOtherSettingsSurviveReopening() =
        runBlocking {
            val settings =
                ReminderSettings(
                    12,
                    30,
                    4,
                    true,
                    LearningSchedule(
                        LearningWindow(setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY), 22 * 60, 6 * 60),
                        LearningWindow(emptySet(), 0, 1440),
                    ),
                )
            store.save(settings, 1000)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            openStore()
            assertEquals(settings, store.settings.first())
        }

    @Test fun invalidScheduleDoesNotPartiallySaveOtherSettings() =
        runBlocking {
            val before = store.settings.first()
            val invalid =
                before.copy(
                    intervalMinutes = 30,
                    schedule = before.schedule.copy(weekend = before.schedule.workingDays),
                )
            try {
                store.save(invalid, 1000)
                fail("Expected invalid schedule rejection")
            } catch (_: IllegalArgumentException) {
                assertEquals(before, store.settings.first())
            }
        }
}
