package work.spyglee.ladger.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.domain.assertAmount

class ReceiptItemsTest {
    @Test
    fun `reads one product per line`() {
        val items = ReceiptItems.parseResponse("MLIJEKO 2,8% 1L | 2 | 1.29 | 2.58\nKRUH POLUBIJELI | 1 | 1.49 | 1.49")
        assertEquals(listOf("MLIJEKO 2,8% 1L", "KRUH POLUBIJELI"), items.map { it.name })
        assertAmount("2", items[0].quantity)
        assertAmount("1.29", items[0].unitPrice)
        assertAmount("2.58", items[0].totalPrice)
    }

    @Test
    fun `skips fences, headers, table rules and chatter`() {
        val reply = """
            Here are the products:
            ```
            | name | quantity | unit price | line total |
            |---|---|---|---|
            | BANANE | 0,456 | 1,99 | 0,91 |
            - JOGURT | 1 | 0.89 | 0.89
            UKUPNO | | | 1.80
            ```
        """.trimIndent()
        val items = ReceiptItems.parseResponse(reply)
        // The UKUPNO row looks like a product to the parser; the instructions keep it out of the reply.
        assertEquals(listOf("BANANE", "JOGURT", "UKUPNO"), items.map { it.name })
        assertAmount("0.456", items[0].quantity)
        assertAmount("0.91", items[0].totalPrice)
    }

    @Test
    fun `fills in a missing unit price or total`() {
        val noUnit = ReceiptItems.item("SIR", "2", "", "5.00")
        assertAmount("2.50", noUnit?.unitPrice)
        val noTotal = ReceiptItems.item("SIR", "3", "1.10", "")
        assertAmount("3.30", noTotal?.totalPrice)
        val noQuantity = ReceiptItems.item("SIR", "", "", "4.20")
        assertAmount("1", noQuantity?.quantity)
        assertAmount("4.20", noQuantity?.unitPrice)
    }

    @Test
    fun `a line needs a name with letters and a price`() {
        assertNull(ReceiptItems.item("12345", "1", "1.00", "1.00"))
        assertNull(ReceiptItems.item("SIR", "1", "", ""))
        assertTrue(ReceiptItems.parseResponse(null).isEmpty())
        assertTrue(ReceiptItems.parseResponse("SIR | 1 | 1.00").isEmpty())
    }

    @Test
    fun `at most 80 items`() {
        val reply = (1..100).joinToString("\n") { "ARTIKL $it | 1 | 1.00 | 1.00" }
        assertEquals(ReceiptItems.MAX_ITEMS, ReceiptItems.parseResponse(reply).size)
    }

    @Test
    fun `names are grouped ignoring case, diacritics and spacing`() {
        assertEquals("mlijeko 1l", ReceiptItems.normalize("  MLIJEKO \t 1L "))
        assertEquals(ReceiptItems.normalize("Čokolada  Đumbir"), ReceiptItems.normalize("cokolada dumbir"))
    }
}
