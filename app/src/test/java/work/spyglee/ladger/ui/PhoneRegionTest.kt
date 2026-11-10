package work.spyglee.ladger.ui

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** A language chosen for the app only keeps the phone's region, so amounts stay in its currency (D8). */
class PhoneRegionTest {
    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    @Test
    fun `a language without a region takes the phone's region`() {
        assertEquals(locale("uk-UA"), withPhoneRegion(locale("uk"), phoneLocale = locale("en-UA")))
        assertEquals(locale("en-HR"), withPhoneRegion(locale("en"), phoneLocale = locale("hr-HR")))
    }

    @Test
    fun `a language with a region stays as it is`() {
        assertEquals(locale("en-US"), withPhoneRegion(locale("en-US"), phoneLocale = locale("hr-HR")))
    }

    @Test
    fun `without a region on the phone either, nothing changes`() {
        assertEquals(locale("pl"), withPhoneRegion(locale("pl"), phoneLocale = locale("pl")))
    }

    @Test
    fun `amounts use the phone's currency, and EUR only when there is no region at all`() {
        assertEquals("£5.00", formatMoney(BigDecimal("5.00"), withPhoneRegion(locale("en"), phoneLocale = locale("en-GB"))))
        assertEquals("€5.00", formatMoney(BigDecimal("5.00"), locale("en")))
    }
}
