package com.mewo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEngineTest {
    private val settings = ReminderSettings(rotationSize = 2, intervalMinutes = 15, wordsPerNotification = 2)

    private fun dictionary(size: Int = 5) = (0 until size).map { WordProgress("word-$it", it) }

    @Test fun startsWithConfiguredRotationInDictionaryOrder() {
        val plan = ReminderEngine.plan(dictionary(), settings, 100)
        assertEquals(listOf("word-0", "word-1"), plan.selected.map { it.id })
        assertEquals(2, plan.progress.count { it.introduced })
        assertTrue(plan.progress.filter { it.introduced }.all { it.nextDueAt == 100L })
    }

    @Test fun exposureIntervalsDoubleAndCapAtSevenDays() {
        var word = dictionary()[0].copy(introduced = true)
        for (appearance in 1..20) {
            word = ReminderEngine.recordShown(word, settings, 100)
            assertEquals(appearance, word.shownCount)
            val expected = (15 * 60_000L * (1L shl (appearance - 1))).coerceAtMost(7 * 24 * 60 * 60_000L)
            assertEquals(100 + expected, word.nextDueAt)
        }
    }

    @Test fun graduatesWordAndIntroducesReplacement() {
        val first =
            dictionary().mapIndexed { i, word ->
                if (i == 0) {
                    word.copy(introduced = true, shownCount = 3)
                } else if (i == 1) {
                    word.copy(introduced = true)
                } else {
                    word
                }
            }
        val graduate = ReminderEngine.recordShown(first[0], settings, 100)
        val updated = first.toMutableList().also { it[0] = graduate }
        val plan = ReminderEngine.plan(updated, settings, 101)
        assertTrue(plan.progress[2].introduced)
        assertFalse(plan.selected.any { it.id == graduate.id })
        assertEquals(LearningStatus.REVIEW, ReminderEngine.status(graduate, ReminderEngine.activeIds(plan.progress, 2)))
        assertEquals(2, ReminderEngine.activeIds(plan.progress, 2).size)
    }

    @Test fun reviewWordsRemainEligibleAfterDictionaryExhaustion() {
        val words = dictionary(2).map { it.copy(introduced = true, shownCount = 4) }
        val plan = ReminderEngine.plan(words, settings, 100)
        assertEquals(2, plan.selected.size)
        assertEquals(words, plan.progress)
    }

    @Test fun selectsEarliestDueThenDictionaryOrderWithoutDuplicates() {
        val words =
            dictionary().map {
                it.copy(
                    introduced = true,
                    shownCount = 4,
                    nextDueAt =
                        if (it.position ==
                            3
                        ) {
                            5
                        } else {
                            10
                        },
                )
            }
        val plan = ReminderEngine.plan(words.reversed(), settings, 50)
        assertEquals(listOf("word-3", "word-0"), plan.selected.map { it.id })
        assertEquals(
            plan.selected.size,
            plan.selected
                .map { it.id }
                .distinct()
                .size,
        )
    }

    @Test fun skipsNotYetDueWordsAndNeverReturnsEmptyRemindersAsWords() {
        val words = dictionary().map { it.copy(introduced = true, shownCount = 4, nextDueAt = 101) }
        assertTrue(ReminderEngine.plan(words, settings, 100).selected.isEmpty())
    }

    @Test fun delayedRunsScheduleFromActualPostTimeWithoutCatchUp() {
        val overdue = dictionary()[0].copy(introduced = true, shownCount = 2, nextDueAt = 1)
        val updated = ReminderEngine.recordShown(overdue, settings, 1_000_000)
        assertEquals(3, updated.shownCount)
        assertEquals(1_000_000L + 60 * 60_000L, updated.nextDueAt)
    }

    @Test fun shrinkingRotationPausesExcessAndGrowingResumesThemBeforeNewWords() {
        val words =
            dictionary().mapIndexed { i, word ->
                if (i <
                    3
                ) {
                    word.copy(introduced = true, shownCount = 1)
                } else {
                    word
                }
            }
        val small = settings.copy(rotationSize = 1, wordsPerNotification = 1)
        val shrunk = ReminderEngine.plan(words, small, 100)
        assertEquals(listOf("word-0"), shrunk.selected.map { it.id })
        assertEquals(LearningStatus.PAUSED, ReminderEngine.status(words[1], ReminderEngine.activeIds(words, 1)))
        val grown = ReminderEngine.plan(shrunk.progress, settings.copy(rotationSize = 3), 100)
        assertFalse(grown.progress[3].introduced)
        assertEquals(3, ReminderEngine.activeIds(grown.progress, 3).size)
        assertEquals(words, grown.progress)
    }

    @Test fun pausingDoesNotIntroduceOrSelectWords() {
        val words = dictionary()
        val plan = ReminderEngine.plan(words, settings.copy(enabled = false), 100)
        assertEquals(words, plan.progress)
        assertTrue(plan.selected.isEmpty())
    }

    @Test fun settingsChangesPreserveExistingDueTimes() {
        val words =
            dictionary().mapIndexed { i, word ->
                if (i <
                    2
                ) {
                    word.copy(introduced = true, nextDueAt = 1000)
                } else {
                    word
                }
            }
        val plan = ReminderEngine.plan(words, settings.copy(intervalMinutes = 30), 100)
        assertEquals(words, plan.progress)
        assertTrue(plan.selected.isEmpty())
    }

    @Test fun rejectsInvalidSettings() {
        listOf(
            settings.copy(rotationSize = 0),
            settings.copy(rotationSize = 6),
            settings.copy(intervalMinutes = 14),
            settings.copy(intervalMinutes = 1441),
            settings.copy(wordsPerNotification = 0),
            settings.copy(wordsPerNotification = 3),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { ReminderEngine.plan(dictionary(), invalid, 100) }
        }
    }

    @Test fun shownCountSaturatesWithoutOverflow() {
        val word = dictionary()[0].copy(introduced = true, shownCount = Int.MAX_VALUE)
        val updated = ReminderEngine.recordShown(word, settings, 0)
        assertEquals(Int.MAX_VALUE, updated.shownCount)
        assertEquals(7 * 24 * 60 * 60_000L, updated.nextDueAt)
    }

    @Test fun introducesLowerPrioritiesBeforeEarlierPositions() {
        val words =
            listOf(
                WordProgress("extended", 0, priority = WordPriority.EXTENDED),
                WordProgress("everyday", 1, priority = WordPriority.EVERYDAY),
                WordProgress("common-later", 3, priority = WordPriority.COMMON),
                WordProgress("common-first", 2, priority = WordPriority.COMMON),
            )
        val plan = ReminderEngine.plan(words, settings, 100)
        assertEquals(listOf("common-first", "common-later"), plan.selected.map { it.id })
        val reviewed = plan.progress.map { if (it.introduced) it.copy(shownCount = 4, nextDueAt = 200) else it }
        val next = ReminderEngine.plan(reviewed, settings, 100)
        assertEquals(listOf("everyday", "extended"), next.selected.map { it.id })
    }

    @Test fun dueTimePrecedesPriorityAndPriorityBreaksDueTimeTies() {
        val words =
            listOf(
                WordProgress("extended-overdue", 0, 4, true, 1, WordPriority.EXTENDED),
                WordProgress("everyday", 1, 4, true, 10, WordPriority.EVERYDAY),
                WordProgress("common", 2, 4, true, 10, WordPriority.COMMON),
            )
        val plan = ReminderEngine.plan(words, settings, 100)
        assertEquals(listOf("extended-overdue", "common"), plan.selected.map { it.id })
    }

    @Test fun pausedWordsResumeBeforeIntroducingLowerPriorityWords() {
        val words =
            listOf(
                WordProgress("existing", 0, 1, true, 100, WordPriority.EXTENDED),
                WordProgress("new", 1, priority = WordPriority.COMMON),
            )
        val plan = ReminderEngine.plan(words, settings.copy(rotationSize = 1, wordsPerNotification = 1), 100)
        assertEquals(listOf("existing"), plan.selected.map { it.id })
        assertFalse(plan.progress[1].introduced)
    }
}
