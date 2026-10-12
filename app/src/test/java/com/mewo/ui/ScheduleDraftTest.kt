package com.mewo.ui

import com.mewo.domain.LearningSchedule
import com.mewo.domain.LearningWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek

class ScheduleDraftTest {
    @Test fun defaultHoursAppearInTwentyFourHourFormat() {
        val draft = ScheduleDraft(LearningSchedule())
        assertEquals("08:00", draft.working.start)
        assertEquals("20:00", draft.working.end)
        assertEquals("09:00", draft.weekend.start)
        assertEquals("18:00", draft.weekend.end)
        assertEquals(LearningSchedule(), draft.toSchedule())
    }

    @Test fun acceptsAllDayAndOvernightWindows() {
        val days = LearningWindow(setOf(DayOfWeek.MONDAY), 0, 1440).dayMask
        assertEquals(
            LearningWindow(setOf(DayOfWeek.MONDAY), 0, 1440),
            WindowDraft(days, "00:00", "24:00").toWindow("Work"),
        )
        assertEquals(
            LearningWindow(setOf(DayOfWeek.MONDAY), 1320, 360),
            WindowDraft(days, "22:00", "06:00").toWindow("Work"),
        )
    }

    @Test fun malformedTimesAreRejectedBeforeSaving() {
        listOf("8:00", "08:60", "25:00", "24:01", "noon", "", "08:00:00").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { WindowDraft(1, value, "20:00").toWindow("Work") }
        }
    }

    @Test fun midnightIsAllowedOnlyAsAnEndTimeForAllDay() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) { WindowDraft(1, "24:00", "20:00").toWindow("Work").validate() }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { WindowDraft(1, "08:00", "08:00").toWindow("Work").validate() }
    }
}
