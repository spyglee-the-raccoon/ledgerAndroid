package work.spyglee.ladger.domain.receipt

import java.math.BigDecimal
import java.math.RoundingMode
import work.spyglee.ladger.domain.folded

/** A purchased product line read from a receipt; [unitPrice] is per piece or per kg. */
data class ParsedReceiptItem(
    val name: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
)

/**
 * The second model request, which lists the purchased products (iOS `ReceiptParser.parseItems`). It is
 * separate so a long item list can't push the total, date and category out of the model's context.
 * iOS gets typed output from a schema; here the model writes one `name | quantity | unit price | total`
 * line per product, which needs far fewer output tokens than JSON and no JSON parser.
 */
object ReceiptItems {
    const val MAX_ITEMS = 80

    val instructions: String = """
        You list the purchased products from OCR text of shopping receipts. The text may contain OCR errors. Only use values that appear in the text. Numbers often use a decimal comma (1,29). A product line usually shows the name, then quantity × unit price and the line total; weighed goods show the weight in kg as the quantity. Skip totals, subtotals, VAT/tax (PDV, ПДВ, PTU) tables, payment, change, card, loyalty points and store information lines. If a discount line directly follows a product, subtract it from that product's line total.
        Reply with only one line per purchased product, in receipt order, at most $MAX_ITEMS lines, formatted as: name | quantity | unit price | line total
        name: the product name exactly as printed, without quantity or price, e.g. MLIJEKO 2,8% 1L. quantity: number of pieces or weight in kg with a dot as decimal separator, e.g. 2 or 0.456; 1 if not printed. unit price: price of one piece or one kg with a dot as decimal separator, e.g. 1.29; empty if not printed. line total: total paid for this product with a dot as decimal separator, e.g. 2.58.
        Example: MLIJEKO 2,8% 1L | 2 | 1.29 | 2.58
    """.trimIndent()

    fun prompt(receiptText: String): String = ReceiptPrompt.prompt(receiptText)

    /** The products in the model's reply; lines that aren't a product (headers, fences, chatter) are skipped. */
    fun parseResponse(response: String?): List<ParsedReceiptItem> =
        response.orEmpty()
            .lineSequence()
            .mapNotNull(::parseLine)
            .take(MAX_ITEMS)
            .toList()

    private fun parseLine(line: String): ParsedReceiptItem? {
        // Markdown tables wrap rows in pipes: "| name | 1 | 2.00 | 2.00 |".
        val columns = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
        if (columns.size != 4) return null
        val (name, quantity, unitPrice, totalPrice) = columns
        return item(name, quantity, unitPrice, totalPrice)
    }

    /** Validates one line and fills in a missing unit price or total, as iOS does. */
    fun item(name: String, quantity: String, unitPrice: String, totalPrice: String): ParsedReceiptItem? {
        val trimmedName = name.trim().removePrefix("-").removePrefix("*").trim()
        if (trimmedName.none(Char::isLetter)) return null
        val count = ReceiptRulesParser.decimal(quantity) ?: BigDecimal.ONE
        val unit = ReceiptRulesParser.decimal(unitPrice)
        val total = ReceiptRulesParser.decimal(totalPrice) ?: unit?.multiply(count) ?: return null
        return ParsedReceiptItem(
            name = trimmedName,
            quantity = count,
            unitPrice = unit ?: total.divide(count, 4, RoundingMode.HALF_UP).stripTrailingZeros(),
            totalPrice = total,
        )
    }

    /**
     * Grouping key for price comparison: case, diacritics and spacing ignored, so OCR differences such
     * as "Mlijeko  1L" and "MLIJEKO 1l" count as the same product.
     */
    fun normalize(name: String): String = name.folded().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}
