package work.spyglee.ladger.reminder

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {
    private val zagreb = ZoneId.of("Europe/Zagreb")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zagreb)

    @Test
    fun beforeHalfPastEightFiresTheSameEvening() {
        assertEquals(at(2026, 10, 7, 20, 30), ReminderSchedule.nextTrigger(at(2026, 10, 7, 9, 0)))
    }

    @Test
    fun atOrAfterHalfPastEightFiresTheNextEvening() {
        assertEquals(at(2026, 10, 8, 20, 30), ReminderSchedule.nextTrigger(at(2026, 10, 7, 20, 30)))
        assertEquals(at(2026, 10, 8, 20, 30), ReminderSchedule.nextTrigger(at(2026, 10, 7, 23, 59)))
    }

    @Test
    fun keepsLocalTimeAcrossDaylightSavingChanges() {
        // Clocks go back on 25 October 2026 in Croatia; the reminder stays at 20:30 local time.
        val next = ReminderSchedule.nextTrigger(at(2026, 10, 24, 21, 0))
        assertEquals(LocalDateTime.of(2026, 10, 25, 20, 30), next.toLocalDateTime())
        assertEquals(1, next.offset.totalSeconds / 3600)
    }
}
