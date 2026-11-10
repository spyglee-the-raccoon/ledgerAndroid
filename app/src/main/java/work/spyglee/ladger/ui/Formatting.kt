package work.spyglee.ladger.ui

import android.icu.text.CompactDecimalFormat
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import work.spyglee.ladger.R
import work.spyglee.ladger.domain.DayGrouping
import work.spyglee.ladger.domain.RelativeDay

/**
 * The phone's own locale, which a language chosen for this app (Android 13+) doesn't change. [LadgerApp]
 * keeps it current; within the app's process, `Resources.getSystem()` already has the app's language.
 * Android 12 and lower have no per-app language, so there the default locale is the phone's.
 */
object PhoneLocale {
    @Volatile
    var current: Locale? = null

    fun get(): Locale = current ?: Locale.getDefault()
}

/**
 * [locale] with the phone's region added when it has none. A language chosen for this app only
 * (Android 13+, decision D8) comes without a region, but the currency and the number and date styles
 * depend on it. iOS keeps the phone's region in `Locale.current` with a per-app language, so this keeps
 * amounts in the phone's currency on both.
 */
fun withPhoneRegion(locale: Locale, phoneLocale: Locale = PhoneLocale.get()): Locale {
    if (locale.country.isNotEmpty() || phoneLocale.country.isEmpty()) return locale
    return runCatching { Locale.Builder().setLocale(locale).setRegion(phoneLocale.country).build() }.getOrDefault(locale)
}

/**
 * Formats an amount in the currency of the app's locale, with the phone's region ([withPhoneRegion]).
 * Locales without a region have no currency, so those fall back to EUR, as the iOS app does.
 * [wholeUnits] rounds to whole euros, dollars and so on, as the iOS widgets do.
 */
fun formatMoney(amount: BigDecimal, locale: Locale = withPhoneRegion(Locale.getDefault()), wholeUnits: Boolean = false): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    if (locale.country.isEmpty()) {
        format.currency = Currency.getInstance("EUR")
    }
    if (wholeUnits) {
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 0
    }
    return format.format(amount)
}

/** The decimal separator users type in amount fields. */
fun localDecimalSeparator(locale: Locale = withPhoneRegion(Locale.getDefault())): Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator

@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = withPhoneRegion(LocalConfiguration.current.locales[0])

/** "Today", "Yesterday" or the full date, as the headers of day-grouped lists. */
@Composable
fun dayTitle(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (DayGrouping.relativeDay(day, today)) {
    RelativeDay.TODAY -> stringResource(R.string.today)
    RelativeDay.YESTERDAY -> stringResource(R.string.yesterday)
    RelativeDay.OTHER -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale()))
}

/** Weekday, day and month without the year, e.g. "Wednesday, 7 October". */
@Composable
fun headerDate(day: LocalDate): String {
    val locale = currentLocale()
    return day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale))
}

@Composable
fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()))

/** Day, month and year, e.g. "21 Oct 2026". */
@Composable
fun formatDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()))

@Composable
fun formatDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(currentLocale())
    )

/** Month and year, e.g. "October 2026", for chart selections and section headers. */
@Composable
fun monthTitle(month: YearMonth): String = formatMonth(month, "MMMMy")

/** Short month and two-digit year, e.g. "Oct 26", for chart axes. */
@Composable
fun monthAxisLabel(month: YearMonth): String = formatMonth(month, "MMMyy")

@Composable
private fun formatMonth(month: YearMonth, skeleton: String): String {
    val locale = currentLocale()
    return month.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

/** A short signed amount without currency, e.g. "+1.2K" or "-350", as the savings chart labels on iOS. Zero has no sign. */
@Composable
fun formatCompactSigned(amount: BigDecimal): String {
    val text = CompactDecimalFormat.getInstance(currentLocale(), CompactDecimalFormat.CompactStyle.SHORT).format(amount)
    return if (amount.signum() > 0) "+$text" else text
}

/** A plain number such as a quantity, e.g. "0,456" or "2". */
@Composable
fun formatNumber(value: BigDecimal): String = NumberFormat.getNumberInstance(currentLocale()).apply { maximumFractionDigits = 3 }.format(value)

/**
 * A share as a percentage, e.g. 0.845 as "85 %" with no [fractionDigits]. With [signed] a rise gets a
 * plus sign, as iOS's `.sign(strategy: .always())` for price changes.
 */
@Composable
fun formatPercent(ratio: Double, fractionDigits: Int = 0, signed: Boolean = false): String {
    val format = NumberFormat.getPercentInstance(currentLocale()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    val text = format.format(ratio)
    return if (signed && ratio >= 0) "+$text" else text
}

/** Month and year, e.g. "Oct 2026", for "purchases since". */
@Composable
fun formatMonthYear(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    formatMonth(YearMonth.from(instant.atZone(zone)), "MMMy")
