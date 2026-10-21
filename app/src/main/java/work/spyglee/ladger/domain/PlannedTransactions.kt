package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

/** When planned transactions fall due, as iOS's `PlannedTransaction` and `RecurrenceEngine`. */
object Recurrence {
    /** Interval range of the editor's stepper, as on iOS. */
    val INTERVALS = 1..365

    /**
     * Occurrence [n] (0 is the start date): the start plus n × interval units of local time in
     * [zone]. Months without the start's day use their last day (31 January, 28 February, 31 March),
     * as Foundation's `Calendar.date(byAdding:)` does.
     */
    fun occurrenceDate(planned: PlannedTransaction, n: Int, zone: ZoneId): Instant {
        val start = planned.startDate.atZone(zone)
        val units = n.toLong() * planned.interval
        return when (planned.frequency) {
            RecurrenceFrequency.DAILY -> start.plusDays(units)
            RecurrenceFrequency.WEEKLY -> start.plusWeeks(units)
            RecurrenceFrequency.MONTHLY -> start.plusMonths(units)
            RecurrenceFrequency.YEARLY -> start.plusYears(units)
        }.toInstant()
    }

    /** The next occurrence to record, or null once it would fall after the end date. Paused plans still have one. */
    fun nextDate(planned: PlannedTransaction, zone: ZoneId): Instant? =
        occurrenceDate(planned, planned.occurrencesGenerated, zone).takeUnless { planned.endDate != null && it > planned.endDate }

    /** Occurrences due by [now] that haven't been recorded, oldest first; none while paused. */
    fun dueDates(planned: PlannedTransaction, now: Instant, zone: ZoneId): List<Instant> {
        if (planned.isPaused || planned.interval < 1) return emptyList()
        val dates = mutableListOf<Instant>()
        var n = planned.occurrencesGenerated
        while (true) {
            val due = occurrenceDate(planned, n, zone)
            if (due > now || (planned.endDate != null && due > planned.endDate)) break
            dates += due
            n++
        }
        return dates
    }

    /** The transaction recorded for the occurrence on [date]. */
    fun transaction(planned: PlannedTransaction, date: Instant) = Transaction(
        amount = planned.amount,
        kind = planned.kind,
        sourceId = planned.sourceId,
        destinationId = planned.destinationId,
        note = planned.note,
        date = date,
        categoryId = planned.categoryId,
        plannedTransactionId = planned.id,
    )
}

/** The editable state of the add/edit planned transaction form. */
data class PlannedTransactionDraft(
    val amount: BigDecimal? = null,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val sourceId: Long? = null,
    val destinationId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val interval: Int = 1,
    val startDate: Instant = Instant.now(),
    val endDate: Instant? = null,
    val isPaused: Boolean = false,
) {
    /** The same sources and category as a transaction of its kind, a valid interval, and no end before the start. */
    val isValid: Boolean
        get() = interval in Recurrence.INTERVALS && (endDate == null || endDate >= startDate) && asTransactionDraft().isValid

    /** Clears what the new kind doesn't use, as [TransactionDraft.withKind]. */
    fun withKind(newKind: TransactionKind, categories: List<Category>): PlannedTransactionDraft {
        val switched = asTransactionDraft().withKind(newKind, categories)
        return copy(kind = newKind, sourceId = switched.sourceId, destinationId = switched.destinationId, categoryId = switched.categoryId)
    }

    /** Turning the end date on starts it a year after the start date, as on iOS. */
    fun withEndDate(enabled: Boolean, zone: ZoneId): PlannedTransactionDraft =
        copy(endDate = if (enabled) startDate.atZone(zone).plusYears(1).toInstant() else null)

    /**
     * The plan to save; only call when [isValid]. Editing keeps [existing]'s id and the count of
     * occurrences already recorded, so changing the schedule doesn't record past ones again.
     */
    fun toPlanned(existing: PlannedTransaction?): PlannedTransaction = PlannedTransaction(
        id = existing?.id ?: 0,
        amount = checkNotNull(amount),
        kind = kind,
        sourceId = sourceId.takeIf { kind != TransactionKind.INCOME },
        destinationId = destinationId.takeIf { kind != TransactionKind.EXPENSE },
        note = note,
        categoryId = categoryId.takeIf { kind != TransactionKind.TRANSFER },
        frequency = frequency,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
        occurrencesGenerated = existing?.occurrencesGenerated ?: 0,
        isPaused = isPaused,
    )

    private fun asTransactionDraft() = TransactionDraft(amount, kind, sourceId, destinationId, categoryId)

    companion object {
        fun from(planned: PlannedTransaction) = PlannedTransactionDraft(
            amount = planned.amount,
            kind = planned.kind,
            sourceId = planned.sourceId,
            destinationId = planned.destinationId,
            categoryId = planned.categoryId,
            note = planned.note,
            frequency = planned.frequency,
            interval = planned.interval,
            startDate = planned.startDate,
            endDate = planned.endDate,
            isPaused = planned.isPaused,
        )
    }
}
