package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.Budget

class BudgetsTest {
    private val october = MonthRange(YearMonth.of(2026, 10), UTC)
    private val general = Budget(id = 1, monthlyLimit = BigDecimal("100"))
    private val food = Budget(id = 2, monthlyLimit = BigDecimal("30"), categoryId = 7)

    private val transactions = listOf(
        expense("20", categoryId = 7, date = day(2026, 10, 3)),
        expense("15.50", categoryId = 7, date = day(2026, 10, 31, 23)),
        expense("40", categoryId = 8, date = day(2026, 10, 10)),
        expense("9", date = day(2026, 10, 12)),
        expense("500", categoryId = 7, date = day(2026, 9, 30, 23)),
        expense("500", categoryId = 7, date = day(2026, 11, 1)),
        income("1000", categoryId = 7, date = day(2026, 10, 5)),
        transfer("300", sourceId = 1, destinationId = 2, date = day(2026, 10, 5)),
    )

    @Test
    fun `the general limit counts every expense of the month, a category limit only its own`() {
        val (generalUsage, foodUsage) = Budgets.usages(listOf(general, food), transactions, october)
        assertAmount("84.50", generalUsage.spent)
        assertAmount("35.50", foodUsage.spent)
        assertAmount("15.50", generalUsage.remaining)
        assertAmount("-5.50", foodUsage.remaining)
        assertEquals(0.845, generalUsage.ratio, 1e-9)
    }

    @Test
    fun `a budget without a limit is never used up or reached`() {
        val usage = BudgetUsage(Budget(monthlyLimit = BigDecimal.ZERO), BigDecimal("50"))
        assertEquals(0.0, usage.ratio, 0.0)
        assertEquals(false, usage.isReached)
    }

    @Test
    fun `the general usage needs an active general budget with a limit`() {
        val usages = Budgets.usages(listOf(food, general), transactions, october)
        assertEquals(general, Budgets.generalUsage(usages)?.budget)
        assertNull(Budgets.generalUsage(Budgets.usages(listOf(food, general.copy(isActive = false)), transactions, october)))
        assertNull(Budgets.generalUsage(Budgets.usages(listOf(general.copy(monthlyLimit = BigDecimal.ZERO)), transactions, october)))
    }

    @Test
    fun `reaching a limit alerts once a month`() {
        val now = day(2026, 10, 20)
        val first = Budgets.check(Budgets.usages(listOf(general, food), transactions, october), october, now)
        assertEquals(listOf(food.copy(lastAlertDate = now)), first.alerts.map { it.budget })
        assertEquals(listOf(food.copy(lastAlertDate = now)), first.updated)

        val second = Budgets.check(Budgets.usages(listOf(general, food.copy(lastAlertDate = now)), transactions, october), october, now)
        assertTrue(second.alerts.isEmpty())
        assertTrue(second.updated.isEmpty())
    }

    @Test
    fun `last month's alert doesn't stop this month's`() {
        val alertedInSeptember = food.copy(lastAlertDate = day(2026, 9, 15))
        val result = Budgets.check(Budgets.usages(listOf(alertedInSeptember), transactions, october), october, day(2026, 10, 20))
        assertEquals(1, result.alerts.size)
    }

    @Test
    fun `dropping below the limit again allows a new alert`() {
        val alerted = food.copy(lastAlertDate = day(2026, 10, 4))
        val fewer = transactions.drop(1)
        val result = Budgets.check(Budgets.usages(listOf(alerted), fewer, october), october, day(2026, 10, 20))
        assertTrue(result.alerts.isEmpty())
        assertEquals(listOf(food), result.updated)
    }

    @Test
    fun `inactive budgets don't alert`() {
        val result = Budgets.check(Budgets.usages(listOf(food.copy(isActive = false)), transactions, october), october, day(2026, 10, 20))
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `seven hints by the share of the general limit used`() {
        val expected = listOf(
            0.0 to BudgetHint.WELL_UNDER, 0.2499 to BudgetHint.WELL_UNDER,
            0.25 to BudgetHint.UNDER_HALF, 0.5 to BudgetHint.OVER_HALF,
            0.75 to BudgetHint.GETTING_CLOSE, 0.9 to BudgetHint.ALMOST, 0.999 to BudgetHint.ALMOST,
            1.0 to BudgetHint.REACHED, 1.0999 to BudgetHint.REACHED, 1.1 to BudgetHint.OVER, 3.0 to BudgetHint.OVER,
        )
        expected.forEach { (ratio, hint) -> assertEquals("ratio $ratio", hint, Budgets.hint(ratio)) }
    }

    @Test
    fun `bar colour levels`() {
        fun level(spent: String, active: Boolean = true) =
            Budgets.level(BudgetUsage(general.copy(isActive = active), BigDecimal(spent)))
        assertEquals(BudgetLevel.FINE, level("79.99"))
        assertEquals(BudgetLevel.CLOSE, level("80"))
        assertEquals(BudgetLevel.OVER, level("100"))
        assertEquals(BudgetLevel.INACTIVE, level("100", active = false))
    }
}
