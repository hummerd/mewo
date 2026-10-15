package com.mewo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mewo.domain.LearningSchedule
import com.mewo.domain.LearningWindow
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WordRepositoryTest {
    private lateinit var database: WordDatabase
    private lateinit var repository: WordRepository
    private val settings =
        ReminderSettings(
            rotationSize = 2,
            intervalMinutes = 15,
            wordsPerNotification = 2,
            schedule =
                LearningSchedule(
                    LearningWindow(DayOfWeek.entries.toSet(), 0, 1440),
                    LearningWindow(emptySet(), 0, 1440),
                ),
        )

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WordDatabase::class.java).build()
        repository = WordRepository(context, database)
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun knownWordsStayExcludedAfterReimportAndResetReturnsThemToLearning() =
        runBlocking {
            repository.initialize()
            assertTrue(repository.remind(settings, 100) { true })
            val original = database.words().getAll().first()
            repository.markKnown(original.id)
            repository.initialize()
            val known = database.words().getAll().first()
            assertEquals(original.copy(known = true), known)
            assertTrue(
                repository.remind(settings, 200) { selected ->
                    assertTrue(selected.none { it.id == original.id })
                    true
                },
            )
            repository.resetProgress(original.id)
            repository.initialize()
            val reset = database.words().getAll().first()
            assertEquals(original.copy(shownCount = 0, nextDueAt = 0, firstShownAt = null), reset)
            assertTrue(
                repository.remind(settings, 300) { selected ->
                    assertTrue(selected.any { it.id == original.id })
                    true
                },
            )
            val shown = database.words().getAll().first()
            assertEquals(1, shown.shownCount)
            assertEquals(300L, shown.firstShownAt)
            assertFalse(shown.known)
        }

    @Test fun resettingReviewWordClearsItsAgeAndDelayWithoutChangingOtherWords() =
        runBlocking {
            repository.initialize()
            val before = database.words().getAll()
            val review =
                before.first().copy(
                    shownCount = 40,
                    introduced = true,
                    firstShownAt = 1,
                    nextDueAt = Long.MAX_VALUE,
                )
            database.words().update(listOf(review))
            repository.resetProgress(review.id)
            val after = database.words().getAll()
            assertEquals(before.drop(1), after.drop(1))
            assertEquals(before.first().copy(introduced = true), after.first())
            assertTrue(
                repository.remind(settings, 1_000_000_000) { selected ->
                    assertTrue(selected.any { it.id == review.id })
                    true
                },
            )
            assertEquals(
                1_000_000_000L,
                database
                    .words()
                    .getAll()
                    .first()
                    .firstShownAt,
            )
        }

    @Test fun unsuccessfulPostLeavesAllProgressUntouched() =
        runBlocking {
            repository.initialize()
            val before = database.words().getAll()
            assertFalse(repository.remind(settings, 100) { false })
            assertEquals(before, database.words().getAll())
        }

    @Test fun successfulPostPersistsCountsAndReimportPreservesProgress() =
        runBlocking {
            repository.initialize()
            val selected = mutableListOf<WordEntity>()
            assertTrue(
                repository.remind(settings, 100) {
                    selected.addAll(it)
                    true
                },
            )
            assertEquals(2, selected.size)
            val saved = database.words().getAll()
            assertTrue(saved.take(2).all { it.shownCount == 1 && it.nextDueAt == 900_100L })
            assertTrue(saved.drop(2).all { it.shownCount == 0 && !it.introduced })
            repository.initialize()
            assertEquals(saved, database.words().getAll())
            assertTrue(repository.remind(settings, 101) { true })
            val repeated = database.words().getAll()
            assertTrue(repeated.take(2).all { it.shownCount == 2 && it.firstShownAt == 100L })
        }

    @Test fun pausedRemindersNeverPublishOrMutateProgress() =
        runBlocking {
            repository.initialize()
            val before = database.words().getAll()
            assertFalse(
                repository.remind(settings.copy(enabled = false), 100) {
                    fail("Reminders are paused")
                    true
                },
            )
            assertEquals(before, database.words().getAll())
        }

    @Test fun outsideLearningScheduleNeverPublishesOrMutatesProgress() =
        runBlocking {
            repository.initialize()
            val before = database.words().getAll()
            val quiet =
                settings.copy(
                    schedule =
                        LearningSchedule(
                            LearningWindow(DayOfWeek.entries.toSet(), 8 * 60, 9 * 60),
                            LearningWindow(emptySet(), 0, 1440),
                        ),
                )
            val now =
                java.time.ZonedDateTime
                    .now()
                    .withHour(20)
                    .withMinute(0)
                    .toInstant()
                    .toEpochMilli()
            assertFalse(
                repository.remind(quiet, now) {
                    fail("Outside the learning schedule")
                    true
                },
            )
            assertEquals(before, database.words().getAll())
        }

    @Test fun publisherExceptionRollsBackAllProgress() =
        runBlocking {
            repository.initialize()
            val before = database.words().getAll()
            try {
                repository.remind(settings, 100) { throw IllegalStateException("Post failed") }
                fail("Expected publisher failure")
            } catch (_: IllegalStateException) {
                // Worker retries without advancing words.
            }
            assertEquals(before, database.words().getAll())
        }

    @Test fun progressSurvivesClosingAndReopeningDatabase() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "mewo-test-${UUID.randomUUID()}.db"
            var persistent = Room.databaseBuilder(context, WordDatabase::class.java, name).build()
            try {
                val first = WordRepository(context, persistent)
                first.initialize()
                assertTrue(first.remind(settings, 100) { true })
                val saved = persistent.words().getAll()
                persistent.close()
                persistent = Room.databaseBuilder(context, WordDatabase::class.java, name).build()
                WordRepository(context, persistent).initialize()
                assertEquals(saved, persistent.words().getAll())
            } finally {
                persistent.close()
                context.deleteDatabase(name)
            }
        }
}
