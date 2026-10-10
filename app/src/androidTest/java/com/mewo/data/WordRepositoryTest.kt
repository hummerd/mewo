package com.mewo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WordRepositoryTest {
    private lateinit var database: WordDatabase
    private lateinit var repository: WordRepository
    private val settings = ReminderSettings(rotationSize = 2, intervalMinutes = 15, wordsPerNotification = 2)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WordDatabase::class.java).build()
        repository = WordRepository(context, database)
    }

    @After fun tearDown() { database.close() }

    @Test fun unsuccessfulPostLeavesAllProgressUntouched() = runBlocking {
        repository.initialize()
        val before = database.words().getAll()
        assertFalse(repository.remind(settings, 100) { false })
        assertEquals(before, database.words().getAll())
    }

    @Test fun successfulPostPersistsCountsAndReimportPreservesProgress() = runBlocking {
        repository.initialize()
        val selected = mutableListOf<WordEntity>()
        assertTrue(repository.remind(settings, 100) { selected.addAll(it); true })
        assertEquals(2, selected.size)
        val saved = database.words().getAll()
        assertTrue(saved.take(2).all { it.shownCount == 1 && it.nextDueAt == 900_100L })
        assertTrue(saved.drop(2).all { it.shownCount == 0 && !it.introduced })
        repository.initialize()
        assertEquals(saved, database.words().getAll())
        assertFalse(repository.remind(settings, 101) { fail("No words are due"); true })
    }

    @Test fun pausedRemindersNeverPublishOrMutateProgress() = runBlocking {
        repository.initialize()
        val before = database.words().getAll()
        assertFalse(repository.remind(settings.copy(enabled = false), 100) { fail("Reminders are paused"); true })
        assertEquals(before, database.words().getAll())
    }

    @Test fun publisherExceptionRollsBackAllProgress() = runBlocking {
        repository.initialize()
        val before = database.words().getAll()
        try {
            repository.remind(settings, 100) { throw IllegalStateException("Post failed") }
            fail("Expected publisher failure")
        } catch (_: IllegalStateException) { /* Worker retries without advancing words. */ }
        assertEquals(before, database.words().getAll())
    }

    @Test fun progressSurvivesClosingAndReopeningDatabase() = runBlocking {
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
