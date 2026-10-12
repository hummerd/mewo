package com.mewo.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.mewo.domain.LearningSchedule
import com.mewo.domain.LearningWindow
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "schedule-test-${UUID.randomUUID()}"
    private val scheduler = ReminderScheduler(context, name)
    private val workManager = WorkManager.getInstance(context)
    private val settings = ReminderSettings(intervalMinutes = 15)

    @After fun tearDown() =
        runBlocking {
            workManager.cancelUniqueWork(name).await()
            Unit
        }

    @Test fun nextCheckAndScheduleEditsUseAllowedWindowsWithoutDuplicatingWork() =
        runBlocking {
            scheduler.apply(settings)
            val first = workManager.getWorkInfosForUniqueWork(name).get().single()
            assertTrue(settings.schedule.allows(first.nextScheduleTimeMillis))
            val updated =
                settings.copy(
                    schedule =
                        LearningSchedule(
                            LearningWindow(DayOfWeek.entries.toSet(), 0, 1),
                            LearningWindow(emptySet(), 0, 1440),
                        ),
                )
            val before = System.currentTimeMillis() + updated.intervalMinutes * 60_000L
            scheduler.apply(updated)
            val after = System.currentTimeMillis() + updated.intervalMinutes * 60_000L
            val next = workManager.getWorkInfosForUniqueWork(name).get().single()
            assertEquals(first.id, next.id)
            assertTrue(updated.schedule.allows(next.nextScheduleTimeMillis))
            assertTrue(next.nextScheduleTimeMillis >= updated.schedule.nextAllowedAt(before))
            assertTrue(next.nextScheduleTimeMillis <= updated.schedule.nextAllowedAt(after))
        }

    @Test fun pausingCancelsTheScheduledWork() =
        runBlocking {
            scheduler.apply(settings)
            scheduler.apply(settings.copy(enabled = false))
            assertEquals(
                WorkInfo.State.CANCELLED,
                workManager
                    .getWorkInfosForUniqueWork(name)
                    .get()
                    .single()
                    .state,
            )
        }
}
