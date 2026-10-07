package work.spyglee.ladger.domain.receipt

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.MerchantCategory
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.TransactionDraft

class ReceiptDraftTest {
    private val food = Category(id = 1, name = "Hrana", kind = CategoryKind.EXPENSE)
    private val restaurants = Category(id = 2, name = "Restorani", kind = CategoryKind.EXPENSE)
    private val salary = Category(id = 3, name = "Plaća", kind = CategoryKind.INCOME)
    private val categories = listOf(food, restaurants, salary)
    private val receipt = ParsedReceipt(total = BigDecimal("12.49"), merchant = "Konzum", categoryName = "Hrana")

    @Test
    fun `fills amount, note and the model's category and makes it an expense`() {
        val draft = TransactionDraft(kind = TransactionKind.INCOME, destinationId = 5, categoryId = salary.id)
            .withReceipt(receipt, learned = null, categories = categories)
        assertEquals(TransactionKind.EXPENSE, draft.kind)
        assertEquals(BigDecimal("12.49"), draft.amount)
        assertEquals("Konzum", draft.note)
        assertEquals(food.id, draft.categoryId)
        assertNull(draft.destinationId)
    }

    @Test
    fun `learned category and source win over the model's guess`() {
        val learned = MerchantCategory(merchantKey = "konzum", categoryId = restaurants.id, sourceId = 7)
        val draft = TransactionDraft(sourceId = 1).withReceipt(receipt, learned, categories)
        assertEquals(restaurants.id, draft.categoryId)
        assertEquals(7L, draft.sourceId)
    }

    @Test
    fun `a typed note is kept and income categories are never picked`() {
        val draft = TransactionDraft(note = "Weekly shop")
            .withReceipt(receipt.copy(categoryName = "Plaća"), learned = null, categories = categories)
        assertEquals("Weekly shop", draft.note)
        assertNull(draft.categoryId)
    }

    @Test
    fun `missing values leave the form as it was`() {
        val before = TransactionDraft(amount = BigDecimal("3"), sourceId = 1, categoryId = food.id)
        assertEquals(before, before.withReceipt(ParsedReceipt(), learned = null, categories = categories))
    }
}
