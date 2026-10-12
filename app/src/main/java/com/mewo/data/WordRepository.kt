package com.mewo.data

import android.content.Context
import androidx.room.withTransaction
import com.mewo.domain.ReminderEngine
import com.mewo.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class DictionaryWord(
    val id: String,
    val hebrew: String,
    val english: String,
    val transliteration: String,
)

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
            val dictionary = Json.decodeFromString<List<DictionaryWord>>(json)
            require(dictionary.isNotEmpty() && dictionary.map { it.id }.distinct().size == dictionary.size)
            require(
                dictionary.all {
                    it.id.isNotBlank() &&
                        it.hebrew.isNotBlank() &&
                        it.english.isNotBlank() &&
                        it.transliteration.isNotBlank()
                },
            )
            database.words().insert(
                dictionary.mapIndexed { index, word ->
                    WordEntity(word.id, index, word.hebrew, word.english, word.transliteration)
                },
            )
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
