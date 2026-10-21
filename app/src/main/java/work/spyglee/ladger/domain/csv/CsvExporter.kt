package work.spyglee.ladger.domain.csv

import java.time.ZoneId
import java.math.BigDecimal
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.lineBreak

/**
 * Writes transactions in the layout [CsvImporter] reads, so they can be imported back unchanged.
 * Since iOS `f909b82` the export also lists the sources and planned transactions, in files the
 * importer (on either platform) doesn't read.
 */
object CsvExporter {
    fun export(
        transactions: List<Transaction>,
        sources: List<Source>,
        categories: List<Category>,
        zone: ZoneId,
        plannedTransactions: List<PlannedTransaction> = emptyList(),
    ): List<CsvFile> {
        val sourceNames = sources.associate { it.id to it.name }
        val categoryNames = categories.associate { it.id to it.name }
        val byKind = transactions.sortedWith(compareBy({ it.date }, { it.id })).groupBy { it.kind }

        val transactionFiles = TransactionKind.entries.mapNotNull { kind ->
            val items = byKind[kind].orEmpty()
            if (items.isEmpty()) return@mapNotNull null
            val rows = items.map { transaction ->
                val source = sourceNames[transaction.sourceId].orEmpty()
                val destination = sourceNames[transaction.destinationId].orEmpty()
                val category = categoryNames[transaction.categoryId].orEmpty()
                val date = CsvFormat.exportDate.format(transaction.date.atZone(zone))
                val amount = transaction.amount.plain()
                when (kind) {
                    TransactionKind.EXPENSE -> listOf(date, amount, source, category, transaction.note)
                    TransactionKind.INCOME -> listOf(date, amount, destination, category, transaction.note)
                    TransactionKind.TRANSFER -> listOf(date, amount, source, destination, transaction.note)
                }
            }
            CsvFile("${kind.raw}s.csv", (listOf(header(kind)) + rows).toCsv())
        }
        return transactionFiles + listOfNotNull(
            sources.takeIf { it.isNotEmpty() }?.let { CsvFile("sources.csv", sourcesCsv(it)) },
            plannedTransactions.takeIf { it.isNotEmpty() }?.let {
                CsvFile("planned_transactions.csv", plannedCsv(it, sourceNames, categoryNames, zone))
            },
        )
    }

    private fun sourcesCsv(sources: List<Source>): String {
        val rows = sources.sortedWith(compareBy({ it.createdAt }, { it.id })).map { source ->
            listOf(source.name, source.initValue.plain(), source.systemImage, source.color.raw)
        }
        return (listOf(listOf("name", "initial_value", "icon", "color")) + rows).toCsv()
    }

    private fun plannedCsv(
        planned: List<PlannedTransaction>,
        sourceNames: Map<Long, String>,
        categoryNames: Map<Long, String>,
        zone: ZoneId,
    ): String {
        val header = listOf(
            "kind", "amount", "source", "destination", "category", "frequency",
            "interval", "start_date", "end_date", "paused", "details",
        )
        val rows = planned.sortedWith(compareBy({ it.startDate }, { it.id })).map { item ->
            listOf(
                item.kind.raw,
                item.amount.plain(),
                sourceNames[item.sourceId].orEmpty(),
                sourceNames[item.destinationId].orEmpty(),
                categoryNames[item.categoryId].orEmpty(),
                item.frequency.raw,
                item.interval.toString(),
                CsvFormat.exportDate.format(item.startDate.atZone(zone)),
                item.endDate?.let { CsvFormat.exportDate.format(it.atZone(zone)) }.orEmpty(),
                item.isPaused.toString(),
                item.note,
            )
        }
        return (listOf(header) + rows).toCsv()
    }

    /** Like NSDecimalNumber's `stringValue`: "12.5", "1000". */
    private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

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
