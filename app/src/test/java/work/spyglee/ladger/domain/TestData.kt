package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

val UTC: ZoneId = ZoneOffset.UTC

fun day(year: Int, month: Int, day: Int, hour: Int = 0): Instant =
    LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC)

fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

fun expense(amount: String, sourceId: Long? = null, categoryId: Long? = null, date: Instant = day(2026, 10, 1), id: Long = 0) =
    Transaction(id = id, amount = BigDecimal(amount), kind = TransactionKind.EXPENSE, sourceId = sourceId, categoryId = categoryId, date = date)

fun income(amount: String, destinationId: Long? = null, categoryId: Long? = null, date: Instant = day(2026, 10, 1), id: Long = 0) =
    Transaction(id = id, amount = BigDecimal(amount), kind = TransactionKind.INCOME, destinationId = destinationId, categoryId = categoryId, date = date)

fun transfer(amount: String, sourceId: Long?, destinationId: Long?, date: Instant = day(2026, 10, 1), id: Long = 0) =
    Transaction(id = id, amount = BigDecimal(amount), kind = TransactionKind.TRANSFER, sourceId = sourceId, destinationId = destinationId, date = date)

/** BigDecimal equality ignoring scale, so 12.5 equals 12.50. */
fun assertAmount(expected: String, actual: BigDecimal?) {
    assertNotNull("expected $expected but was null", actual)
    assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
}
