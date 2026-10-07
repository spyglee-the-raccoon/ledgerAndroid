package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

/** One donut slice. A null [category] is the "uncategorized" slice. */
data class CategorySlice(val category: Category?, val amount: BigDecimal)

data class IncomeExpenseMonth(val month: YearMonth, val incomes: BigDecimal, val expenses: BigDecimal)

data class SavingsMonth(val month: YearMonth, val startBalance: BigDecimal, val endBalance: BigDecimal) {
    val difference: BigDecimal get() = endBalance - startBalance
    val base: BigDecimal get() = startBalance.min(endBalance)
    val top: BigDecimal get() = startBalance.max(endBalance)
}

object Statistics {
    /**
     * Expense totals per category, in category order, followed by an "uncategorized" slice for
     * expenses without a known category. Categories with nothing spent are left out.
     */
    fun expensesByCategory(transactions: List<Transaction>, categories: List<Category>): List<CategorySlice> {
        val byCategory = mutableMapOf<Long, BigDecimal>()
        var uncategorized = BigDecimal.ZERO
        val knownIds = categories.mapTo(HashSet()) { it.id }
        for (expense in transactions.filter { it.kind == TransactionKind.EXPENSE }) {
            val categoryId = expense.categoryId
            if (categoryId != null && categoryId in knownIds) {
                byCategory.merge(categoryId, expense.amount, BigDecimal::add)
            } else {
                uncategorized += expense.amount
            }
        }
        val slices = categories.mapNotNull { category ->
            byCategory[category.id]?.takeIf { it.signum() > 0 }?.let { CategorySlice(category, it) }
        }
        return if (uncategorized.signum() > 0) slices + CategorySlice(null, uncategorized) else slices
    }

    /** Incomes and expenses per month from the first to the last month with data, gaps filled with zeros. Transfers are ignored. */
    fun incomeExpenseByMonth(transactions: List<Transaction>, zone: ZoneId): List<IncomeExpenseMonth> {
        val incomes = mutableMapOf<YearMonth, BigDecimal>()
        val expenses = mutableMapOf<YearMonth, BigDecimal>()
        for (transaction in transactions) {
            val month = transaction.date.yearMonth(zone)
            when (transaction.kind) {
                TransactionKind.INCOME -> incomes.merge(month, transaction.amount, BigDecimal::add)
                TransactionKind.EXPENSE -> expenses.merge(month, transaction.amount, BigDecimal::add)
                TransactionKind.TRANSFER -> Unit
            }
        }
        return monthsBetween(incomes.keys + expenses.keys).map { month ->
            IncomeExpenseMonth(month, incomes[month] ?: BigDecimal.ZERO, expenses[month] ?: BigDecimal.ZERO)
        }
    }

    /**
     * Running total balance per month: starts from the sources' starting values ([initialBalance]),
     * then adds each month's incomes minus expenses. Months without transactions keep the balance flat.
     */
    fun savingsByMonth(transactions: List<Transaction>, initialBalance: BigDecimal, zone: ZoneId): List<SavingsMonth> {
        val netByMonth = mutableMapOf<YearMonth, BigDecimal>()
        for (transaction in transactions) {
            val signed = when (transaction.kind) {
                TransactionKind.INCOME -> transaction.amount
                TransactionKind.EXPENSE -> transaction.amount.negate()
                TransactionKind.TRANSFER -> continue
            }
            netByMonth.merge(transaction.date.yearMonth(zone), signed, BigDecimal::add)
        }
        var balance = initialBalance
        return monthsBetween(netByMonth.keys).map { month ->
            val end = balance + (netByMonth[month] ?: BigDecimal.ZERO)
            SavingsMonth(month, balance, end).also { balance = end }
        }
    }

    /** The largest transactions of [kind] in [month], biggest first. */
    fun biggest(
        transactions: List<Transaction>,
        kind: TransactionKind,
        month: YearMonth,
        zone: ZoneId,
        limit: Int = 3,
    ): List<Transaction> =
        transactions
            .filter { it.kind == kind && it.date.yearMonth(zone) == month }
            .sortedByDescending { it.amount }
            .take(limit)

    private fun monthsBetween(months: Set<YearMonth>): List<YearMonth> {
        val first = months.minOrNull() ?: return emptyList()
        val last = months.max()
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.toList()
    }
}
