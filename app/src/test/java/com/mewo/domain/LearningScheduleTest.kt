package com.mewo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class LearningScheduleTest {
    private val utc = ZoneId.of("UTC")
    private val schedule = LearningSchedule()

    private fun at(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun defaultsUseSeparateWorkingAndWeekendHours() {
        assertFalse(schedule.allows(at("2026-10-12T07:59:59Z"), utc))
        assertTrue(schedule.allows(at("2026-10-12T08:00:00Z"), utc))
        assertTrue(schedule.allows(at("2026-10-12T19:59:59Z"), utc))
        assertFalse(schedule.allows(at("2026-10-12T20:00:00Z"), utc))
        assertFalse(schedule.allows(at("2026-10-17T08:59:59Z"), utc))
        assertTrue(schedule.allows(at("2026-10-17T09:00:00Z"), utc))
        assertFalse(schedule.allows(at("2026-10-18T18:00:00Z"), utc))
    }

    @Test fun followsTheProvidedLocalTimezone() {
        val now = at("2026-10-12T05:00:00Z")
        assertFalse(schedule.allows(now, utc))
        assertTrue(schedule.allows(now, ZoneId.of("Asia/Jerusalem")))
    }

    @Test fun unselectedDaysStayQuietAndOneGroupCanBeDisabled() {
        val mondayOnly =
            LearningSchedule(
                LearningWindow(setOf(DayOfWeek.MONDAY), 480, 1200),
                LearningWindow(emptySet(), 540, 1080),
            )
        mondayOnly.validate()
        assertFalse(mondayOnly.allows(at("2026-10-13T10:00:00Z"), utc))
        assertEquals(at("2026-10-19T08:00:00Z"), mondayOnly.nextAllowedAt(at("2026-10-12T20:00:00Z"), utc))
    }

    @Test fun overnightWindowsBelongToTheSelectedStartDay() {
        val overnight =
            LearningSchedule(
                LearningWindow(setOf(DayOfWeek.FRIDAY), 22 * 60, 6 * 60),
                LearningWindow(emptySet(), 0, 1440),
            )
        assertFalse(overnight.allows(at("2026-10-16T05:00:00Z"), utc))
        assertTrue(overnight.allows(at("2026-10-16T22:00:00Z"), utc))
        assertTrue(overnight.allows(at("2026-10-17T05:59:59Z"), utc))
        assertFalse(overnight.allows(at("2026-10-17T06:00:00Z"), utc))
        assertEquals(at("2026-10-16T22:00:00Z"), overnight.nextAllowedAt(at("2026-10-16T21:00:00Z"), utc))
    }

    @Test fun midnightEndDoesNotLeakIntoTheNextDay() {
        val evening =
            LearningSchedule(
                LearningWindow(setOf(DayOfWeek.MONDAY), 22 * 60, 0),
                LearningWindow(emptySet(), 0, 1440),
            )
        assertTrue(evening.allows(at("2026-10-12T23:59:59Z"), utc))
        assertFalse(evening.allows(at("2026-10-13T00:00:00Z"), utc))
    }

    @Test fun allDayIncludesTheLastMinuteButOnlyOnSelectedDays() {
        val allDay =
            LearningSchedule(
                LearningWindow(setOf(DayOfWeek.MONDAY), 0, 1440),
                LearningWindow(emptySet(), 0, 1440),
            )
        assertTrue(allDay.allows(at("2026-10-12T00:00:00Z"), utc))
        assertTrue(allDay.allows(at("2026-10-12T23:59:59Z"), utc))
        assertFalse(allDay.allows(at("2026-10-13T00:00:00Z"), utc))
    }

    @Test fun nextCheckMovesIntoTheNextWindowWithoutChangingAnAllowedTime() {
        val allowed = at("2026-10-12T10:23:45Z")
        assertEquals(allowed, schedule.nextAllowedAt(allowed, utc))
        assertEquals(at("2026-10-17T09:00:00Z"), schedule.nextAllowedAt(at("2026-10-16T20:00:01Z"), utc))
        assertEquals(at("2026-10-19T08:00:00Z"), schedule.nextAllowedAt(at("2026-10-18T18:00:00Z"), utc))
    }

    @Test fun daylightSavingGapSkipsANonexistentWindow() {
        val narrow =
            LearningSchedule(
                LearningWindow(setOf(DayOfWeek.SUNDAY), 2 * 60 + 15, 2 * 60 + 45),
                LearningWindow(emptySet(), 0, 1440),
            )
        val next = narrow.nextAllowedAt(at("2026-03-08T06:59:00Z"), ZoneId.of("America/New_York"))
        assertEquals(at("2026-03-15T06:15:00Z"), next)
    }

    @Test fun daylightSavingOverlapFindsTheSecondOccurrence() {
        val narrow =
            LearningSchedule(
                LearningWindow(setOf(DayOfWeek.SUNDAY), 90, 105),
                LearningWindow(emptySet(), 0, 1440),
            )
        assertEquals(
            at("2026-11-01T06:30:00Z"),
            narrow.nextAllowedAt(at("2026-11-01T05:50:00Z"), ZoneId.of("America/New_York")),
        )
    }

    @Test fun rejectsOverlappingWeekdaysEmptySchedulesAndInvalidTimes() {
        val invalid =
            listOf(
                schedule.copy(weekend = schedule.weekend.copy(days = setOf(DayOfWeek.MONDAY))),
                LearningSchedule(LearningWindow(emptySet(), 0, 1440), LearningWindow(emptySet(), 0, 1440)),
                schedule.copy(workingDays = schedule.workingDays.copy(startMinute = -1)),
                schedule.copy(workingDays = schedule.workingDays.copy(startMinute = 1440)),
                schedule.copy(weekend = schedule.weekend.copy(endMinute = 1441)),
                schedule.copy(weekend = schedule.weekend.copy(endMinute = 540)),
            )
        invalid.forEach { value -> assertThrows(IllegalArgumentException::class.java) { value.validate() } }
    }

    @Test fun quietHoursDoNotIntroduceWordsOrAdvanceOverdueProgress() {
        val words = listOf(WordProgress("new", 0), WordProgress("due", 1, 2, true, 1))
        val settings = ReminderSettings(rotationSize = 2, wordsPerNotification = 2)
        val quiet = ReminderEngine.plan(words, settings, at("2026-10-12T20:00:00Z"), utc)
        assertEquals(words, quiet.progress)
        assertTrue(quiet.selected.isEmpty())
        val morning = ReminderEngine.plan(words, settings, at("2026-10-13T08:00:00Z"), utc)
        assertEquals(listOf("due", "new"), morning.selected.map { it.id })
        assertEquals(2, morning.progress[1].shownCount)
        assertEquals(1L, morning.progress[1].nextDueAt)
    }

    @Test fun dayMasksRoundTripIncludingAnEmptyGroup() {
        val days = setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY)
        assertEquals(days, LearningWindow.daysFromMask(LearningWindow(days, 0, 1440).dayMask))
        assertTrue(LearningWindow.daysFromMask(0).isEmpty())
    }
}
