package com.mewo.data

import com.mewo.domain.WordPriority
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FeminineForm(
    val hebrew: String,
    val transliteration: String,
    val modification: String,
)

@Serializable
data class DictionaryWord(
    val id: String,
    val hebrew: String,
    val english: String,
    val transliteration: String,
    val priority: Int,
    val feminine: FeminineForm? = null,
    val mergedIds: List<String> = emptyList(),
) {
    /** Merge legacy gender records once while retaining their appearance history. */
    fun entity(
        position: Int,
        existing: Map<String, WordEntity>,
    ): WordEntity {
        val previous = (listOf(id) + mergedIds).mapNotNull { existing[it] }
        val introduced = previous.filter { it.introduced }
        return WordEntity(
            id = id,
            position = position,
            hebrew = hebrew,
            english = english,
            transliteration = transliteration,
            priority = priority,
            feminineHebrew = feminine?.hebrew,
            feminineTransliteration = feminine?.transliteration,
            feminineModification = feminine?.modification,
            shownCount = previous.sumOf { it.shownCount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            introduced = introduced.isNotEmpty(),
            nextDueAt = introduced.minOfOrNull { it.nextDueAt } ?: 0,
        )
    }
}

object Dictionary {
    fun decode(json: String): List<DictionaryWord> {
        val words = Json.decodeFromString<List<DictionaryWord>>(json)
        val ids = words.map { it.id }
        val aliases = words.flatMap { it.mergedIds }
        require(words.isNotEmpty() && ids.distinct().size == ids.size) { "Dictionary IDs must be unique." }
        require(aliases.distinct().size == aliases.size && aliases.none { it in ids || it.isBlank() }) {
            "Merged IDs must be unique and separate from dictionary IDs."
        }
        require(
            words.all { it.valid() },
        ) { "Dictionary entries must have text, a supported priority, and complete forms." }
        return words
    }

    private fun DictionaryWord.valid(): Boolean =
        listOf(id, hebrew, english, transliteration).all { it.isNotBlank() } &&
            priority in WordPriority.values &&
            feminine?.let { listOf(it.hebrew, it.transliteration, it.modification).all(String::isNotBlank) } != false
}
