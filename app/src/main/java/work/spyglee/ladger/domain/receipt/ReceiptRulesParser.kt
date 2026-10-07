package work.spyglee.ladger.domain.receipt

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import work.spyglee.ladger.domain.lineBreak

data class ParsedReceipt(
    val total: BigDecimal? = null,
    val date: LocalDate? = null,
    val merchant: String? = null,
    val categoryName: String? = null,
)

/** Raw values produced by the on-device language model, before validation. */
data class GeneratedReceipt(
    val merchant: String?,
    val total: String?,
    val date: String?,
    val category: String?,
)

/**
 * Rule-based receipt parsing for Croatian, English, Ukrainian and Polish receipts. Used on its own
 * when no on-device language model is available, and merged with the model's answer otherwise.
 */
object ReceiptRulesParser {
    /** Labels of the final amount. Matched as whole words, so "total" doesn't match "subtotal". */
    private val totalKeywords = wordPatterns(
        // Croatian / English
        "za platiti", "za uplatu", "sveukupno", "ukupno", "total",
        // Ukrainian
        "до сплати", "всього", "разом", "сума",
        // Polish, with and without diacritics, as OCR often drops them
        "do zapłaty", "do zaplaty", "razem", "suma",
    )

    /**
     * VAT summary lines, which often repeat a smaller "total". These are word stems: they must start
     * a word but may continue, so "porez" also matches "poreza", while "vat" no longer matches
     * inside "Hrvatska" (the iOS app matches substrings and skips such lines).
     */
    private val excludedStems = stemPatterns(
        // Croatian
        "pdv", "porez", "osnovic",
        // Ukrainian
        "пдв", "податок", "податк",
        // Polish
        "ptu", "vat", "podatek", "podatk", "opodatk",
    )

    private val amountPattern = Regex("""\d{1,3}(?:[ .,]?\d{3})*[.,]\d{2}(?!\d)""")
    private val datePattern = Regex("""(\d{1,2})\.\s?(\d{1,2})\.\s?(\d{4})""")

    fun parse(text: String): ParsedReceipt {
        val lines = text.lowercase().split(lineBreak)
        val merchant = text.split(lineBreak)
            .map { it.trim(' ', '\t') }
            .firstOrNull { line -> line.any(Char::isLetter) }
        return ParsedReceipt(total = findTotal(lines), date = findDate(text), merchant = merchant)
    }

    /**
     * Combines model output with the rule-based result. The model can latch onto a smaller "total"
     * (e.g. from a VAT table), so the larger of the two totals wins. Other model values win when
     * valid, and the category is only kept if it is one of the user's [categoryNames].
     */
    fun merge(fallback: ParsedReceipt, generated: GeneratedReceipt?, categoryNames: List<String>): ParsedReceipt {
        if (generated == null) return fallback
        val merchant = generated.merchant?.trim().orEmpty()
        return ParsedReceipt(
            total = listOfNotNull(generated.total?.let(::decimal), fallback.total).maxOrNull(),
            date = generated.date?.let(::isoDate) ?: fallback.date,
            merchant = merchant.ifEmpty { fallback.merchant },
            categoryName = generated.category?.takeIf { it in categoryNames },
        )
    }

    /**
     * The largest amount on any total line (or on the line after it, when the label stands alone),
     * so a smaller total from a VAT table printed after the final amount is never picked.
     */
    private fun findTotal(lines: List<String>): BigDecimal? =
        lines.withIndex()
            .filter { (_, line) ->
                val words = keywordView(line)
                totalKeywords.any { it.containsMatchIn(words) } && excludedStems.none { it.containsMatchIn(words) }
            }
            .mapNotNull { (index, line) -> lastAmount(line) ?: lines.getOrNull(index + 1)?.let(::lastAmount) }
            .maxOrNull()

    /**
     * The line as used for finding keywords: OCR often reads the letter O as a zero in receipt fonts
     * ("UKUPN0", "T0TAL"), so a zero touching a letter counts as an "o". Amounts are read from the
     * original line.
     */
    private fun keywordView(line: String): String = zeroInWord.replace(line, "o")

    private val zeroInWord = Regex("""(?<=\p{L})0|0(?=\p{L})""")

    private fun lastAmount(line: String): BigDecimal? =
        amountPattern.findAll(line).lastOrNull()?.let { decimal(it.value) }

    private fun findDate(text: String): LocalDate? {
        val match = datePattern.find(text) ?: return null
        val (day, month, year) = match.destructured
        return try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * Reads an amount whose last `.` or `,` is the decimal separator, so "1.234,56", "1,234.56"
     * and "1234.56" all give 1234.56. Only positive values are accepted.
     */
    fun decimal(string: String): BigDecimal? {
        var number = string.filter { it.isDigit() || it == ',' || it == '.' }
        val separator = number.indexOfLast { it == ',' || it == '.' }
        if (separator >= 0) {
            val integer = number.substring(0, separator).filter(Char::isDigit)
            val fraction = number.substring(separator + 1)
            number = "$integer.$fraction"
        }
        return number.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }

    /** Parses the model's `YYYY-MM-DD` date. */
    fun isoDate(string: String): LocalDate? {
        val parts = string.split("-").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 3) return null
        return try {
            LocalDate.of(parts[0], parts[1], parts[2])
        } catch (_: DateTimeException) {
            null
        }
    }

    // A keyword must not be glued to letters on either side; digits and punctuation are fine ("PDV13%").
    private fun wordPatterns(vararg words: String) =
        words.map { Regex("""(?<!\p{L})${Regex.escape(it)}(?!\p{L})""") }

    private fun stemPatterns(vararg stems: String) =
        stems.map { Regex("""(?<!\p{L})${Regex.escape(it)}""") }
}
