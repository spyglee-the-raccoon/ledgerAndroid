package work.spyglee.ladger.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A calendar month in [zone], as the half-open instant range `[start, end)`. */
data class MonthRange(val month: YearMonth, val zone: ZoneId) {
    val start: Instant = month.atDay(1).atStartOfDay(zone).toInstant()
    val end: Instant = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end

    companion object {
        fun containing(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): MonthRange =
            MonthRange(YearMonth.from(instant.atZone(zone)), zone)
    }
}

fun Instant.yearMonth(zone: ZoneId): YearMonth = YearMonth.from(atZone(zone))
