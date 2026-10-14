package com.mewo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class ReminderEngineTest {
    private val utc = ZoneId.of("UTC")
    private val start = Instant.parse("2026-10-12T08:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60_000L
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

    private fun dictionary(size: Int = 20) = (0 until size).map { WordProgress("word-$it", it) }

    private fun plan(
        words: List<WordProgress>,
        now: Long = start,
    ) = ReminderEngine.plan(words, settings, now, utc)

    @Test fun startsWithConfiguredRotationInDictionaryOrder() {
        val plan = plan(dictionary())
        assertEquals(listOf("word-0", "word-1"), plan.selected.map { it.id })
        assertEquals(2, plan.progress.count { it.introduced })
        assertTrue(plan.progress.all { it.firstShownAt == null })
    }

    @Test fun frequencyFadesByLocalDayRatherThanAppearanceCount() {
        var word = dictionary()[0].copy(introduced = true)
        repeat(20) { word = ReminderEngine.recordShown(word, settings, start, utc) }
        assertEquals(start, word.firstShownAt)
        assertEquals(start + 15 * 60_000L, word.nextDueAt)
        for (age in 1..4) {
            val now = start + age * day
            word = ReminderEngine.recordShown(word, settings, now, utc)
            assertEquals(now + 15 * 60_000L * (1L shl age), word.nextDueAt)
        }
    }

    @Test fun midnightAndTimezoneDetermineLearningDay() {
        val first = Instant.parse("2026-10-12T23:59:00Z").toEpochMilli()
        val word = dictionary()[0].copy(introduced = true, firstShownAt = first)
        val now = first + 2 * 60_000L
        assertEquals(now + 30 * 60_000L, ReminderEngine.recordShown(word, settings, now, utc).nextDueAt)
        assertEquals(
            now + 15 * 60_000L,
            ReminderEngine.recordShown(word, settings, now, ZoneId.of("Asia/Jerusalem")).nextDueAt,
        )
    }

    @Test fun fadingFreesCapacityForNewWordsBeforeGraduation() {
        val initial =
            plan(dictionary()).progress.map {
                if (it.introduced) ReminderEngine.recordShown(it, settings, start, utc) else it
            }
        val next = plan(initial, start + day)
        assertEquals(3, next.progress.count { it.introduced })
        assertTrue(next.progress[2].introduced)
        assertEquals(3, ReminderEngine.activeIds(next.progress, 2, start + day, utc).size)
    }

    @Test fun fiveDaysGraduateIndependentlyOfCountAndKeepReviewing() {
        val words =
            dictionary().mapIndexed { i, word ->
                if (i < 2) word.copy(introduced = true, shownCount = 1, firstShownAt = start) else word
            }
        val before = ReminderEngine.activeIds(words, 2, start + 4 * day, utc)
        assertEquals(LearningStatus.LEARNING, ReminderEngine.status(words[0], before, start + 4 * day, utc))
        val now = start + 5 * day
        val next = plan(words, now)
        assertEquals(LearningStatus.REVIEW, ReminderEngine.status(words[0], emptySet(), now, utc))
        assertTrue(next.progress[2].introduced)
        assertEquals(listOf("word-0", "word-1"), next.selected.map { it.id })
        val reviewed = ReminderEngine.recordShown(next.selected[0], settings, now, utc)
        assertEquals(now + 7 * day, reviewed.nextDueAt)
    }

    @Test fun everyAllowedCheckPostsEvenWhenAllDueTimesAreInTheFuture() {
        val words =
            dictionary(2).map {
                it.copy(introduced = true, firstShownAt = start, nextDueAt = start + day)
            }
        val next = plan(words)
        assertEquals(words, next.progress)
        assertEquals(2, next.selected.size)
        assertEquals(
            2,
            next.selected
                .map { it.id }
                .distinct()
                .size,
        )
    }

    @Test fun reviewOnlyDictionaryContinuesWithoutEmptyChecks() {
        val words =
            dictionary(2).map {
                it.copy(introduced = true, firstShownAt = start - 5 * day, nextDueAt = start + day)
            }
        assertEquals(words, plan(words).selected)
    }

    @Test fun overdueReviewIsNotStarvedByFreshWords() {
        val words =
            dictionary().mapIndexed { i, word ->
                if (i == 0) word.copy(introduced = true, firstShownAt = start - 5 * day, nextDueAt = 1) else word
            }
        assertEquals("word-0", plan(words).selected.first().id)
    }

    @Test fun reducingCapacityPausesAndIncreasingResumesBeforeUnseenWords() {
        val words =
            dictionary().mapIndexed { i, word ->
                if (i < 3) word.copy(introduced = true, firstShownAt = start, shownCount = 1) else word
            }
        val smaller = settings.copy(rotationSize = 1, wordsPerNotification = 1)
        val small = ReminderEngine.plan(words, smaller, start, utc)
        assertEquals(listOf("word-0"), small.selected.map { it.id })
        assertEquals(LearningStatus.PAUSED, ReminderEngine.status(words[1], setOf("word-0"), start, utc))
        val grown = ReminderEngine.plan(words, settings.copy(rotationSize = 3), start, utc)
        assertFalse(grown.progress[3].introduced)
    }

    @Test fun priorityControlsIntroductionAndBreaksDueTimeTies() {
        val words =
            listOf(
                WordProgress("extended", 0, priority = WordPriority.EXTENDED),
                WordProgress("everyday", 1, priority = WordPriority.EVERYDAY),
                WordProgress("common-later", 3),
                WordProgress("common-first", 2),
            )
        assertEquals(listOf("common-first", "common-later"), plan(words).selected.map { it.id })
    }

    @Test fun dueTimePrecedesDictionaryPriority() {
        val words =
            listOf(
                WordProgress("extended", 0, 1, true, 1, WordPriority.EXTENDED, start - 5 * day),
                WordProgress("common", 1, 1, true, 2, WordPriority.COMMON, start - 5 * day),
            )
        assertEquals(listOf("extended", "common"), plan(words).selected.map { it.id })
    }

    @Test fun clockMovingBackwardsDoesNotGraduateOrOverflow() {
        val word = dictionary()[0].copy(introduced = true, firstShownAt = start + day, shownCount = Int.MAX_VALUE)
        val shown = ReminderEngine.recordShown(word, settings, start, utc)
        assertEquals(Int.MAX_VALUE, shown.shownCount)
        assertEquals(start + 15 * 60_000L, shown.nextDueAt)
    }

    @Test fun pausingDoesNotIntroduceOrSelectWords() {
        val words = dictionary()
        val plan = ReminderEngine.plan(words, settings.copy(enabled = false), start, utc)
        assertEquals(words, plan.progress)
        assertTrue(plan.selected.isEmpty())
    }

    @Test fun legacyAppearanceCountsDoNotImmediatelyGraduateLearningWords() {
        val word = dictionary()[0].copy(introduced = true, shownCount = 8, nextDueAt = start + 3 * day)
        val shown = ReminderEngine.recordShown(word, settings, start, utc)
        assertEquals(start, shown.firstShownAt)
        assertEquals(start + 15 * 60_000L, shown.nextDueAt)
    }

    @Test fun scheduledSimulationFadesOldWordsAndNeverLeavesAGap() {
        var words = dictionary(200)
        val appearances = MutableList(5) { 0 }
        var reviewAppearances = 0
        for (dayIndex in 0..9) {
            repeat(96) { check ->
                val now = start + dayIndex * day + check * 15 * 60_000L
                val next = plan(words, now)
                assertEquals(2, next.selected.size)
                reviewAppearances +=
                    next.selected.count {
                        ReminderEngine.status(it, emptySet(), now, utc) == LearningStatus.REVIEW
                    }
                val ids = next.selected.map { it.id }.toSet()
                if (dayIndex < 5) appearances[dayIndex] += ids.count { it in setOf("word-0", "word-1") }
                words =
                    next.progress.map {
                        if (it.id in ids) ReminderEngine.recordShown(it, settings, now, utc) else it
                    }
            }
        }
        appearances.zipWithNext().forEach { (earlier, later) -> assertTrue(later < earlier) }
        assertTrue(words.count { it.introduced } > settings.rotationSize)
        assertTrue(reviewAppearances > 0)
    }

    @Test fun rejectsInvalidSettings() {
        listOf(
            settings.copy(rotationSize = 0),
            settings.copy(rotationSize = 21),
            settings.copy(intervalMinutes = 14),
            settings.copy(intervalMinutes = 1441),
            settings.copy(wordsPerNotification = 0),
            settings.copy(wordsPerNotification = 3),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                ReminderEngine.plan(dictionary(), invalid, start, utc)
            }
        }
    }
}
