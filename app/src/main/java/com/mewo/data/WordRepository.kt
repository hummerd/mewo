package com.mewo.data

import android.content.Context
import androidx.room.withTransaction
import com.mewo.domain.ReminderEngine
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordRepository(
    private val context: Context,
    private val database: WordDatabase,
) {
    val words = database.words().observe()

    suspend fun initialize() =
        withContext(Dispatchers.IO) {
            val json =
                context.assets
                    .open("hebrew.json")
                    .bufferedReader()
                    .use { it.readText() }
            val dictionary = Dictionary.decode(json)
            database.withTransaction {
                val existing = database.words().getAll().associateBy { it.id }
                val entities = dictionary.mapIndexed { index, word -> word.entity(index, existing) }
                database.words().insert(entities)
                database.words().update(entities)
                database.words().deleteMerged(dictionary.flatMap { it.mergedIds })
            }
        }

    /** Record appearances only if the notification publisher reports success. */
    suspend fun remind(
        settings: ReminderSettings,
        now: Long,
        publish: (List<WordEntity>) -> Boolean,
    ): Boolean =
        database.withTransaction {
            val entities = database.words().getAll()
            val plan = ReminderEngine.plan(entities.map { it.progress() }, settings, now)
            val byId = entities.associateBy { it.id }
            val selected = plan.selected.map { byId.getValue(it.id).withProgress(it) }
            if (selected.isEmpty() || !publish(selected)) return@withTransaction false
            val shownIds = selected.map { it.id }.toSet()
            val updated =
                plan.progress.map {
                    val progress = if (it.id in shownIds) ReminderEngine.recordShown(it, settings, now) else it
                    byId.getValue(it.id).withProgress(progress)
                }
            database.words().update(updated.filter { it != byId.getValue(it.id) })
            true
        }
}
