package work.spyglee.ladger.domain

import java.math.BigDecimal
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.Transaction

data class CategoryTotal(val category: Category, val total: BigDecimal)

object Collections {
    /** Incomes minus expenses. Transfers move money between the user's own sources, so they don't count. */
    fun total(transactions: List<Transaction>): BigDecimal = Balances.net(transactions)

    /**
     * Categories with the largest totals (by absolute value) among [transactions], as shown at the
     * top of a collection. Uncategorized transactions are left out; ties keep category order.
     */
    fun topCategories(transactions: List<Transaction>, categories: List<Category>, limit: Int = 3): List<CategoryTotal> {
        val byCategory = transactions.filter { it.categoryId != null }.groupBy { it.categoryId }
        return categories
            .mapNotNull { category -> byCategory[category.id]?.let { CategoryTotal(category, total(it)) } }
            .sortedByDescending { it.total.abs() }
            .take(limit)
    }
}
