package work.spyglee.ladger.domain.receipt

import java.math.BigDecimal
import java.math.RoundingMode
import work.spyglee.ladger.domain.AmountInput

/**
 * The fields of the receipt item editor as typed, as iOS's `ReceiptItemEditorView` (since `8a76b5d`):
 * a product line added by hand or one that was recognized incorrectly. Changing the quantity or
 * unit price sets the total to quantity × unit price; the total can still be changed afterwards,
 * e.g. for a discounted line.
 */
data class ReceiptItemDraft(
    val name: String = "",
    val quantityText: String = "1",
    val unitPriceText: String = "",
    val totalText: String = "",
) {
    val quantity: BigDecimal? get() = AmountInput.parse(quantityText)
    val total: BigDecimal? get() = AmountInput.parse(totalText)

    /** An empty unit price is zero, as a new item starts on iOS. */
    val unitPrice: BigDecimal? get() = if (unitPriceText.isBlank()) BigDecimal.ZERO else AmountInput.parse(unitPriceText)

    val isValid: Boolean
        get() = name.isNotBlank() && (quantity?.signum() ?: 0) > 0 && unitPrice != null && (total?.signum() ?: -1) >= 0

    fun withQuantity(text: String, decimalSeparator: Char): ReceiptItemDraft = copy(quantityText = text).withLineTotal(decimalSeparator)

    fun withUnitPrice(text: String, decimalSeparator: Char): ReceiptItemDraft = copy(unitPriceText = text).withLineTotal(decimalSeparator)

    private fun withLineTotal(decimalSeparator: Char): ReceiptItemDraft {
        val quantity = quantity ?: return this
        val unitPrice = AmountInput.parse(unitPriceText) ?: return this
        return copy(totalText = AmountInput.format(lineTotal(quantity, unitPrice), decimalSeparator))
    }

    /** The edited item with a trimmed name, or null while the fields are invalid. */
    fun toItem(): ParsedReceiptItem? {
        if (!isValid) return null
        return ParsedReceiptItem(name.trim(), quantity!!, unitPrice!!, total!!)
    }

    companion object {
        /** Quantity × unit price rounded to cents, as iOS's `NSDecimalRound(…, 2, .plain)`. */
        fun lineTotal(quantity: BigDecimal, unitPrice: BigDecimal): BigDecimal =
            (quantity * unitPrice).setScale(2, RoundingMode.HALF_UP)

        fun from(item: ParsedReceiptItem, decimalSeparator: Char) = ReceiptItemDraft(
            name = item.name,
            quantityText = AmountInput.format(item.quantity, decimalSeparator),
            unitPriceText = AmountInput.format(item.unitPrice, decimalSeparator),
            totalText = AmountInput.format(item.totalPrice, decimalSeparator),
        )
    }
}
