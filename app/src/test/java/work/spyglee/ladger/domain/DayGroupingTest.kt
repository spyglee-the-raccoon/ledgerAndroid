package work.spyglee.ladger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DayGroupingTest {
    @Test
    fun `groups by day, newest day and newest transaction first`() {
        val sections = DayGrouping.groupByDay(
            listOf(
                expense("1", id = 1, date = day(2026, 10, 1, hour = 9)),
                expense("2", id = 2, date = day(2026, 10, 2, hour = 8)),
                expense("3", id = 3, date = day(2026, 10, 1, hour = 18)),
            ),
            UTC,
        )
        assertEquals(listOf(date(2026, 10, 2), date(2026, 10, 1)), sections.map { it.day })
        assertEquals(listOf(3L, 1L), sections[1].items.map { it.id })
    }

    @Test
    fun `relative day labels`() {
        val today = date(2026, 10, 7)
        assertEquals(RelativeDay.TODAY, DayGrouping.relativeDay(today, today))
        assertEquals(RelativeDay.YESTERDAY, DayGrouping.relativeDay(date(2026, 10, 6), today))
        assertEquals(RelativeDay.OTHER, DayGrouping.relativeDay(date(2026, 10, 5), today))
    }

    @Test
    fun `search ignores case and diacritics`() {
        assertEquals(true, "Režije za listopad".containsFolded("REZIJE"))
        assertEquals(true, "Đakovo".containsFolded("dak"))
        assertEquals(false, "Konzum".containsFolded("lidl"))
    }
}
