package work.spyglee.ladger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind

class CollectionsTest {
    private val food = Category(id = 1, name = "Food")
    private val hotel = Category(id = 2, name = "Hotel")
    private val transport = Category(id = 3, name = "Transport")
    private val museums = Category(id = 4, name = "Museums")
    private val refunds = Category(id = 5, name = "Refunds", kind = CategoryKind.INCOME)
    private val categories = listOf(food, hotel, transport, museums, refunds)

    @Test
    fun `total is incomes minus expenses and ignores transfers`() {
        val total = Collections.total(
            listOf(expense("120", categoryId = 2), expense("30.50"), income("20", categoryId = 5), transfer("200", 1, 2))
        )
        assertAmount("-130.50", total)
    }

    @Test
    fun `top categories by absolute total, uncategorized left out`() {
        val top = Collections.topCategories(
            listOf(
                expense("12", categoryId = 1),
                expense("18", categoryId = 1),
                expense("120", categoryId = 2),
                expense("25", categoryId = 3),
                expense("10", categoryId = 4),
                income("60", categoryId = 5),
                expense("500"),
            ),
            categories,
        )
        assertEquals(listOf(hotel, refunds, food), top.map { it.category })
        assertAmount("-120", top[0].total)
        assertAmount("60", top[1].total)
        assertAmount("-30", top[2].total)
    }

    @Test
    fun `ties keep category order and empty collections have no top categories`() {
        val top = Collections.topCategories(listOf(expense("5", categoryId = 3), expense("5", categoryId = 1)), categories)
        assertEquals(listOf(food, transport), top.map { it.category })
        assertTrue(Collections.topCategories(emptyList(), categories).isEmpty())
    }
}
