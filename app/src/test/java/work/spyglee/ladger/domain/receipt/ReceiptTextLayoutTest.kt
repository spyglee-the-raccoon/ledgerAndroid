package work.spyglee.ladger.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTextLayoutTest {
    private fun line(text: String, left: Int, top: Int, width: Int = 200, height: Int = 30) =
        TextLine(text, left, top, left + width, top + height)

    @Test
    fun `labels and right-aligned amounts from separate blocks end up on one line`() {
        // ML Kit returns the label column as one block and the amount column as another.
        val lines = listOf(
            line("KONZUM d.d.", 100, 50),
            line("Mlijeko", 100, 200),
            line("UKUPNO", 100, 300),
            line("PDV 25%", 100, 400),
            line("1,49", 700, 202),
            line("12,49", 700, 298),
            line("2,50", 700, 405),
        )
        assertEquals("KONZUM d.d.\nMlijeko 1,49\nUKUPNO 12,49\nPDV 25% 2,50", ReceiptTextLayout.toText(lines))
        assertEquals(BigDecimalOf("12.49"), ReceiptRulesParser.parse(ReceiptTextLayout.toText(lines)).total)
    }

    @Test
    fun `slightly tilted rows stay together and neighbouring rows stay apart`() {
        val lines = listOf(
            line("UKUPNO", 100, 300),
            line("12,49", 700, 310),
            line("Gotovina", 100, 345),
            line("20,00", 700, 352),
        )
        assertEquals("UKUPNO 12,49\nGotovina 20,00", ReceiptTextLayout.toText(lines))
    }

    @Test
    fun `blank lines are dropped and empty input gives empty text`() {
        assertEquals("A", ReceiptTextLayout.toText(listOf(line(" ", 0, 0), line("A", 0, 100))))
        assertEquals("", ReceiptTextLayout.toText(emptyList()))
    }

    @Suppress("TestFunctionName")
    private fun BigDecimalOf(value: String) = java.math.BigDecimal(value)
}
