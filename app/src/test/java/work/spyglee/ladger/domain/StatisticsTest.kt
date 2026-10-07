package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.TransactionKind

class StatisticsTest {
    private val groceries = Category(id = 1, name = "Groceries")
    private val transport = Category(id = 2, name = "Transport")
    private val salary = Category(id = 3, name = "Salary", kind = CategoryKind.INCOME)

    @Test
    fun `expenses by category keep category order and end with uncategorized`() {
        val slices = Statistics.expensesByCategory(
            listOf(
                expense("10", categoryId = 1),
                expense("5", categoryId = 1),
                expense("7", categoryId = 99),
                expense("3"),
                income("1000", categoryId = 3),
            ),
            listOf(groceries, transport, salary),
        )
        assertEquals(listOf(groceries, null), slices.map { it.category })
        assertAmount("15", slices[0].amount)
        assertAmount("10", slices[1].amount)
    }

    @Test
    fun `income and expense months are gap-filled and ignore transfers`() {
        val months = Statistics.incomeExpenseByMonth(
            listOf(
                income("100", date = day(2026, 1, 10)),
                expense("40", date = day(2026, 1, 20)),
                transfer("999", 1, 2, date = day(2026, 2, 5)),
                expense("10", date = day(2026, 3, 1)),
            ),
            UTC,
        )
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), months.map { it.month })
        assertAmount("100", months[0].incomes)
        assertAmount("40", months[0].expenses)
        assertAmount("0", months[1].incomes)
        assertAmount("0", months[1].expenses)
        assertAmount("10", months[2].expenses)
    }

    @Test
    fun `savings carry the running balance from the starting values`() {
        val months = Statistics.savingsByMonth(
            listOf(
                income("100", date = day(2026, 1, 10)),
                expense("40", date = day(2026, 1, 20)),
                expense("10", date = day(2026, 3, 1)),
            ),
            initialBalance = BigDecimal("1000"),
            zone = UTC,
        )
        assertEquals(3, months.size)
        assertAmount("1000", months[0].startBalance)
        assertAmount("1060", months[0].endBalance)
        assertAmount("1060", months[1].startBalance)
        assertAmount("1060", months[1].endBalance)
        assertAmount("-10", months[2].difference)
        assertAmount("1050", months[2].base)
        assertAmount("1060", months[2].top)
    }

    @Test
    fun `no transactions give no months`() {
        assertTrue(Statistics.savingsByMonth(emptyList(), BigDecimal.TEN, UTC).isEmpty())
        assertTrue(Statistics.incomeExpenseByMonth(emptyList(), UTC).isEmpty())
    }

    @Test
    fun `biggest returns the top three of a kind in the month`() {
        val top = Statistics.biggest(
            listOf(
                expense("5", date = day(2026, 1, 1)),
                expense("50", date = day(2026, 1, 2)),
                expense("20", date = day(2026, 1, 3)),
                expense("30", date = day(2026, 1, 4)),
                expense("100", date = day(2026, 2, 1)),
                income("500", date = day(2026, 1, 5)),
            ),
            TransactionKind.EXPENSE,
            YearMonth.of(2026, 1),
            UTC,
        )
        assertEquals(listOf("50", "30", "20"), top.map { it.amount.toPlainString() })
    }

    @Test
    fun `month range is half-open`() {
        val range = MonthRange(YearMonth.of(2026, 2), UTC)
        assertTrue(day(2026, 2, 1) in range)
        assertTrue(day(2026, 2, 28, hour = 23) in range)
        assertEquals(false, day(2026, 3, 1) in range)
    }
}
