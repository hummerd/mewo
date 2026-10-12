package com.mewo.data

import com.mewo.domain.WordPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryTest {
    private val paired =
        DictionaryWord(
            "khaver",
            "חבר",
            "Friend",
            "khaver",
            WordPriority.COMMON,
            FeminineForm("חברה", "khavera", "Add ה"),
            listOf("khavera"),
        )

    @Test fun bundledDictionaryHasExactTiersAndCompleteUniqueRecords() {
        val json = checkNotNull(javaClass.getResourceAsStream("/hebrew.json")).bufferedReader().use { it.readText() }
        val words = Dictionary.decode(json)
        assertEquals(1000, words.size)
        assertEquals(mapOf(100 to 100, 300 to 300, 1000 to 600), words.groupingBy { it.priority }.eachCount())
        assertEquals(words.size, words.map { it.hebrew to it.transliteration }.distinct().size)
        assertEquals(words.map { it.priority }.sorted(), words.map { it.priority })
        assertTrue(words.filter { it.feminine != null }.all { it.feminine?.modification?.isNotBlank() == true })
        assertTrue(words.none { it.id in listOf("khavera", "tova") })
    }

    @Test fun combiningFormsPreservesCountsAndEarliestExistingReview() {
        val male = paired.entity(0, emptyMap()).copy(shownCount = 2, introduced = true, nextDueAt = 100)
        val female = male.copy(id = "khavera", shownCount = 3, nextDueAt = 200)
        val merged = paired.entity(1, mapOf(male.id to male, female.id to female))
        assertEquals(5, merged.shownCount)
        assertEquals(100L, merged.nextDueAt)
        assertTrue(merged.introduced)
        assertEquals("חברה", merged.feminineHebrew)
        assertEquals(merged, paired.entity(1, mapOf(merged.id to merged)))
    }

    @Test fun existingFeminineOnlyProgressTransfersToCanonicalRecord() {
        val female =
            paired
                .entity(
                    0,
                    emptyMap(),
                ).copy(id = "khavera", shownCount = 1, introduced = true, nextDueAt = 200)
        val merged = paired.entity(0, mapOf(female.id to female))
        assertEquals("khaver", merged.id)
        assertEquals(1, merged.shownCount)
        assertEquals(200L, merged.nextDueAt)
    }

    @Test fun newRecordsStartUnintroducedAndMetadataUpdatesPreserveProgress() {
        val initial = paired.entity(0, emptyMap())
        assertFalse(initial.introduced)
        val saved = initial.copy(shownCount = 2, introduced = true, nextDueAt = 300)
        val updated =
            paired
                .copy(priority = WordPriority.EVERYDAY, english = "Companion")
                .entity(5, mapOf(saved.id to saved))
        assertEquals(saved.progress().copy(position = 5, priority = WordPriority.EVERYDAY), updated.progress())
        assertEquals("Companion", updated.english)
    }

    @Test fun rejectsUnsupportedPrioritiesAndOverlappingAliases() {
        val entry = """{"id":"one","hebrew":"אחד","english":"One","transliteration":"ekhad","priority":100}"""
        assertEquals(1, Dictionary.decode("[$entry]").size)
        assertThrows(IllegalArgumentException::class.java) { Dictionary.decode("[${entry.replace("100", "200")}]") }
        assertThrows(IllegalArgumentException::class.java) { Dictionary.decode("[$entry,$entry]") }
        val alias = entry.replace("\"priority\":100", "\"priority\":100,\"mergedIds\":[\"one\"]")
        assertThrows(IllegalArgumentException::class.java) { Dictionary.decode("[$alias]") }
    }
}
