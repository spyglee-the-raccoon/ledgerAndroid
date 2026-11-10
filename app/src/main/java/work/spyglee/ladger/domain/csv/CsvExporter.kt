package work.spyglee.ladger.domain.csv

import java.time.ZoneId
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CollectionTransaction
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.ReceiptItem
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionCollection
import work.spyglee.ladger.data.TransactionKind

/**
 * Writes transactions in the layout [CsvImporter] reads, one file per transaction kind, so they can
 * be imported back unchanged. Accounts, planned transactions, categories, budgets, collections and
 * receipt items go to their own files, which the importer (on either platform) recognizes by their
 * headers, as iOS's `CSVExporter` since `8a76b5d`. Receipt photos aren't exported.
 */
object CsvExporter {
    fun export(
        transactions: List<Transaction>,
        sources: List<Source>,
        categories: List<Category>,
        zone: ZoneId,
        plannedTransactions: List<PlannedTransaction> = emptyList(),
        budgets: List<Budget> = emptyList(),
        collections: List<TransactionCollection> = emptyList(),
        collectionTransactions: List<CollectionTransaction> = emptyList(),
        receiptItems: List<ReceiptItem> = emptyList(),
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
                val amount = CsvFormat.plain(transaction.amount)
                when (kind) {
                    TransactionKind.EXPENSE -> listOf(date, amount, source, category, transaction.note)
                    TransactionKind.INCOME -> listOf(date, amount, destination, category, transaction.note)
                    TransactionKind.TRANSFER -> listOf(date, amount, source, destination, transaction.note)
                }
            }
            CsvFile("${kind.raw}s.csv", (listOf(header(kind)) + rows).toCsv())
        }

        val references = TransactionReferences(transactions, sourceNames, zone)
        return transactionFiles + listOfNotNull(
            sources.takeIf { it.isNotEmpty() }?.let { CsvFile("accounts.csv", sourcesCsv(it)) },
            plannedTransactions.takeIf { it.isNotEmpty() }?.let {
                CsvFile("planned_transactions.csv", plannedCsv(it, sourceNames, categoryNames, zone))
            },
            categories.takeIf { it.isNotEmpty() }?.let { CsvFile("categories.csv", categoriesCsv(it)) },
            budgets.takeIf { it.isNotEmpty() }?.let { CsvFile("budgets.csv", budgetsCsv(it, categoryNames)) },
        ) + collectionFiles(collections, collectionTransactions, references) + listOfNotNull(
            receiptItems.takeIf { it.isNotEmpty() }?.let { CsvFile("receipt_items.csv", receiptItemsCsv(it, references)) },
        )
    }

    private fun sourcesCsv(sources: List<Source>): String {
        val rows = sources.sortedWith(compareBy({ it.createdAt }, { it.id })).map { source ->
            listOf(source.name, CsvFormat.plain(source.initValue), source.systemImage, source.color.raw)
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
                CsvFormat.plain(item.amount),
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

    private fun categoriesCsv(categories: List<Category>): String {
        val rows = categories.sortedWith(compareBy({ it.name }, { it.id })).map { category ->
            listOf(category.name, category.kind.raw, category.systemImage, category.color.raw)
        }
        return (listOf(listOf("name", "kind", "icon", "color")) + rows).toCsv()
    }

    /** The general limit has an empty category, so it comes first. */
    private fun budgetsCsv(budgets: List<Budget>, categoryNames: Map<Long, String>): String {
        val rows = budgets
            .map { categoryNames[it.categoryId].orEmpty() to it }
            .sortedWith(compareBy({ it.first }, { it.second.id }))
            .map { (category, budget) -> listOf(category, CsvFormat.plain(budget.monthlyLimit), budget.isActive.toString()) }
        return (listOf(listOf("category", "limit", "active")) + rows).toCsv()
    }

    /** `collections.csv`, plus `collection_transactions.csv` with one row per transaction in a collection. */
    private fun collectionFiles(
        collections: List<TransactionCollection>,
        memberships: List<CollectionTransaction>,
        references: TransactionReferences,
    ): List<CsvFile> {
        if (collections.isEmpty()) return emptyList()
        val sorted = collections.sortedWith(compareBy({ it.name }, { it.id }))
        val collectionRows = sorted.map { listOf(it.name, it.systemImage, it.color.raw) }

        val membersByCollection = memberships.groupBy({ it.collectionId }, { it.transactionId })
        val membershipRows = sorted.flatMap { collection ->
            membersByCollection[collection.id].orEmpty()
                .mapNotNull(references::transaction)
                .sortedWith(compareBy({ it.date }, { it.id }))
                .map { listOf(collection.name) + references.reference(it) }
        }
        return listOf(
            CsvFile("collections.csv", (listOf(listOf("name", "icon", "color")) + collectionRows).toCsv()),
            CsvFile(
                "collection_transactions.csv",
                (listOf(listOf("collection") + CsvFormat.transactionReferenceHeader) + membershipRows).toCsv(),
            ),
        )
    }

    private fun receiptItemsCsv(items: List<ReceiptItem>, references: TransactionReferences): String {
        val header = CsvFormat.transactionReferenceHeader + listOf("position", "name", "quantity", "unit_price", "total_price")
        val rows = items
            .map { references.transaction(it.transactionId) to it }
            .sortedWith(compareBy({ it.first?.date }, { it.second.position }, { it.second.id }))
            .map { (transaction, item) ->
                val reference = transaction?.let(references::reference)
                    ?: List(CsvFormat.transactionReferenceHeader.size) { "" }
                reference + listOf(
                    item.position.toString(),
                    item.name,
                    CsvFormat.plain(item.quantity),
                    CsvFormat.plain(item.unitPrice),
                    CsvFormat.plain(item.totalPrice),
                )
            }
        return (listOf(header) + rows).toCsv()
    }

    /** Identifies exported transactions, which have no IDs, by date, kind, amount, accounts and details. */
    private class TransactionReferences(
        transactions: List<Transaction>,
        private val sourceNames: Map<Long, String>,
        private val zone: ZoneId,
    ) {
        private val byId = transactions.associateBy { it.id }

        fun transaction(id: Long): Transaction? = byId[id]

        fun reference(transaction: Transaction): List<String> = listOf(
            CsvFormat.exportDate.format(transaction.date.atZone(zone)),
            transaction.kind.raw,
            CsvFormat.plain(transaction.amount),
            sourceNames[transaction.sourceId].orEmpty(),
            sourceNames[transaction.destinationId].orEmpty(),
            transaction.note,
        )
    }

    private fun header(kind: TransactionKind): List<String> = when (kind) {
        TransactionKind.EXPENSE -> listOf("date", "amount", "source", "category", "details")
        TransactionKind.INCOME -> listOf("date", "amount", "destination", "category", "details")
        TransactionKind.TRANSFER -> listOf("date", "amount", "source", "target", "details")
    }

    private fun List<List<String>>.toCsv(): String =
        joinToString("\n") { row -> row.joinToString(CsvFormat.SEPARATOR, transform = CsvFormat::escaped) } + "\n"
}
