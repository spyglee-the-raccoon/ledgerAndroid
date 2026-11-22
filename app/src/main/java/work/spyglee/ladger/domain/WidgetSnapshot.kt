package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

/** One category's spending in the month chart widget; uncategorized expenses are a gray slice. */
data class WidgetSlice(val name: String, val amount: BigDecimal, val color: CategoryColor)

/** The budget the budget widget shows, with this month's spending; no category is the general limit. */
data class WidgetBudget(val usage: BudgetUsage, val category: Category?)

/** A recent transaction with its category, if it has one. */
data class WidgetTransaction(val transaction: Transaction, val category: Category?)

/**
 * What the widgets show (decision D4), as iOS's `WidgetSnapshot`: this month's spending by category and
 * balance, one budget, and the latest transactions. iOS widgets can't open the database, so the iOS app
 * writes this for them; Android widgets run in the app's process and build it when they update.
 */
data class WidgetSnapshot(
    val month: MonthRange,
    val budget: WidgetBudget?,
    val slices: List<WidgetSlice>,
    val monthBalance: BigDecimal,
    val recentTransactions: List<WidgetTransaction>,
) {
    /** The snapshot as it should look at [now]: once its month is over, nothing has been spent or earned in the new month yet. */
    fun current(now: Instant): WidgetSnapshot {
        if (now in month) return this
        return copy(
            month = MonthRange.containing(now, month.zone),
            budget = budget?.let { it.copy(usage = it.usage.copy(spent = BigDecimal.ZERO)) },
            slices = emptyList(),
            monthBalance = BigDecimal.ZERO,
        )
    }

    companion object {
        /** How many transactions the transactions widget lists. */
        const val RECENT_COUNT = 3

        /**
         * The snapshot at [now] in [zone], from the month's transactions ([monthTransactions]; others are
         * ignored), recent ones ([recentTransactions], at least the latest [RECENT_COUNT]), all budgets and categories.
         */
        fun make(
            now: Instant,
            zone: ZoneId,
            monthTransactions: List<Transaction>,
            recentTransactions: List<Transaction>,
            budgets: List<Budget>,
            categories: List<Category>,
            uncategorizedName: String,
        ): WidgetSnapshot {
            val month = MonthRange.containing(now, zone)
            val inMonth = monthTransactions.filter { it.date in month }
            val categoriesById = categories.associateBy { it.id }
            return WidgetSnapshot(
                month = month,
                budget = shownBudget(Budgets.usages(budgets, inMonth, month), categoriesById),
                slices = slices(inMonth, categoriesById, uncategorizedName),
                monthBalance = Balances.net(inMonth),
                recentTransactions = recentTransactions
                    .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
                    .take(RECENT_COUNT)
                    .map { transaction ->
                        WidgetTransaction(transaction, transaction.categoryId?.let(categoriesById::get))
                    },
            )
        }

        /** The general limit when it's set and on, otherwise the first active category budget by name, as on iOS. */
        private fun shownBudget(usages: List<BudgetUsage>, categories: Map<Long, Category>): WidgetBudget? {
            val shown = usages.filter { it.budget.isActive && it.budget.monthlyLimit.signum() > 0 }
            shown.firstOrNull { it.budget.isGeneral }?.let { return WidgetBudget(it, null) }
            val collator = Collator.getInstance()
            return shown
                .mapNotNull { usage -> usage.budget.categoryId?.let(categories::get)?.let { WidgetBudget(usage, it) } }
                .minWithOrNull { a, b -> collator.compare(a.category?.name.orEmpty(), b.category?.name.orEmpty()) }
        }

        /** Spending per category, largest first (then by name), like iOS's widget; uncategorized is gray. */
        private fun slices(transactions: List<Transaction>, categories: Map<Long, Category>, uncategorizedName: String): List<WidgetSlice> {
            val byCategory = linkedMapOf<Long, BigDecimal>()
            var uncategorized = BigDecimal.ZERO
            for (expense in transactions.filter { it.kind == TransactionKind.EXPENSE }) {
                val categoryId = expense.categoryId?.takeIf { it in categories }
                if (categoryId != null) {
                    byCategory.merge(categoryId, expense.amount, BigDecimal::add)
                } else {
                    uncategorized += expense.amount
                }
            }
            val slices = byCategory.map { (id, amount) ->
                val category = categories.getValue(id)
                WidgetSlice(category.name, amount, category.color)
            } + listOfNotNull(
                WidgetSlice(uncategorizedName, uncategorized, CategoryColor.GRAY).takeIf { uncategorized.signum() > 0 }
            )
            return slices.sortedWith(compareByDescending<WidgetSlice> { it.amount }.thenBy { it.name })
        }
    }
}
