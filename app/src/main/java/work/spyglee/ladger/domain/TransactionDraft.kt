package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.time.Instant
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

/** The editable state of the add/edit transaction form. */
data class TransactionDraft(
    val amount: BigDecimal? = null,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val sourceId: Long? = null,
    val destinationId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val date: Instant = Instant.now(),
    /** Kept when editing a transaction a plan recorded, so it stays linked to the plan. */
    val plannedTransactionId: Long? = null,
) {
    /** Expenses need a source and category, incomes a destination and category, transfers both sources. */
    val isValid: Boolean
        get() {
            if (amount == null || amount.signum() <= 0) return false
            return when (kind) {
                TransactionKind.EXPENSE -> categoryId != null && sourceId != null
                TransactionKind.INCOME -> categoryId != null && destinationId != null
                TransactionKind.TRANSFER -> sourceId != null && destinationId != null
            }
        }

    /**
     * Switches the kind and clears what the new kind doesn't use: incomes have no source, expenses
     * no destination, and a category is kept only if it belongs to the new kind (transfers have none).
     */
    fun withKind(newKind: TransactionKind, categories: List<Category>): TransactionDraft {
        val categoryKind = newKind.categoryKind
        val keepCategory = categoryKind != null && categories.any { it.id == categoryId && it.kind == categoryKind }
        return copy(
            kind = newKind,
            sourceId = sourceId.takeIf { newKind != TransactionKind.INCOME },
            destinationId = destinationId.takeIf { newKind != TransactionKind.EXPENSE },
            categoryId = categoryId.takeIf { keepCategory },
        )
    }

    /** Only call when [isValid]. */
    fun toTransaction(id: Long = 0): Transaction = Transaction(
        id = id,
        amount = checkNotNull(amount),
        kind = kind,
        sourceId = sourceId.takeIf { kind != TransactionKind.INCOME },
        destinationId = destinationId.takeIf { kind != TransactionKind.EXPENSE },
        note = note,
        date = date,
        categoryId = categoryId.takeIf { kind != TransactionKind.TRANSFER },
        plannedTransactionId = plannedTransactionId,
    )

    companion object {
        fun from(transaction: Transaction) = TransactionDraft(
            amount = transaction.amount,
            kind = transaction.kind,
            sourceId = transaction.sourceId,
            destinationId = transaction.destinationId,
            categoryId = transaction.categoryId,
            note = transaction.note,
            date = transaction.date,
            plannedTransactionId = transaction.plannedTransactionId,
        )
    }
}

/** The category kind a transaction kind uses; transfers have no category. */
val TransactionKind.categoryKind: CategoryKind?
    get() = when (this) {
        TransactionKind.INCOME -> CategoryKind.INCOME
        TransactionKind.EXPENSE -> CategoryKind.EXPENSE
        TransactionKind.TRANSFER -> null
    }

/** Parses and formats amounts typed into a text field. */
object AmountInput {
    private val pattern = Regex("""-?\d*[.,]?\d*""")

    /** Accepts a comma or a dot as decimal separator, e.g. "12,49" or "12.49". */
    fun parse(text: String, allowNegative: Boolean = false): BigDecimal? {
        val trimmed = text.trim()
        if (!pattern.matches(trimmed) || trimmed.none(Char::isDigit)) return null
        if (!allowNegative && trimmed.startsWith("-")) return null
        return trimmed.replace(',', '.').toBigDecimalOrNull()
    }

    fun format(value: BigDecimal, decimalSeparator: Char): String =
        value.stripTrailingZeros().toPlainString().replace('.', decimalSeparator)
}
