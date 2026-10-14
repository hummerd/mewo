package com.mewo.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Values validated both at the UI boundary and before background execution. */
data class ReminderSettings(
    val rotationSize: Int = 10,
    val intervalMinutes: Int = 60,
    val wordsPerNotification: Int = 1,
    val enabled: Boolean = true,
    val schedule: LearningSchedule = LearningSchedule(),
) {
    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_INTERVAL_MINUTES = 1440
    }

    fun validate(dictionarySize: Int) {
        schedule.validate()
        require(rotationSize in 1..dictionarySize) { "Rotation must be between 1 and $dictionarySize." }
        require(intervalMinutes in MIN_INTERVAL_MINUTES..MAX_INTERVAL_MINUTES) {
            "Interval must be between 15 and 1440 minutes."
        }
        require(
            wordsPerNotification in 1..rotationSize,
        ) { "Words per notification must be between 1 and $rotationSize." }
    }
}

data class WordProgress(
    val id: String,
    val position: Int,
    val shownCount: Int = 0,
    val introduced: Boolean = false,
    val nextDueAt: Long = 0,
    val priority: Int = WordPriority.COMMON,
    val firstShownAt: Long? = null,
)

object WordPriority {
    const val COMMON = 100
    const val EVERYDAY = 300
    const val EXTENDED = 1000
    val values = setOf(COMMON, EVERYDAY, EXTENDED)
}

data class ReminderPlan(
    val progress: List<WordProgress>,
    val selected: List<WordProgress>,
)

enum class LearningStatus { NEW, LEARNING, PAUSED, REVIEW }

/** Pure scheduling policy. All times are milliseconds since the Unix epoch. */
object ReminderEngine {
    const val LEARNING_DAYS = 5
    private const val FRESH_WEIGHT = 16
    private const val REVIEW_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private val dictionaryOrder = compareBy<WordProgress> { it.priority }.thenBy { it.position }

    private fun age(
        word: WordProgress,
        now: Long,
        zone: ZoneId,
    ): Int =
        word.firstShownAt?.let {
            ChronoUnit.DAYS
                .between(
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate(),
                    Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
                ).coerceIn(0, LEARNING_DAYS.toLong())
                .toInt()
        } ?: 0

    private fun weight(
        word: WordProgress,
        now: Long,
        zone: ZoneId,
    ): Int = FRESH_WEIGHT shr age(word, now, zone).coerceAtMost(LEARNING_DAYS - 1)

    /** N fresh-word equivalents; older words free capacity as their frequency halves each day. */
    fun activeIds(
        words: List<WordProgress>,
        rotationSize: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Set<String> {
        var remaining = rotationSize * FRESH_WEIGHT
        return words
            .filter { it.introduced && age(it, now, zone) < LEARNING_DAYS }
            .sortedWith(dictionaryOrder)
            .filter {
                val cost = weight(it, now, zone)
                (cost <= remaining).also { fits -> if (fits) remaining -= cost }
            }.map { it.id }
            .toSet()
    }

    fun status(
        word: WordProgress,
        activeIds: Set<String>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LearningStatus =
        when {
            !word.introduced -> LearningStatus.NEW
            age(word, now, zone) >= LEARNING_DAYS -> LearningStatus.REVIEW
            word.id in activeIds -> LearningStatus.LEARNING
            else -> LearningStatus.PAUSED
        }

    fun plan(
        words: List<WordProgress>,
        settings: ReminderSettings,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReminderPlan {
        settings.validate(words.size)
        if (!settings.enabled || !settings.schedule.allows(now, zone)) return ReminderPlan(words, emptyList())
        val active = activeIds(words, settings.rotationSize, now, zone)
        val used = words.filter { it.id in active }.sumOf { weight(it, now, zone) }
        val missing = (settings.rotationSize * FRESH_WEIGHT - used) / FRESH_WEIGHT
        val newIds =
            words
                .filter { !it.introduced }
                .sortedWith(dictionaryOrder)
                .take(missing)
                .map { it.id }
                .toSet()
        val progress =
            words.map {
                if (it.id in newIds) it.copy(introduced = true, nextDueAt = now) else it
            }
        val eligibleIds = activeIds(progress, settings.rotationSize, now, zone)
        val eligible =
            progress
                .filter {
                    it.id in eligibleIds || (it.introduced && age(it, now, zone) >= LEARNING_DAYS)
                }.sortedWith(compareBy<WordProgress> { it.nextDueAt }.then(dictionaryOrder))
        // Due words lead, but every allowed check has a full, distinct-word reminder even in a quiet rotation.
        return ReminderPlan(progress, eligible.take(settings.wordsPerNotification))
    }

    fun recordShown(
        word: WordProgress,
        settings: ReminderSettings,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WordProgress {
        require(word.introduced)
        val started = word.copy(firstShownAt = word.firstShownAt ?: now)
        val count = if (word.shownCount == Int.MAX_VALUE) Int.MAX_VALUE else word.shownCount + 1
        val interval =
            if (age(started, now, zone) >= LEARNING_DAYS) {
                REVIEW_INTERVAL_MS
            } else {
                settings.intervalMinutes * 60_000L * settings.rotationSize / settings.wordsPerNotification *
                    (1L shl age(started, now, zone))
            }
        return started.copy(shownCount = count, nextDueAt = now + interval)
    }
}
