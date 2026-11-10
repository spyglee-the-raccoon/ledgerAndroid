package work.spyglee.ladger.domain.csv

import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CollectionTransaction
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.ReceiptItem
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionCollection

/** In-memory stand-in for the database side of an import. Lists start with what already exists. */
class FakeSink(
    val sources: MutableList<Source> = mutableListOf(),
    val categories: MutableList<Category> = mutableListOf(),
    existingTransactions: List<Transaction> = emptyList(),
) : CsvImportSink {
    private val existing = existingTransactions.toList()
    val transactions = existingTransactions.toMutableList()
    val planned = mutableListOf<PlannedTransaction>()
    val budgets = mutableListOf<Budget>()
    val collections = mutableListOf<TransactionCollection>()
    val memberships = mutableListOf<CollectionTransaction>()
    val receiptItems = mutableListOf<ReceiptItem>()
    private var nextId = 100L

    /** Transactions added by the import, without the existing ones. */
    val imported: List<Transaction> get() = transactions.drop(existing.size)

    override suspend fun createSource(source: Source): Long =
        nextId++.also { sources += source.copy(id = it) }

    override suspend fun createCategory(category: Category): Long =
        nextId++.also { categories += category.copy(id = it) }

    override suspend fun insertTransaction(transaction: Transaction): Long =
        nextId++.also { transactions += transaction.copy(id = it) }

    override suspend fun insertPlannedTransaction(planned: PlannedTransaction) {
        this.planned += planned.copy(id = nextId++)
    }

    override suspend fun insertBudget(budget: Budget) {
        budgets += budget.copy(id = nextId++)
    }

    override suspend fun createCollection(collection: TransactionCollection): Long =
        nextId++.also { collections += collection.copy(id = it) }

    override suspend fun addToCollection(collectionId: Long, transactionId: Long) {
        memberships += CollectionTransaction(collectionId, transactionId)
    }

    override suspend fun insertReceiptItem(item: ReceiptItem) {
        receiptItems += item.copy(id = nextId++)
    }

    override suspend fun existingTransactions(): List<Transaction> = existing

    override suspend fun collectionTransactionIds(collectionId: Long): List<Long> =
        memberships.filter { it.collectionId == collectionId }.map { it.transactionId }

    override suspend fun receiptItems(transactionId: Long): List<ReceiptItem> =
        receiptItems.filter { it.transactionId == transactionId }

    fun membersOf(collectionId: Long): List<Transaction> {
        val ids = memberships.filter { it.collectionId == collectionId }.map { it.transactionId }
        return ids.map { id -> transactions.first { it.id == id } }
    }

    fun sourceName(id: Long?): String? = sources.firstOrNull { it.id == id }?.name
    fun categoryName(id: Long?): String? = categories.firstOrNull { it.id == id }?.name
}
