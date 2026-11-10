package work.spyglee.ladger.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.ReceiptItemName

class TransactionSearchTest {
    private val categories = mapOf(7L to Category(id = 7, name = "Hrana"))
    private val itemNames = listOf(ReceiptItemName(1, "MLIJEKO 2,8% 1L"), ReceiptItemName(1, "ČOKOLADA"), ReceiptItemName(2, "KRUH")).byTransaction()
    private val konzum = expense("10", categoryId = 7, id = 1).copy(note = "Konzum")
    private val bakery = expense("2", id = 2).copy(note = "Pekara")

    private fun search(query: String) = listOf(konzum, bakery).filter { it.matches(query, categories, itemNames) }.map { it.id }

    @Test
    fun `matches note, category and receipt items ignoring case and diacritics`() {
        assertEquals(listOf(1L), search("konz"))
        assertEquals(listOf(1L), search("hrana"))
        assertEquals(listOf(1L), search("mlijeko"))
        assertEquals(listOf(1L), search("cokolada"))
        assertEquals(listOf(2L), search(" kruh "))
        assertEquals(listOf(1L, 2L), search(""))
        assertEquals(emptyList<Long>(), search("sir"))
    }
}
