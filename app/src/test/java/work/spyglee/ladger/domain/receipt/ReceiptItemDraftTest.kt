package work.spyglee.ladger.domain.receipt

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.domain.assertAmount

class ReceiptItemDraftTest {
    @Test
    fun `quantity and unit price set the total, rounded to cents`() {
        val draft = ReceiptItemDraft(name = "Banane")
            .withQuantity("0,456", ',')
            .withUnitPrice("1,99", ',')
        assertEquals("0,91", draft.totalText) // 0.90744
        assertEquals("3", draft.withQuantity("2", ',').withUnitPrice("1.5", ',').totalText)
    }

    @Test
    fun `the total can be changed afterwards`() {
        val draft = ReceiptItemDraft(name = "Milk").withQuantity("2", '.').withUnitPrice("1.29", '.').copy(totalText = "2.00")
        assertAmount("2", draft.toItem()!!.totalPrice)
    }

    @Test
    fun `an unreadable quantity or unit price leaves the total alone`() {
        val draft = ReceiptItemDraft(name = "Milk", totalText = "4").withQuantity("abc", '.')
        assertEquals("4", draft.totalText)
        assertEquals("4", draft.withQuantity("2", '.').copy(totalText = "4").withUnitPrice("x", '.').totalText)
    }

    @Test
    fun `needs a name, a positive quantity and a total of at least zero`() {
        val valid = ReceiptItemDraft(name = " Kruh ", quantityText = "1", unitPriceText = "", totalText = "0")
        assertTrue(valid.isValid)
        assertEquals(ParsedReceiptItem("Kruh", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO), valid.toItem())

        assertFalse(valid.copy(name = "  ").isValid)
        assertFalse(valid.copy(quantityText = "0").isValid)
        assertFalse(valid.copy(quantityText = "").isValid)
        assertFalse(valid.copy(totalText = "").isValid)
        assertFalse(valid.copy(totalText = "-1").isValid)
        assertFalse(valid.copy(unitPriceText = "x").isValid)
        assertNull(valid.copy(name = "").toItem())
    }

    @Test
    fun `starts from an item with the local decimal separator`() {
        val draft = ReceiptItemDraft.from(ParsedReceiptItem("Mlijeko", BigDecimal("2"), BigDecimal("1.290"), BigDecimal("2.58")), ',')
        assertEquals(ReceiptItemDraft("Mlijeko", "2", "1,29", "2,58"), draft)
    }
}
