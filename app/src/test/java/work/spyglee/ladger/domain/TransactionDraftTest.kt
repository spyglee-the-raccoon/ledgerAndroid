package work.spyglee.ladger.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.TransactionKind

class TransactionDraftTest {
    private val food = Category(id = 1, name = "Food")
    private val salary = Category(id = 2, name = "Salary", kind = CategoryKind.INCOME)
    private val categories = listOf(food, salary)
    private val ten = BigDecimal.TEN

    @Test
    fun `validity depends on the fields each kind needs`() {
        assertTrue(TransactionDraft(ten, TransactionKind.EXPENSE, sourceId = 1, categoryId = 1).isValid)
        assertFalse(TransactionDraft(ten, TransactionKind.EXPENSE, sourceId = 1).isValid)
        assertFalse(TransactionDraft(BigDecimal.ZERO, TransactionKind.EXPENSE, sourceId = 1, categoryId = 1).isValid)
        assertFalse(TransactionDraft(null, TransactionKind.EXPENSE, sourceId = 1, categoryId = 1).isValid)
        assertTrue(TransactionDraft(ten, TransactionKind.INCOME, destinationId = 1, categoryId = 2).isValid)
        assertFalse(TransactionDraft(ten, TransactionKind.INCOME, sourceId = 1, categoryId = 2).isValid)
        assertTrue(TransactionDraft(ten, TransactionKind.TRANSFER, sourceId = 1, destinationId = 2).isValid)
        assertFalse(TransactionDraft(ten, TransactionKind.TRANSFER, sourceId = 1).isValid)
    }

    @Test
    fun `changing kind clears fields the new kind does not use`() {
        val transfer = TransactionDraft(ten, TransactionKind.TRANSFER, sourceId = 1, destinationId = 2)
        val income = transfer.withKind(TransactionKind.INCOME, categories)
        assertNull(income.sourceId)
        assertEquals(2L, income.destinationId)

        val expense = transfer.withKind(TransactionKind.EXPENSE, categories)
        assertEquals(1L, expense.sourceId)
        assertNull(expense.destinationId)
    }

    @Test
    fun `changing kind keeps only a category of the matching kind`() {
        val expense = TransactionDraft(ten, TransactionKind.EXPENSE, sourceId = 1, categoryId = food.id)
        assertEquals(food.id, expense.withKind(TransactionKind.EXPENSE, categories).categoryId)
        assertNull(expense.withKind(TransactionKind.INCOME, categories).categoryId)
        assertNull(expense.withKind(TransactionKind.TRANSFER, categories).categoryId)
    }

    @Test
    fun `round trips through a transaction and drops unused fields`() {
        val draft = TransactionDraft(ten, TransactionKind.INCOME, sourceId = 5, destinationId = 2, categoryId = 2, note = "May")
        val transaction = draft.toTransaction(id = 7)
        assertEquals(7L, transaction.id)
        assertNull(transaction.sourceId)
        assertEquals(draft.copy(sourceId = null), TransactionDraft.from(transaction))
    }

    @Test
    fun `amount input accepts comma or dot and rejects junk`() {
        assertAmount("12.49", AmountInput.parse("12,49"))
        assertAmount("12.49", AmountInput.parse(" 12.49 "))
        assertAmount("0.5", AmountInput.parse(",5"))
        assertAmount("12", AmountInput.parse("12."))
        assertNull(AmountInput.parse(""))
        assertNull(AmountInput.parse(","))
        assertNull(AmountInput.parse("1.2.3"))
        assertNull(AmountInput.parse("12a"))
        assertNull(AmountInput.parse("-5"))
        assertAmount("-5", AmountInput.parse("-5", allowNegative = true))
        assertEquals("12,5", AmountInput.format(BigDecimal("12.50"), ','))
        assertEquals("100", AmountInput.format(BigDecimal("100"), '.'))
    }
}
