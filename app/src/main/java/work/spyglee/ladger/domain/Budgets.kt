package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

/** What was spent against [budget] in a month. */
data class BudgetUsage(val budget: Budget, val spent: BigDecimal) {
    /** Share of the limit used, e.g. 0.5 for half; 0 when no limit is set. */
    val ratio: Double
        get() = if (budget.monthlyLimit.signum() > 0) spent.divide(budget.monthlyLimit, MathContext.DECIMAL64).toDouble() else 0.0

    val remaining: BigDecimal get() = budget.monthlyLimit - spent

    val isReached: Boolean get() = budget.isActive && budget.monthlyLimit.signum() > 0 && spent >= budget.monthlyLimit
}

/** Colour of a budget's progress bar. */
enum class BudgetLevel { INACTIVE, FINE, CLOSE, OVER }

/** The daily reminder's line about the general limit, by how much of it is used. */
enum class BudgetHint { WELL_UNDER, UNDER_HALF, OVER_HALF, GETTING_CLOSE, ALMOST, REACHED, OVER }

/** The result of [Budgets.check]: budgets to notify about, and budgets whose alert date changed. */
data class BudgetCheck(val alerts: List<BudgetUsage>, val updated: List<Budget>)

/** The iOS `BudgetNotifier` and `BudgetingView` rules. */
object Budgets {
    /** Each budget with the expenses of [month]: all of them for the general limit, else its category's. */
    fun usages(budgets: List<Budget>, transactions: List<Transaction>, month: MonthRange): List<BudgetUsage> {
        val expenses = transactions.filter { it.kind == TransactionKind.EXPENSE && it.date in month }
        return budgets.map { budget ->
            val spent = expenses.filter { budget.isGeneral || it.categoryId == budget.categoryId }.sumOf { it.amount }
            BudgetUsage(budget, spent)
        }
    }

    /** The general limit's usage, if it is switched on and has a limit. */
    fun generalUsage(usages: List<BudgetUsage>): BudgetUsage? =
        usages.firstOrNull { it.budget.isGeneral && it.budget.isActive && it.budget.monthlyLimit.signum() > 0 }

    /**
     * Budgets that reached their limit and weren't notified about yet this month get an alert and
     * [now] as their alert date. A budget notified this month that is no longer reached (an expense
     * was edited or deleted) has its alert date cleared, so reaching the limit again alerts again.
     */
    fun check(usages: List<BudgetUsage>, month: MonthRange, now: Instant): BudgetCheck {
        val alerts = mutableListOf<BudgetUsage>()
        val updated = mutableListOf<Budget>()
        for (usage in usages) {
            val budget = usage.budget
            val wasAlerted = budget.lastAlertDate?.let { it in month } ?: false
            if (usage.isReached && !wasAlerted) {
                val alerted = budget.copy(lastAlertDate = now)
                updated += alerted
                alerts += usage.copy(budget = alerted)
            } else if (!usage.isReached && wasAlerted) {
                updated += budget.copy(lastAlertDate = null)
            }
        }
        return BudgetCheck(alerts, updated)
    }

    fun hint(ratio: Double): BudgetHint = when {
        ratio < 0.25 -> BudgetHint.WELL_UNDER
        ratio < 0.5 -> BudgetHint.UNDER_HALF
        ratio < 0.75 -> BudgetHint.OVER_HALF
        ratio < 0.9 -> BudgetHint.GETTING_CLOSE
        ratio < 1 -> BudgetHint.ALMOST
        ratio < 1.1 -> BudgetHint.REACHED
        else -> BudgetHint.OVER
    }

    fun level(usage: BudgetUsage): BudgetLevel = when {
        !usage.budget.isActive -> BudgetLevel.INACTIVE
        usage.ratio < 0.8 -> BudgetLevel.FINE
        usage.ratio < 1 -> BudgetLevel.CLOSE
        else -> BudgetLevel.OVER
    }
}
