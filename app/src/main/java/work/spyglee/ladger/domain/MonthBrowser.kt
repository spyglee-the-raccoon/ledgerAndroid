package work.spyglee.ladger.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * The month shown on the main screen, which the user can swipe through as on iOS: back to the
 * month of the oldest transaction, and forward to the [current] month but not past it.
 */
data class MonthBrowser(val shown: YearMonth, val current: YearMonth) {
    val isCurrent: Boolean get() = shown == current
    val canShowNext: Boolean get() = shown < current

    /** True when a transaction older than the shown month exists; [earliest] is the oldest transaction's date. */
    fun canShowPrevious(earliest: Instant?, zone: ZoneId): Boolean =
        earliest != null && earliest < MonthRange(shown, zone).start

    fun previous(): MonthBrowser = copy(shown = shown.minusMonths(1))
    fun next(): MonthBrowser = if (canShowNext) copy(shown = shown.plusMonths(1)) else this

    /** At a month rollover the shown month follows only if the user was looking at the current month. */
    fun withCurrent(month: YearMonth): MonthBrowser =
        if (month == current) this else MonthBrowser(shown = if (isCurrent) month else shown, current = month)

    companion object {
        fun at(month: YearMonth) = MonthBrowser(month, month)
    }
}
