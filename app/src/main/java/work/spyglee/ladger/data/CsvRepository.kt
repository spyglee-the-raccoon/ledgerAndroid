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
        )
        importer.import(inputs, object : CsvImportSink {
            override suspend fun createSource(source: Source): Long = database.sourceDao().insert(source)
            override suspend fun createCategory(category: Category): Long = database.categoryDao().insert(category)
            override suspend fun insertTransaction(transaction: Transaction) {
                database.transactionDao().insert(transaction)
            }
        })
    }

    suspend fun export(): CsvExport {
        val transactions = database.transactionDao().getAllChronological()
        val files = CsvExporter.export(
            transactions = transactions,
            sources = database.sourceDao().getAll(),
            categories = database.categoryDao().getAll(),
            zone = ZoneId.systemDefault(),
            plannedTransactions = database.plannedTransactionDao().getAll(),
        )
        return CsvExport(files, transactions.size)
    }

    suspend fun hasTransactions(): Boolean = database.transactionDao().count() > 0
}

/** The files to write and how many transactions they hold, for the "Exported N transactions" message. */
data class CsvExport(val files: List<CsvFile>, val transactionCount: Int)
