package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.TransactionKind

class PlannedTransactionsTest {
    private fun planned(
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        start: java.time.Instant = day(2026, 1, 31, hour = 9),
        end: java.time.Instant? = null,
        generated: Int = 0,
        paused: Boolean = false,
    ) = PlannedTransaction(
        id = 7, amount = BigDecimal("12.99"), kind = TransactionKind.EXPENSE, sourceId = 1, categoryId = 2, note = "Netflix",
        frequency = frequency, interval = interval, startDate = start, endDate = end, occurrencesGenerated = generated, isPaused = paused,
    )

    @Test
    fun `months without the start's day use their last day, without drifting`() {
        val plan = planned()
        assertEquals(
            listOf(day(2026, 1, 31, 9), day(2026, 2, 28, 9), day(2026, 3, 31, 9), day(2026, 4, 30, 9)),
            (0..3).map { Recurrence.occurrenceDate(plan, it, UTC) },
        )
    }

    @Test
    fun `intervals multiply the unit`() {
        val start = day(2026, 10, 1, 8)
        assertEquals(day(2026, 10, 7, 8), Recurrence.occurrenceDate(planned(RecurrenceFrequency.DAILY, 3, start), 2, UTC))
        assertEquals(day(2026, 10, 29, 8), Recurrence.occurrenceDate(planned(RecurrenceFrequency.WEEKLY, 2, start), 2, UTC))
        assertEquals(day(2028, 10, 1, 8), Recurrence.occurrenceDate(planned(RecurrenceFrequency.YEARLY, 1, start), 2, UTC))
    }

    @Test
    fun `days keep the local time across a daylight saving change`() {
        val zagreb = ZoneId.of("Europe/Zagreb")
        val start = LocalDateTime.of(2026, 10, 24, 9, 0).atZone(zagreb).toInstant()
        val afterChange = Recurrence.occurrenceDate(planned(RecurrenceFrequency.DAILY, start = start), 2, zagreb)
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), afterChange.atZone(zagreb).toLocalDateTime())
    }

    @Test
    fun `due dates are the unrecorded occurrences up to now`() {
        val plan = planned(RecurrenceFrequency.WEEKLY, start = day(2026, 10, 1, 9), generated = 1)
        assertEquals(listOf(day(2026, 10, 8, 9), day(2026, 10, 15, 9)), Recurrence.dueDates(plan, day(2026, 10, 15, 9), UTC))
        assertEquals(emptyList<Any>(), Recurrence.dueDates(plan, day(2026, 10, 8, 8), UTC))
    }

    @Test
    fun `a plan starting now is due straight away`() {
        val now = day(2026, 10, 21, 12)
        assertEquals(listOf(now), Recurrence.dueDates(planned(start = now), now, UTC))
    }

    @Test
    fun `nothing is due after the end date or while paused`() {
        val start = day(2026, 10, 1, 9)
        val ending = planned(RecurrenceFrequency.DAILY, start = start, end = day(2026, 10, 3, 9))
        assertEquals(3, Recurrence.dueDates(ending, day(2026, 12, 1), UTC).size)
        assertEquals(emptyList<Any>(), Recurrence.dueDates(planned(start = start, paused = true), day(2026, 12, 1), UTC))
    }

    @Test
    fun `next date is null once the plan has ended, and kept while paused`() {
        val start = day(2026, 10, 1, 9)
        assertNull(Recurrence.nextDate(planned(start = start, end = day(2026, 10, 20), generated = 1), UTC))
        assertEquals(day(2026, 11, 1, 9), Recurrence.nextDate(planned(start = start, generated = 1, paused = true), UTC))
    }

    @Test
    fun `recorded transactions copy the plan and link to it`() {
        val transaction = Recurrence.transaction(planned(), day(2026, 2, 28, 9))
        assertEquals(TransactionKind.EXPENSE, transaction.kind)
        assertAmount("12.99", transaction.amount)
        assertEquals(1L, transaction.sourceId)
        assertEquals(2L, transaction.categoryId)
        assertEquals("Netflix", transaction.note)
        assertEquals(day(2026, 2, 28, 9), transaction.date)
        assertEquals(7L, transaction.plannedTransactionId)
    }

    @Test
    fun `draft validity follows the transaction rules plus the schedule`() {
        val valid = PlannedTransactionDraft(BigDecimal.TEN, TransactionKind.EXPENSE, sourceId = 1, categoryId = 2, startDate = day(2026, 10, 1))
        assertTrue(valid.isValid)
        assertFalse(valid.copy(categoryId = null).isValid)
        assertFalse(valid.copy(interval = 0).isValid)
        assertFalse(valid.copy(interval = 366).isValid)
        assertFalse(valid.copy(endDate = day(2026, 9, 30)).isValid)
        assertTrue(valid.copy(endDate = day(2026, 10, 1)).isValid)
    }

    @Test
    fun `switching kind clears what the new kind doesn't use`() {
        val categories = listOf(Category(id = 2, name = "Food"), Category(id = 3, name = "Salary", kind = CategoryKind.INCOME))
        val draft = PlannedTransactionDraft(BigDecimal.TEN, TransactionKind.EXPENSE, sourceId = 1, categoryId = 2)
        val income = draft.withKind(TransactionKind.INCOME, categories)
        assertNull(income.sourceId)
        assertNull(income.categoryId)
        val transfer = draft.copy(destinationId = 4).withKind(TransactionKind.TRANSFER, categories)
        assertEquals(1L, transfer.sourceId)
        assertNull(transfer.categoryId)
    }

    @Test
    fun `turning the end date on sets it a year after the start`() {
        val draft = PlannedTransactionDraft(startDate = day(2026, 10, 1, 9)).withEndDate(true, UTC)
        assertEquals(day(2027, 10, 1, 9), draft.endDate)
        assertNull(draft.withEndDate(false, UTC).endDate)
    }

    @Test
    fun `editing keeps the id and the occurrences already recorded`() {
        val existing = planned(generated = 4)
        val edited = PlannedTransactionDraft.from(existing).copy(interval = 2).toPlanned(existing)
        assertEquals(existing.copy(interval = 2), edited)
        assertEquals(0, PlannedTransactionDraft.from(existing).toPlanned(null).occurrencesGenerated)
    }
}
