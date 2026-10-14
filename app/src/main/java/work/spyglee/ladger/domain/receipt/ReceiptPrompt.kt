package work.spyglee.ladger.domain.receipt

/**
 * The request sent to the on-device language model (Gemini Nano) and how its answer is read.
 * The instructions are the iOS app's; iOS gets typed output from a schema, here the model is asked
 * for a JSON object, read leniently because small models sometimes wrap it in extra text.
 */
object ReceiptPrompt {
    /** Gemini Nano accepts about 4000 tokens; receipts are far shorter, but OCR of a long PDF might not be. */
    private const val MAX_TEXT_LENGTH = 6_000

    fun instructions(categoryNames: List<String>): String {
        val categories = categoryNames.distinct().sorted()
        val categoryField = if (categories.isEmpty()) {
            ""
        } else {
            ", \"category\": the spending category that best matches the purchased items and the store, exactly one of " +
                categories.joinToString(", ") { "\"$it\"" }
        }
        return """
            You extract data from OCR text of shopping receipts for a personal budgeting app. The text may contain OCR errors. Only use values that appear in the text. Receipts are often Croatian: the final amount is labeled UKUPNO, SVEUKUPNO, ZA PLATITI, ZA UPLATU or TOTAL, numbers use a decimal comma (12,49), dates are written as DD.MM.YYYY and the purchase time is labeled VRIJEME and written as HH:MM or HH:MM:SS. Ukrainian receipts label the final amount СУМА, ВСЬОГО, РАЗОМ or ДО СПЛАТИ and VAT as ПДВ. Polish receipts label the final amount SUMA, SUMA PLN, RAZEM or DO ZAPŁATY and VAT as PTU or VAT. Ignore VAT (PDV, ПДВ, PTU) summary lines when choosing the total. A VAT table with its own TOTAL, UKUPNO, СУМА or SUMA row often follows the final amount. If several amounts are labeled as a total, return the largest one. Pick the category from the purchased items and the type of store.
            Reply with only a JSON object with these string fields: "merchant": the store brand name printed at the top of the receipt, without the legal form (d.o.o., d.d.) or address, e.g. Konzum; "total": the final amount paid including tax, digits only with a dot as decimal separator, e.g. 12.49; "date": the purchase date as YYYY-MM-DD, or an empty string if not printed; "time": the purchase time as HH:MM in 24-hour format, e.g. 14:35, often labeled VRIJEME on Croatian receipts, or an empty string if not printed$categoryField.
        """.trimIndent()
    }

    fun prompt(receiptText: String): String = "Receipt text:\n${receiptText.take(MAX_TEXT_LENGTH)}"

    /** Reads the model's reply; null when it holds no usable JSON object. */
    fun parseResponse(response: String?): GeneratedReceipt? {
        if (response == null) return null
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = response.substring(start, end + 1)
        val receipt = GeneratedReceipt(
            merchant = field(json, "merchant"),
            total = field(json, "total"),
            date = field(json, "date"),
            category = field(json, "category"),
            time = field(json, "time"),
        )
        return receipt.takeIf { listOf(it.merchant, it.total, it.date, it.category, it.time).any { value -> value != null } }
    }

    /** A string or number value of a top-level key; the fields are flat, so no full JSON parser is needed. */
    private fun field(json: String, key: String): String? {
        val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?[0-9]+(?:[.,][0-9]+)*))").find(json) ?: return null
        val string = match.groups[1]?.value
        return string?.let(::unescape) ?: match.groups[2]?.value
    }

    private fun unescape(value: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n' -> result.append('\n')
                    't' -> result.append('\t')
                    'u' -> if (i + 5 < value.length) {
                        value.substring(i + 2, i + 6).toIntOrNull(16)?.let { result.append(it.toChar()) }
                        i += 4
                    }
                    else -> result.append(next)
                }
                i += 2
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }
}
