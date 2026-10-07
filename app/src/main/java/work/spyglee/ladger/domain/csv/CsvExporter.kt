package work.spyglee.ladger.domain.csv

import java.time.ZoneId
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.lineBreak

/** Writes transactions in the layout [CsvImporter] reads, so an export can be imported back unchanged. */
object CsvExporter {
    fun export(
        transactions: List<Transaction>,
        sources: List<Source>,
        categories: List<Category>,
        zone: ZoneId,
    ): List<CsvFile> {
        val sourceNames = sources.associate { it.id to it.name }
        val categoryNames = categories.associate { it.id to it.name }
        val byKind = transactions.sortedWith(compareBy({ it.date }, { it.id })).groupBy { it.kind }

        return TransactionKind.entries.mapNotNull { kind ->
            val items = byKind[kind].orEmpty()
            if (items.isEmpty()) return@mapNotNull null
            val rows = items.map { transaction ->
                val source = sourceNames[transaction.sourceId].orEmpty()
                val destination = sourceNames[transaction.destinationId].orEmpty()
                val category = categoryNames[transaction.categoryId].orEmpty()
                val date = CsvFormat.exportDate.format(transaction.date.atZone(zone))
                val amount = transaction.amount.stripTrailingZeros().toPlainString()
                when (kind) {
                    TransactionKind.EXPENSE -> listOf(date, amount, source, category, transaction.note)
                    TransactionKind.INCOME -> listOf(date, amount, destination, category, transaction.note)
                    TransactionKind.TRANSFER -> listOf(date, amount, source, destination, transaction.note)
                }
            }
            CsvFile("${kind.raw}s.csv", (listOf(header(kind)) + rows).toCsv())
        }
    }

    private fun header(kind: TransactionKind): List<String> = when (kind) {
        TransactionKind.EXPENSE -> listOf("date", "amount", "source", "category", "details")
        TransactionKind.INCOME -> listOf("date", "amount", "destination", "category", "details")
        TransactionKind.TRANSFER -> listOf("date", "amount", "source", "target", "details")
    }

    private fun List<List<String>>.toCsv(): String =
        joinToString("\n") { row -> row.joinToString(CsvFormat.SEPARATOR, transform = ::escaped) } + "\n"

    /**
     * The importer splits on every semicolon and line break, so those are replaced rather than
     * quoted to keep values intact on re-import.
     */
    private fun escaped(value: String): String =
        value.replace(";", ",")
            .replace("\"", "'")
            .split(lineBreak)
            .joinToString(" ")
}
