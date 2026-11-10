package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.SourceSuggestion.Role
import work.spyglee.ladger.domain.receipt.ReceiptItems

/**
 * The cases of iOS's `ModelLogicTests` and `RecurrenceTests` (added in `8a76b5d`) that Android's own
 * tests don't already cover, with the same inputs and answers, so both apps stay in step.
 */
class IosParityTest {
    @Test
    fun `merchant names drop case, diacritics and legal forms`() {
        val cases = listOf(
            "Konzum plus d.o.o." to "konzum plus",
            "KONZUM PLUS D.O.O." to "konzum plus",
            "TISAK d.d." to "tisak",
            "Pekara Čokolada, obrt" to "pekara cokolada",
            "Müller Ltd" to "muller",
            "  Lidl   Hrvatska  " to "lidl hrvatska",
            "d.o.o." to "",
        )
        for ((merchant, expected) in cases) assertEquals(merchant, expected, MerchantLearning.normalize(merchant))
    }

    @Test
    fun `receipt item names ignore case, diacritics and spacing`() {
        val cases = listOf("Mlijeko  1L" to "mlijeko 1l", "  MLIJEKO 1l " to "mlijeko 1l", "ČOKOLADA\tMILKA" to "cokolada milka")
        for ((name, expected) in cases) assertEquals(name, expected, ReceiptItems.normalize(name))
    }

    @Test
    fun `newer uses push old ones out of the suggestion history`() {
        // Newest first: 30 card uses, then 40 older cash uses.
        val recent = List(30) { expense("1", sourceId = 2) } + List(40) { expense("1", sourceId = 1) }
        assertEquals(2L, SourceSuggestion.mostLikely(Role.SOURCE, TransactionKind.EXPENSE, recent))
    }

    private fun planned(
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        start: Instant,
        end: Instant? = null,
    ) = PlannedTransaction(
        amount = BigDecimal("9.99"), kind = TransactionKind.EXPENSE, note = "Streaming",
        frequency = frequency, interval = interval, startDate = start, endDate = end,
    )

    @Test
    fun `the second occurrence follows frequency and interval`() {
        val cases = listOf(
            Triple(RecurrenceFrequency.DAILY, 1, day(2026, 3, 16)),
            Triple(RecurrenceFrequency.DAILY, 3, day(2026, 3, 18)),
            Triple(RecurrenceFrequency.WEEKLY, 1, day(2026, 3, 22)),
            Triple(RecurrenceFrequency.WEEKLY, 2, day(2026, 3, 29)),
            Triple(RecurrenceFrequency.MONTHLY, 1, day(2026, 4, 15)),
            Triple(RecurrenceFrequency.MONTHLY, 3, day(2026, 6, 15)),
            Triple(RecurrenceFrequency.YEARLY, 1, day(2027, 3, 15)),
        )
        for ((frequency, interval, expected) in cases) {
            val plan = planned(frequency, interval, start = day(2026, 3, 15))
            assertEquals(day(2026, 3, 15), Recurrence.occurrenceDate(plan, 0, UTC))
            assertEquals("$frequency × $interval", expected, Recurrence.occurrenceDate(plan, 1, UTC))
        }
    }

    @Test
    fun `a yearly plan on 29 February falls back to the 28th, and returns in leap years`() {
        val plan = planned(RecurrenceFrequency.YEARLY, start = day(2028, 2, 29))
        assertEquals(day(2029, 2, 28), Recurrence.occurrenceDate(plan, 1, UTC))
        assertEquals(day(2032, 2, 29), Recurrence.occurrenceDate(plan, 4, UTC))
    }

    @Test
    fun `every past occurrence is due, including one due exactly now`() {
        val plan = planned(start = day(2026, 7, 10, hour = 12))
        assertEquals(4, Recurrence.dueDates(plan, day(2026, 10, 15, hour = 12), UTC).size)
        assertEquals(1, Recurrence.dueDates(planned(start = day(2026, 10, 15, hour = 12)), day(2026, 10, 15, hour = 12), UTC).size)
        assertEquals(0, Recurrence.dueDates(planned(start = day(2026, 11, 1)), day(2026, 10, 15), UTC).size)
    }

    @Test
    fun `recording again only adds new occurrences and stops at the end date`() {
        val plan = planned(start = day(2026, 7, 10, hour = 12))
        val first = Recurrence.dueDates(plan, day(2026, 8, 1), UTC)
        assertEquals(1, first.size)
        assertEquals(3, Recurrence.dueDates(plan.copy(occurrencesGenerated = first.size), day(2026, 10, 15, hour = 12), UTC).size)

        val ending = planned(start = day(2026, 7, 10, hour = 12), end = day(2026, 8, 20))
        assertEquals(2, Recurrence.dueDates(ending, day(2026, 10, 15), UTC).size)
    }

    @Test
    fun `a daily plan catches up after a long gap`() {
        assertEquals(30, Recurrence.dueDates(planned(RecurrenceFrequency.DAILY, start = day(2026, 10, 1, hour = 12)), day(2026, 10, 30, hour = 12), UTC).size)
    }
}
