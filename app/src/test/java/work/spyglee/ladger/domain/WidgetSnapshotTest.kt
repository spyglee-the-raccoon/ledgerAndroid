package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

/** The widget data, as iOS's `WidgetSnapshotTests` (8a76b5d). */
class WidgetSnapshotTest {
    private val now = day(2026, 10, 15)
    private val categories = mutableListOf<Category>()
    private val transactions = mutableListOf<Transaction>()
    private val budgets = mutableListOf<Budget>()

    private fun category(name: String, color: CategoryColor = CategoryColor.BLUE): Category =
        Category(id = categories.size + 1L, name = name, color = color).also { categories += it }

    private fun addExpense(amount: String, category: Category?, on: Instant, note: String = "") {
        transactions += expense(amount, categoryId = category?.id, date = on).copy(id = transactions.size + 1L, note = note)
    }

    private fun add(kind: TransactionKind, amount: String, on: Instant, note: String = "") {
        transactions += Transaction(id = transactions.size + 1L, amount = BigDecimal(amount), kind = kind, note = note, date = on)
    }

    private fun budget(limit: String, category: Category? = null, isActive: Boolean = true) {
        budgets += Budget(id = budgets.size + 1L, monthlyLimit = BigDecimal(limit), isActive = isActive, categoryId = category?.id)
    }

    private fun snapshot(at: Instant = now) = WidgetSnapshot.make(
        now = at,
        zone = UTC,
        monthTransactions = transactions,
        recentTransactions = transactions,
        budgets = budgets,
        categories = categories,
        uncategorizedName = "Uncategorized",
    )

    @Test
    fun `shows the general budget when it is set and on`() {
        val food = category("Food")
        budget("500")
        budget("100", food)
        addExpense("40", food, day(2026, 10, 3))

        val shown = snapshot().budget!!
        assertNull(shown.category)
        assertAmount("500", shown.usage.budget.monthlyLimit)
        assertAmount("40", shown.usage.spent)
    }

    @Test
    fun `falls back to the first active category budget by name`() {
        for ((generalLimit, generalActive) in listOf("500" to false, "0" to true)) {
            categories.clear(); transactions.clear(); budgets.clear()
            val rent = category("Rent")
            val food = category("Food")
            val cafe = category("Cafe")
            budget(generalLimit, isActive = generalActive)
            budget("900", rent)
            budget("100", food)
            budget("50", cafe, isActive = false)
            addExpense("30", food, day(2026, 10, 3))
            addExpense("800", rent, day(2026, 10, 1))

            val shown = snapshot().budget!!
            assertEquals("Alphabetically first active category budget", "Food", shown.category?.name)
            assertAmount("30", shown.usage.spent)
        }
    }

    @Test
    fun `no active budget shows none`() {
        budget("500", isActive = false)
        assertNull(snapshot().budget)
    }

    @Test
    fun `chart and balance cover only this month`() {
        val food = category("Food", CategoryColor.GREEN)
        val rent = category("Rent", CategoryColor.RED)
        addExpense("30", food, day(2026, 10, 2))
        addExpense("20", food, day(2026, 10, 9))
        addExpense("400", rent, day(2026, 10, 1))
        addExpense("5", null, day(2026, 10, 4))
        addExpense("999", food, day(2026, 9, 30))
        add(TransactionKind.INCOME, "1000", day(2026, 10, 1))
        add(TransactionKind.TRANSFER, "300", day(2026, 10, 1))

        val result = snapshot()
        assertEquals("Largest first, uncategorized included", listOf("400", "50", "5"), result.slices.map { it.amount.toPlainString() })
        assertEquals(listOf(CategoryColor.RED, CategoryColor.GREEN, CategoryColor.GRAY), result.slices.map { it.color })
        assertAmount("545", result.monthBalance)
    }

    @Test
    fun `recent transactions are the latest three of any kind`() {
        val food = category("Food")
        addExpense("1", food, day(2026, 9, 1), note = "old")
        addExpense("2", food, day(2026, 10, 1), note = "second")
        add(TransactionKind.INCOME, "3", day(2026, 10, 5), note = "third")
        add(TransactionKind.TRANSFER, "4", day(2026, 10, 14), note = "latest")

        val recent = snapshot().recentTransactions
        assertEquals(listOf("latest", "third", "second"), recent.map { it.transaction.note })
        assertEquals(listOf(TransactionKind.TRANSFER, TransactionKind.INCOME, TransactionKind.EXPENSE), recent.map { it.transaction.kind })
        assertEquals("Food", recent.last().category?.name)
    }

    @Test
    fun `next month starts empty but keeps the recent transactions`() {
        val food = category("Food")
        budget("500")
        addExpense("40", food, day(2026, 10, 3))
        val october = snapshot()

        assertEquals(october, october.current(day(2026, 10, 31, hour = 23)))

        val november = october.current(day(2026, 11, 1))
        assertAmount("0", november.budget!!.usage.spent)
        assertAmount("500", november.budget!!.usage.budget.monthlyLimit)
        assertTrue(november.slices.isEmpty())
        assertAmount("0", november.monthBalance)
        assertEquals(october.recentTransactions, november.recentTransactions)
        assertEquals(day(2026, 11, 1), november.month.start)
    }
}
