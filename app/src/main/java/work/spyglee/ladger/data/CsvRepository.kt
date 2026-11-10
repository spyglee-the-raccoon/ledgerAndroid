package work.spyglee.ladger.data

import androidx.room.withTransaction
import java.time.ZoneId
import work.spyglee.ladger.domain.csv.CsvExporter
import work.spyglee.ladger.domain.csv.CsvFile
import work.spyglee.ladger.domain.csv.CsvImportResult
import work.spyglee.ladger.domain.csv.CsvImportSink
import work.spyglee.ladger.domain.csv.CsvImporter

class CsvRepository(private val database: LadgerDatabase) {
    /** Imports all files in one database transaction: if anything fails, nothing is saved. */
    suspend fun import(inputs: List<CsvImporter.Input>): CsvImportResult = database.withTransaction {
        val importer = CsvImporter(
            existingSources = database.sourceDao().getAll(),
            existingCategories = database.categoryDao().getAll(),
            zone = ZoneId.systemDefault(),
            existingCollections = database.collectionDao().getAll(),
            existingBudgets = database.budgetDao().getAll(),
            existingPlanned = database.plannedTransactionDao().getAll(),
        )
        importer.import(inputs, DatabaseSink(database))
    }

    suspend fun export(): CsvExport {
        val transactions = database.transactionDao().getAllChronological()
        val files = CsvExporter.export(
            transactions = transactions,
            sources = database.sourceDao().getAll(),
            categories = database.categoryDao().getAll(),
            zone = ZoneId.systemDefault(),
            plannedTransactions = database.plannedTransactionDao().getAll(),
            budgets = database.budgetDao().getAll(),
            collections = database.collectionDao().getAll(),
            collectionTransactions = database.collectionDao().getAllMemberships(),
            receiptItems = database.receiptItemDao().getAll(),
        )
        return CsvExport(files, transactions.size)
    }

    /** As on iOS, there is nothing to export only when the export would write no files at all. */
    suspend fun hasDataToExport(): Boolean = export().files.isNotEmpty()

    private class DatabaseSink(private val database: LadgerDatabase) : CsvImportSink {
        override suspend fun createSource(source: Source): Long = database.sourceDao().insert(source)
        override suspend fun createCategory(category: Category): Long = database.categoryDao().insert(category)
        override suspend fun insertTransaction(transaction: Transaction): Long = database.transactionDao().insert(transaction)

        override suspend fun insertPlannedTransaction(planned: PlannedTransaction) {
            database.plannedTransactionDao().insert(planned)
        }

        override suspend fun insertBudget(budget: Budget) {
            database.budgetDao().insert(budget)
        }

        override suspend fun createCollection(collection: TransactionCollection): Long = database.collectionDao().insert(collection)

        override suspend fun addToCollection(collectionId: Long, transactionId: Long) {
            database.collectionDao().addTransaction(CollectionTransaction(collectionId, transactionId))
        }

        override suspend fun insertReceiptItem(item: ReceiptItem) {
            database.receiptItemDao().insert(item)
        }

        override suspend fun existingTransactions(): List<Transaction> = database.transactionDao().getAllChronological()
        override suspend fun collectionTransactionIds(collectionId: Long): List<Long> =
            database.collectionDao().getTransactionIds(collectionId)
        override suspend fun receiptItems(transactionId: Long): List<ReceiptItem> =
            database.receiptItemDao().getForTransaction(transactionId)
    }
}

/** The files to write and how many transactions they hold, for the "Exported N transactions" message. */
data class CsvExport(val files: List<CsvFile>, val transactionCount: Int)
