package com.mewo.domain

/** Values validated both at the UI boundary and before background execution. */
data class ReminderSettings(
    val rotationSize: Int = 10,
    val intervalMinutes: Int = 60,
    val wordsPerNotification: Int = 1,
    val enabled: Boolean = true,
) {
    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_INTERVAL_MINUTES = 1440
    }

    fun validate(dictionarySize: Int) {
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
    const val REVIEW_THRESHOLD = 4
    private const val MAX_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private val dictionaryOrder = compareBy<WordProgress> { it.priority }.thenBy { it.position }

    fun activeIds(
        words: List<WordProgress>,
        rotationSize: Int,
    ): Set<String> =
        words
            .filter { it.introduced && it.shownCount < REVIEW_THRESHOLD }
            .sortedWith(dictionaryOrder)
            .take(rotationSize)
            .map { it.id }
            .toSet()

    fun status(
        word: WordProgress,
        activeIds: Set<String>,
    ): LearningStatus =
        when {
            !word.introduced -> LearningStatus.NEW
            word.shownCount >= REVIEW_THRESHOLD -> LearningStatus.REVIEW
            word.id in activeIds -> LearningStatus.LEARNING
            else -> LearningStatus.PAUSED
        }

    fun plan(
        words: List<WordProgress>,
        settings: ReminderSettings,
        now: Long,
    ): ReminderPlan {
        settings.validate(words.size)
        if (!settings.enabled) return ReminderPlan(words, emptyList())
        val missing = settings.rotationSize - activeIds(words, settings.rotationSize).size
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
        val active = activeIds(progress, settings.rotationSize)
        val selected =
            progress
                .filter {
                    it.introduced && (it.id in active || it.shownCount >= REVIEW_THRESHOLD) && it.nextDueAt <= now
                }.sortedWith(compareBy<WordProgress> { it.nextDueAt }.then(dictionaryOrder))
                .take(settings.wordsPerNotification)
        return ReminderPlan(progress, selected)
    }

    fun recordShown(
        word: WordProgress,
        settings: ReminderSettings,
        now: Long,
    ): WordProgress {
        require(word.introduced)
        val count = if (word.shownCount == Int.MAX_VALUE) Int.MAX_VALUE else word.shownCount + 1
        val multiplier = 1L shl (count - 1).coerceIn(0, 20)
        val interval = (settings.intervalMinutes * 60_000L * multiplier).coerceAtMost(MAX_INTERVAL_MS)
        return word.copy(shownCount = count, nextDueAt = now + interval)
    }
}
