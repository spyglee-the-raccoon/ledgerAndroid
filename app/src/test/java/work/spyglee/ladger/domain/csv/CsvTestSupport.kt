package work.spyglee.ladger.domain.csv

import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction

/** In-memory stand-in for the database side of an import. */
class FakeSink : CsvImportSink {
    val sources = mutableListOf<Source>()
    val categories = mutableListOf<Category>()
    val transactions = mutableListOf<Transaction>()
    private var nextId = 100L

    override suspend fun createSource(source: Source): Long =
        nextId++.also { sources += source.copy(id = it) }

    override suspend fun createCategory(category: Category): Long =
        nextId++.also { categories += category.copy(id = it) }

    override suspend fun insertTransaction(transaction: Transaction) {
        transactions += transaction
    }
}
