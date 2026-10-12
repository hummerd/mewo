package com.mewo.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Weekdays identify the day a window starts, including windows that cross midnight. */
data class LearningWindow(
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
) {
    val dayMask: Int get() = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun validate() {
        require(startMinute in 0 until MINUTES_PER_DAY) { "Start time must be between 00:00 and 23:59." }
        require(endMinute in 0..MINUTES_PER_DAY) { "End time must be between 00:00 and 24:00." }
        require(startMinute != endMinute) { "Start and end times must differ. Use 00:00–24:00 for all day." }
    }

    fun contains(time: ZonedDateTime): Boolean {
        val minute = time.hour * MINUTES_PER_HOUR + time.minute
        return if (startMinute < endMinute) {
            time.dayOfWeek in days && minute >= startMinute && minute < endMinute
        } else {
            (time.dayOfWeek in days && minute >= startMinute) ||
                (time.dayOfWeek.minus(1) in days && minute < endMinute)
        }
    }

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        fun daysFromMask(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
    }
}

data class LearningSchedule(
    val workingDays: LearningWindow =
        LearningWindow(
            DayOfWeek.entries.filter { it <= DayOfWeek.FRIDAY }.toSet(),
            DEFAULT_WORK_START,
            DEFAULT_WORK_END,
        ),
    val weekend: LearningWindow =
        LearningWindow(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), DEFAULT_WEEKEND_START, DEFAULT_WEEKEND_END),
) {
    fun validate() {
        workingDays.validate()
        weekend.validate()
        require(workingDays.days.intersect(weekend.days).isEmpty()) {
            "Assign each weekday to only one schedule."
        }
        require(workingDays.days.isNotEmpty() || weekend.days.isNotEmpty()) {
            "Choose at least one learning day. Use the reminder switch to pause reminders."
        }
    }

    fun allows(
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val time = Instant.ofEpochMilli(now).atZone(zone)
        return workingDays.contains(time) || weekend.contains(time)
    }

    /** Scan real minutes so skipped/repeated local times and timezone changes are handled consistently. */
    fun nextAllowedAt(
        earliest: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        validate()
        if (allows(earliest, zone)) return earliest
        var candidate = Instant.ofEpochMilli(earliest).truncatedTo(ChronoUnit.MINUTES)
        repeat(SEARCH_MINUTES) {
            candidate = candidate.plus(1, ChronoUnit.MINUTES)
            if (allows(candidate.toEpochMilli(), zone)) return candidate.toEpochMilli()
        }
        error("No learning window found in the next eight days.")
    }

    companion object {
        private const val DEFAULT_WORK_START = 8 * LearningWindow.MINUTES_PER_HOUR
        private const val DEFAULT_WORK_END = 20 * LearningWindow.MINUTES_PER_HOUR
        private const val DEFAULT_WEEKEND_START = 9 * LearningWindow.MINUTES_PER_HOUR
        private const val DEFAULT_WEEKEND_END = 18 * LearningWindow.MINUTES_PER_HOUR
        private const val SEARCH_MINUTES = 8 * LearningWindow.MINUTES_PER_DAY
    }
}
