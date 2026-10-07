package work.spyglee.ladger.domain

import java.math.BigDecimal
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

object Balances {
    /** Sum of all starting values plus incomes minus expenses. Transfers move money between sources and net to zero. */
    fun total(sources: List<Source>, transactions: List<Transaction>): BigDecimal =
        sources.sumOf { it.initValue } + net(transactions)

    /** Incomes minus expenses, e.g. the month balance shown in the middle of the donut chart. */
    fun net(transactions: List<Transaction>): BigDecimal =
        sum(transactions, TransactionKind.INCOME) - sum(transactions, TransactionKind.EXPENSE)

    fun sum(transactions: List<Transaction>, kind: TransactionKind): BigDecimal =
        transactions.filter { it.kind == kind }.sumOf { it.amount }

    /**
     * Balance of one source: its starting value, plus money coming in (incomes and transfers to it),
     * minus money going out (expenses and transfers from it). A null [sourceId] gives the balance of
     * transactions that have no source assigned.
     */
    fun forSource(sourceId: Long?, initValue: BigDecimal, transactions: List<Transaction>): BigDecimal {
        val incoming = transactions
            .filter { it.kind != TransactionKind.EXPENSE && it.destinationId == sourceId }
            .sumOf { it.amount }
        val outgoing = transactions
            .filter { it.kind != TransactionKind.INCOME && it.sourceId == sourceId }
            .sumOf { it.amount }
        return initValue + incoming - outgoing
    }

    /** True when a transaction is missing the source or destination its kind needs. */
    fun isUnassigned(transaction: Transaction): Boolean =
        (transaction.kind != TransactionKind.INCOME && transaction.sourceId == null) ||
            (transaction.kind != TransactionKind.EXPENSE && transaction.destinationId == null)
}
