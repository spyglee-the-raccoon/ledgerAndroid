package work.spyglee.ladger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.SourceSuggestion.Role

class SourceSuggestionTest {
    private fun expensesFrom(vararg sourceIds: Long) = sourceIds.map { expense("1", sourceId = it) }

    @Test
    fun `most frequent source wins`() {
        assertEquals(2L, SourceSuggestion.mostLikely(Role.SOURCE, TransactionKind.EXPENSE, expensesFrom(1, 2, 2)))
    }

    @Test
    fun `ties go to the most recently used source`() {
        assertEquals(2L, SourceSuggestion.mostLikely(Role.SOURCE, TransactionKind.EXPENSE, expensesFrom(2, 1, 1, 2, 3)))
        assertEquals(1L, SourceSuggestion.mostLikely(Role.SOURCE, TransactionKind.EXPENSE, expensesFrom(1, 2, 2, 1, 3)))
    }

    @Test
    fun `only the last 30 uses count`() {
        val recent = List(20) { expense("1", sourceId = 1) } + List(25) { expense("1", sourceId = 2) }
        assertEquals(1L, SourceSuggestion.mostLikely(Role.SOURCE, TransactionKind.EXPENSE, recent))
    }

    @Test
    fun `filters by kind and role and skips the excluded source`() {
        val recent = listOf(
            income("1", destinationId = 7),
            transfer("1", sourceId = 3, destinationId = 5),
            transfer("1", sourceId = 3, destinationId = 5),
            transfer("1", sourceId = 5, destinationId = 4),
        )
        assertNull(SourceSuggestion.mostLikely(Role.SOURCE, TransactionKind.EXPENSE, recent))
        assertEquals(7L, SourceSuggestion.mostLikely(Role.DESTINATION, TransactionKind.INCOME, recent))
        assertEquals(5L, SourceSuggestion.mostLikely(Role.DESTINATION, TransactionKind.TRANSFER, recent))
        assertEquals(4L, SourceSuggestion.mostLikely(Role.DESTINATION, TransactionKind.TRANSFER, recent, excludingId = 5))
    }
}
