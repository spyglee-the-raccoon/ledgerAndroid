package work.spyglee.ladger.domain.receipt

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import work.spyglee.ladger.domain.assertAmount
import work.spyglee.ladger.domain.date

class ReceiptRulesParserTest {
    private val konzumReceipt = """
        KONZUM plus d.o.o.
        Marijana Čavića 1a, Zagreb
        OIB: 62226620908
        Račun br: 1234/5/6
        Datum: 03.10.2026. 14:22
        MLIJEKO 2,8% 1L        1,29
        KRUH BIJELI            2,10
        SIR GAUDA              12,50
        UKUPNO EUR             15,89
        PDV 13%  osnovica 14,06  porez 1,83
        ZA PLATITI:
        15,89
        Gotovina               20,00
    """.trimIndent()

    @Test
    fun `reads total from the line after the keyword, date and merchant`() {
        val receipt = ReceiptRulesParser.parse(konzumReceipt)
        assertAmount("15.89", receipt.total)
        assertEquals(date(2026, 10, 3), receipt.date)
        assertEquals("KONZUM plus d.o.o.", receipt.merchant)
        assertNull(receipt.categoryName)
    }

    @Test
    fun `skips VAT summary lines`() {
        val receipt = ReceiptRulesParser.parse("LIDL\nUkupno PDV          2,00\nUKUPNO              24,99")
        assertAmount("24.99", receipt.total)
    }

    @Test
    fun `largest total wins over a smaller total in a VAT table`() {
        val receipt = ReceiptRulesParser.parse(
            """
            LIDL
            UKUPNO              24,99
            PDV 25%  osnovica 19,99  porez 5,00
            TOTAL               19,99
            """.trimIndent()
        )
        assertAmount("24.99", receipt.total)
    }

    @Test
    fun `keywords match whole words only`() {
        // "vat" inside "Hrvatska" must not mark the line as a VAT line (it does on iOS).
        assertAmount("15.89", ReceiptRulesParser.parse("Konzum\nUKUPNO KONZUM HRVATSKA 15,89").total)
        // "total" inside "subtotal" is not a total label.
        assertAmount("12.00", ReceiptRulesParser.parse("Shop\nSUBTOTAL 99,00\nTOTAL 12,00").total)
        // VAT stems still match when glued to digits or followed by an inflection.
        assertAmount("12.00", ReceiptRulesParser.parse("Shop\nUKUPNO PDV13% 50,00\nUKUPNO 12,00").total)
        assertAmount("12.00", ReceiptRulesParser.parse("Shop\nUkupno poreza 50,00\nZA PLATITI 12,00").total)
    }

    @Test
    fun `labels where OCR read the letter O as a zero still match`() {
        // Seen with ML Kit on a monospace receipt font.
        assertAmount("12.49", ReceiptRulesParser.parse("KONZUM\nUKUPN0 EUR 12,49\nPDV 25% 2,50").total)
        assertAmount("8.10", ReceiptRulesParser.parse("Shop\nT0TAL 8,10").total)
        // Amounts with zeros are untouched.
        assertAmount("100.00", ReceiptRulesParser.parse("Shop\nUKUPNO 100,00").total)
    }

    @Test
    fun `reads Ukrainian receipts`() {
        val receipt = ReceiptRulesParser.parse(
            """
            ТОВ "Сільпо-Фуд"
            Хліб            32,50
            Молоко          45,90
            СУМА            78,40
            ПДВ А 20%       13,07
            Сума ПДВ        13,07
            """.trimIndent()
        )
        assertAmount("78.40", receipt.total)
        assertEquals("ТОВ \"Сільпо-Фуд\"", receipt.merchant)
    }

    @Test
    fun `reads Polish receipts with and without diacritics`() {
        val receipt = """
            Biedronka
            SPRZEDAŻ OPODATKOWANA A   23,50
            PTU A 23%   4,39
            SUMA PTU    4,39
            SUMA PLN    23,50
        """.trimIndent()
        assertAmount("23.50", ReceiptRulesParser.parse(receipt).total)
        assertAmount("23.50", ReceiptRulesParser.parse("Zabka\nDO ZAPLATY\n23,50").total)
        assertAmount("23.50", ReceiptRulesParser.parse("Zabka\nDo zapłaty 23,50").total)
    }

    @Test
    fun `reads thousands separators`() {
        assertAmount("1234.56", ReceiptRulesParser.parse("Sveukupno 1.234,56 EUR").total)
    }

    @Test
    fun `missing or invalid values are null`() {
        val receipt = ReceiptRulesParser.parse("12345\nnothing here 45.13.2026")
        assertNull(receipt.total)
        assertNull(receipt.date)
        assertEquals("nothing here 45.13.2026", receipt.merchant)
    }

    @Test
    fun `decimal uses the last separator as the decimal point`() {
        assertAmount("1234.56", ReceiptRulesParser.decimal("1.234,56"))
        assertAmount("1234.56", ReceiptRulesParser.decimal("1,234.56"))
        assertAmount("12.49", ReceiptRulesParser.decimal("12.49"))
        assertAmount("1249", ReceiptRulesParser.decimal("1249"))
        assertNull(ReceiptRulesParser.decimal("0,00"))
        assertNull(ReceiptRulesParser.decimal("abc"))
    }

    @Test
    fun `iso date from the model`() {
        assertEquals(date(2026, 10, 3), ReceiptRulesParser.isoDate("2026-10-03"))
        assertNull(ReceiptRulesParser.isoDate(""))
        assertNull(ReceiptRulesParser.isoDate("2026-13-01"))
    }

    @Test
    fun `merge prefers valid model values and keeps only known categories`() {
        val fallback = ReceiptRulesParser.parse(konzumReceipt)
        val categories = listOf("Groceries", "Transport")

        assertEquals(fallback, ReceiptRulesParser.merge(fallback, null, categories))

        val merged = ReceiptRulesParser.merge(
            fallback,
            GeneratedReceipt(merchant = "  Konzum ", total = "16.20", date = "", category = "Groceries"),
            categories,
        )
        assertAmount("16.20", merged.total)
        assertEquals(date(2026, 10, 3), merged.date)
        assertEquals("Konzum", merged.merchant)
        assertEquals("Groceries", merged.categoryName)

        val invented = ReceiptRulesParser.merge(
            fallback,
            GeneratedReceipt(merchant = "", total = "n/a", date = "2026-10-04", category = "Dining"),
            categories,
        )
        assertAmount("15.89", invented.total)
        assertEquals(date(2026, 10, 4), invented.date)
        assertEquals("KONZUM plus d.o.o.", invented.merchant)
        assertNull(invented.categoryName)
    }

    @Test
    fun `merge keeps the larger total when the model picks a VAT subtotal`() {
        val fallback = ReceiptRulesParser.parse(konzumReceipt)
        val merged = ReceiptRulesParser.merge(
            fallback,
            GeneratedReceipt(merchant = "Konzum", total = "14.06", date = null, category = null),
            emptyList(),
        )
        assertAmount("15.89", merged.total)
    }

    @Test
    fun `reads the time next to the date`() {
        assertEquals(LocalTime.of(14, 22), ReceiptRulesParser.parse(konzumReceipt).time)
        assertEquals(LocalTime.of(9, 7), ReceiptRulesParser.parse("Store\n05.10.2026. 09:07").time)
    }

    @Test
    fun `prefers the time labeled vrijeme, also on the next line`() {
        val receipt = ReceiptRulesParser.parse("Konzum\nBlagajna 10:15\nDatum: 05.10.2026.\nVrijeme: 14:35:12\nUKUPNO 15,89")
        assertEquals(LocalTime.of(14, 35), receipt.time)
        assertEquals(LocalTime.of(18, 3), ReceiptRulesParser.parse("Biedronka 11:00\nGodz.\n18:03").time)
    }

    @Test
    fun `ignores opening hours and impossible times`() {
        val receipt = ReceiptRulesParser.parse("Store\nRadno vrijeme 08:00-21:00\n05.10.2026. 18:42")
        assertEquals(LocalTime.of(18, 42), receipt.time)
        assertNull(ReceiptRulesParser.parse("Store\n05.10.2026.\nKasa 25:61").time)
    }

    @Test
    fun `merge prefers a valid model time`() {
        val fallback = ReceiptRulesParser.parse(konzumReceipt)
        fun mergedTime(time: String?) =
            ReceiptRulesParser.merge(fallback, GeneratedReceipt("Konzum", null, null, null, time), emptyList()).time
        assertEquals(LocalTime.of(14, 30), mergedTime("14:30"))
        assertEquals(LocalTime.of(14, 22), mergedTime(""))
        assertEquals(LocalTime.of(14, 22), mergedTime("99:99"))
    }
}
