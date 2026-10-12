package com.mewo.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WordDatabase::class.java)

    @Test fun migrationAndDictionaryUpgradeRetainAndMergeLegacyProgress() =
        runBlocking {
            val name = "dictionary-migration-test"
            helper.createDatabase(name, 1).use { old ->
                old.execSQL("INSERT INTO words VALUES ('khaver',23,'חבר','Friend (male)','khaver',2,1,1234)")
                old.execSQL("INSERT INTO words VALUES ('khavera',24,'חברה','Friend (female)','khavera',3,1,2345)")
                old.execSQL("INSERT INTO words VALUES ('shalom',0,'שלום','Hello / peace','shalom',1,1,3456)")
            }
            helper.runMigrationsAndValidate(name, 2, true).use { migrated ->
                migrated.query("SELECT priority, shownCount, nextDueAt FROM words WHERE id = 'khaver'").use { row ->
                    assertTrue(row.moveToFirst())
                    assertEquals(100, row.getInt(0))
                    assertEquals(2, row.getInt(1))
                    assertEquals(1234L, row.getLong(2))
                }
            }
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = Room.databaseBuilder(context, WordDatabase::class.java, name).build()
            try {
                val repository = WordRepository(context, database)
                repository.initialize()
                val words = database.words().getAll()
                assertEquals(1000, words.size)
                val friend = words.single { it.id == "khaver" }
                assertEquals(5, friend.shownCount)
                assertEquals(1234L, friend.nextDueAt)
                assertTrue(friend.introduced)
                assertEquals("חברה", friend.feminineHebrew)
                assertEquals(3456L, words.single { it.id == "shalom" }.nextDueAt)
                assertTrue(words.none { it.id == "khavera" })
                repository.initialize()
                assertEquals(words, database.words().getAll())
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }
}
