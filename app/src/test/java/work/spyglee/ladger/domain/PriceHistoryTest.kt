package work.spyglee.ladger.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.DatedReceiptItem
import work.spyglee.ladger.domain.receipt.ReceiptItems

class PriceHistoryTest {
    private var nextId = 0L

    /** One purchase a day, later than every purchase made before, with the given unit prices. */
    private fun purchases(name: String, vararg prices: String): List<DatedReceiptItem> =
        prices.map { price ->
            val id = ++nextId
            DatedReceiptItem(id, name, ReceiptItems.normalize(name), BigDecimal(price), day(2026, 1, 1).plusSeconds(86_400 * id), "Konzum")
        }

    @Test
    fun `a product needs at least 11 purchases`() {
        val ten = purchases("MLIJEKO 1L", *Array(10) { "1.00" })
        assertTrue(PriceHistory.histories(ten).isEmpty())
        val eleven = ten + purchases("Mlijeko  1l", "1.00")
        assertEquals(1, PriceHistory.histories(eleven).size)
    }

    @Test
    fun `prices are averaged over the first and last three purchases`() {
        val milk = purchases("MLIJEKO 1L", "1.00", "1.10", "1.20", "9.99", "1.30", "1.30", "1.30", "1.30", "1.40", "1.50", "1.60").shuffled()
        val history = PriceHistory.histories(milk).single()
        assertAmount("1.10", history.startPrice)
        assertAmount("1.50", history.currentPrice)
        assertEquals(0.4 / 1.1, history.change, 1e-9)
        assertEquals("1.00", history.points.first().unitPrice.toPlainString())
    }

    @Test
    fun `the latest name is shown and lines without a price are left out`() {
        val bread = purchases("kruh", *Array(10) { "1.49" }) + purchases("KRUH", "1.59") + purchases("Kruh", "0")
        val history = PriceHistory.histories(bread).single()
        assertEquals("KRUH", history.name)
        assertEquals(11, history.points.size)
    }

    @Test
    fun `biggest change first and personal inflation is the average`() {
        val steady = purchases("SOL", *Array(11) { "0.50" })
        val rising = purchases("KAVA", "5", "5", "5", "5", "5", "5", "5", "5", "6", "6", "6")
        val falling = purchases("JAJA", "3", "3", "3", "3", "3", "3", "3", "3", "2.7", "2.7", "2.7")
        val histories = PriceHistory.histories(steady + falling + rising)
        assertEquals(listOf("kava", "jaja", "sol"), histories.map { it.key })
        assertEquals((0.2 - 0.1 + 0.0) / 3, PriceHistory.personalInflation(histories), 1e-9)
        assertEquals(0.0, PriceHistory.personalInflation(emptyList()), 0.0)
    }

    @Test
    fun `trend ignores changes under half a percent`() {
        assertEquals(PriceTrend.UP, PriceHistory.trend(0.006))
        assertEquals(PriceTrend.FLAT, PriceHistory.trend(0.005))
        assertEquals(PriceTrend.FLAT, PriceHistory.trend(-0.005))
        assertEquals(PriceTrend.DOWN, PriceHistory.trend(-0.006))
    }
}
