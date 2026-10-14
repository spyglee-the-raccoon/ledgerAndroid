package work.spyglee.ladger.domain

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthBrowserTest {
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)
    private val november = YearMonth.of(2026, 11)

    @Test
    fun `goes back only while older transactions exist`() {
        val browser = MonthBrowser.at(october)
        assertTrue(browser.canShowPrevious(day(2026, 9, 30, hour = 23), UTC))
        assertFalse(browser.canShowPrevious(day(2026, 10, 1), UTC))
        assertFalse(browser.canShowPrevious(null, UTC))
        assertEquals(MonthBrowser(september, october), browser.previous())
    }

    @Test
    fun `goes forward only up to the current month`() {
        val browser = MonthBrowser(september, october)
        assertTrue(browser.canShowNext)
        assertEquals(MonthBrowser.at(october), browser.next())
        assertFalse(browser.next().canShowNext)
        assertEquals(MonthBrowser.at(october), browser.next().next())
    }

    @Test
    fun `follows the month rollover only when showing the current month`() {
        assertEquals(MonthBrowser.at(november), MonthBrowser.at(october).withCurrent(november))
        assertEquals(MonthBrowser(september, november), MonthBrowser(september, october).withCurrent(november))
        assertEquals(MonthBrowser(september, october), MonthBrowser(september, october).withCurrent(october))
    }
}
