package work.spyglee.ladger.domain

import java.time.LocalDate
import java.time.ZoneId
import work.spyglee.ladger.data.Transaction

data class DaySection(val day: LocalDate, val items: List<Transaction>)

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

object DayGrouping {
    /** Transactions grouped by calendar day, newest day first and newest transaction first within a day. */
    fun groupByDay(transactions: List<Transaction>, zone: ZoneId): List<DaySection> =
        transactions
            .groupBy { it.date.atZone(zone).toLocalDate() }
            .map { (day, items) -> DaySection(day, items.sortedByDescending { it.date }) }
            .sortedByDescending { it.day }

    /** Decides whether a section header reads "today", "yesterday" or the full date. */
    fun relativeDay(day: LocalDate, today: LocalDate): RelativeDay = when (day) {
        today -> RelativeDay.TODAY
        today.minusDays(1) -> RelativeDay.YESTERDAY
        else -> RelativeDay.OTHER
    }
}
