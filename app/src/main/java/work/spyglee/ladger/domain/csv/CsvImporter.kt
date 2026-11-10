package work.spyglee.ladger.domain.csv

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.ReceiptItem
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionCollection
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.Recurrence
import work.spyglee.ladger.domain.lineBreak
import work.spyglee.ladger.domain.receipt.ReceiptItems

data class CsvImportResult(
    val importedCount: Int = 0,
    val createdSources: Int = 0,
    val createdCategories: Int = 0,
    /** Line numbers (1-based, header included) that couldn't be read, per file name. */
    val skippedLines: Map<String, List<Int>> = emptyMap(),
) {
    val skippedCount: Int get() = skippedLines.values.sumOf { it.size }
}

class CsvImportException(val reason: Reason, val fileName: String) : Exception("$reason: $fileName") {
    enum class Reason { UNREADABLE, UNKNOWN_FORMAT }
}

/** Where imported data is written and existing data is read; implemented on top of the database inside one transaction. */
interface CsvImportSink {
    suspend fun createSource(source: Source): Long
    suspend fun createCategory(category: Category): Long
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun insertPlannedTransaction(planned: PlannedTransaction)
    suspend fun insertBudget(budget: Budget)
    suspend fun createCollection(collection: TransactionCollection): Long
    suspend fun addToCollection(collectionId: Long, transactionId: Long)
    suspend fun insertReceiptItem(item: ReceiptItem)

    /** The transactions saved before the import, which the collection and receipt item files can refer to. */
    suspend fun existingTransactions(): List<Transaction>
    suspend fun collectionTransactionIds(collectionId: Long): List<Long>
    suspend fun receiptItems(transactionId: Long): List<ReceiptItem>
}

/**
 * Imports the CSV files [CsvExporter] writes, the same files as iOS's `CSVImporter`, plus transaction
 * files from other apps in the same layout. Each file's type comes from its header, not its name.
 * Accounts and categories go first so transactions link to them; the collection and receipt item
 * files, which refer to transactions, go last. Accounts, categories and collections are matched by
 * name ignoring case and created when missing; budgets and planned transactions that already exist
 * are skipped; transactions are always added.
 */
class CsvImporter(
    existingSources: List<Source>,
    existingCategories: List<Category>,
    private val zone: ZoneId,
    existingCollections: List<TransactionCollection> = emptyList(),
    existingBudgets: List<Budget> = emptyList(),
    existingPlanned: List<PlannedTransaction> = emptyList(),
    private val now: Instant = Instant.now(),
) {
    data class Input(val fileName: String, val text: String)

    private val sourceIds = existingSources.associate { normalized(it.name) to it.id }.toMutableMap()
    private val sourceNames = existingSources.associate { it.id to it.name }.toMutableMap()
    private val categoryIds = existingCategories.associate { categoryKey(it.name, it.kind) to it.id }.toMutableMap()
    private val collectionIds = existingCollections.associate { normalized(it.name) to it.id }.toMutableMap()
    private val budgetKeys: MutableSet<String>
    private val plannedKeys: MutableSet<String>

    /** Transactions the collection and receipt item files can refer to, by [transactionKey]. Ones imported in this run come first. */
    private val importedTransactions = mutableMapOf<String, MutableList<Long>>()
    private var existingTransactions: Map<String, List<Long>> = emptyMap()
    private val collectionMembers = mutableMapOf<Long, MutableSet<Long>>()
    private val receiptItemKeys = mutableMapOf<Long, MutableSet<Pair<Int, String>>>()

    private var importedCount = 0
    private var createdSources = 0
    private var createdCategories = 0

    init {
        val categoryNames = existingCategories.associate { it.id to it.name }
        budgetKeys = existingBudgets.mapTo(mutableSetOf()) { normalized(categoryNames[it.categoryId].orEmpty()) }
        plannedKeys = existingPlanned.mapTo(mutableSetOf()) {
            plannedKey(it.kind, it.amount, sourceNames[it.sourceId], sourceNames[it.destinationId], it.frequency, it.interval, it.startDate, it.note)
        }
    }

    /**
     * Every file is parsed before anything is written, so a file in an unknown format imports nothing.
     * Call once per importer: it remembers what it created.
     */
    suspend fun import(inputs: List<Input>, sink: CsvImportSink): CsvImportResult {
        val tables = inputs.map { parse(it.text, it.fileName) }.sortedBy { it.kind.order }

        // Index existing transactions before inserting any, so new ones aren't counted twice.
        if (tables.any { it.kind.referencesTransactions }) {
            existingTransactions = sink.existingTransactions().groupBy({ transactionKey(it) }, { it.id })
        }

        val skipped = linkedMapOf<String, MutableList<Int>>()
        for (table in tables) {
            for (line in table.lines) {
                if (!insert(line, table.kind, sink)) skipped.getOrPut(table.fileName) { mutableListOf() } += line.number
            }
        }
        return CsvImportResult(importedCount, createdSources, createdCategories, skipped)
    }

    // Parsing

    private enum class FileKind(val order: Int, val transactionKind: TransactionKind? = null) {
        ACCOUNTS(0),
        CATEGORIES(1),
        EXPENSES(2, TransactionKind.EXPENSE),
        INCOMES(2, TransactionKind.INCOME),
        TRANSFERS(2, TransactionKind.TRANSFER),
        PLANNED_TRANSACTIONS(3),
        BUDGETS(4),
        COLLECTIONS(5),
        COLLECTION_TRANSACTIONS(6),
        RECEIPT_ITEMS(7);

        val referencesTransactions: Boolean get() = this == COLLECTION_TRANSACTIONS || this == RECEIPT_ITEMS
    }

    private class Table(val fileName: String, val kind: FileKind, val lines: List<Line>)

    private class Line(val number: Int, private val columns: List<String>, private val fields: List<String>) {
        /** The trimmed value in [column], or null when the column is missing or empty. */
        operator fun get(column: String): String? {
            val index = columns.indexOf(column)
            if (index < 0) return null
            return fields.getOrNull(index)?.takeIf { it.isNotEmpty() }
        }
    }

    private fun parse(text: String, fileName: String): Table {
        val lines = text.replace("\uFEFF", "")
            .split(lineBreak)
            .withIndex()
            .filter { it.value.isNotBlank() }

        val header = lines.firstOrNull() ?: throw CsvImportException(CsvImportException.Reason.UNKNOWN_FORMAT, fileName)
        val columns = fields(header.value).map { it.lowercase() }
        val kind = fileKind(columns.toSet()) ?: throw CsvImportException(CsvImportException.Reason.UNKNOWN_FORMAT, fileName)
        return Table(fileName, kind, lines.drop(1).map { (offset, line) -> Line(offset + 1, columns, fields(line)) })
    }

    private fun fileKind(columns: Set<String>): FileKind? {
        fun has(vararg names: String) = columns.containsAll(names.asList())
        if (has("date", "amount")) {
            return when {
                has("source", "target") -> FileKind.TRANSFERS
                has("destination", "category") -> FileKind.INCOMES
                has("source", "category") -> FileKind.EXPENSES
                else -> null
            }
        }
        return when {
            has("collection", "transaction_date") -> FileKind.COLLECTION_TRANSACTIONS
            has("transaction_date", "name") -> FileKind.RECEIPT_ITEMS
            has("kind", "amount", "frequency", "start_date") -> FileKind.PLANNED_TRANSACTIONS
            has("limit") -> FileKind.BUDGETS
            has("name", "initial_value") -> FileKind.ACCOUNTS
            has("name", "kind") -> FileKind.CATEGORIES
            has("name", "icon", "color") -> FileKind.COLLECTIONS
            else -> null
        }
    }

    private fun fields(line: String): List<String> =
        line.split(CsvFormat.SEPARATOR).map { it.trim().trim('"') }

    private fun parseDate(string: String): Instant? {
        val trimmed = string.removeSuffix(".")
        return try {
            LocalDate.parse(trimmed, CsvFormat.importDate).atStartOfDay(zone).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Transaction amounts are always positive; the kind gives the direction. */
    private fun parseAmount(string: String): BigDecimal? = parseSignedAmount(string)?.abs()

    /** Accepts "12,49", "1.234,56", "12.49" and "-5"; account initial values keep their sign. */
    private fun parseSignedAmount(string: String): BigDecimal? {
        var cleaned = string.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        if (cleaned.contains(',')) {
            cleaned = cleaned.replace(".", "").replace(",", ".")
        }
        return cleaned.toBigDecimalOrNull()
    }

    private fun parseBool(string: String?, default: Boolean): Boolean =
        string?.lowercase()?.let { it in setOf("true", "yes", "1") } ?: default

    // Inserting

    /** False when the line is invalid and should be reported. Lines describing something that already exists are ignored and return true. */
    private suspend fun insert(line: Line, kind: FileKind, sink: CsvImportSink): Boolean = when (kind) {
        FileKind.ACCOUNTS -> insertAccount(line, sink)
        FileKind.CATEGORIES -> insertCategory(line, sink)
        FileKind.EXPENSES, FileKind.INCOMES, FileKind.TRANSFERS -> insertTransaction(line, kind.transactionKind!!, sink)
        FileKind.PLANNED_TRANSACTIONS -> insertPlannedTransaction(line, sink)
        FileKind.BUDGETS -> insertBudget(line, sink)
        FileKind.COLLECTIONS -> insertCollection(line, sink)
        FileKind.COLLECTION_TRANSACTIONS -> insertCollectionTransaction(line, sink)
        FileKind.RECEIPT_ITEMS -> insertReceiptItem(line, sink)
    }

    private suspend fun insertAccount(line: Line, sink: CsvImportSink): Boolean {
        val name = line["name"] ?: return false
        val key = normalized(name)
        if (key in sourceIds) return true

        val defaults = Source(name = name)
        val source = defaults.copy(
            systemImage = line["icon"] ?: defaults.systemImage,
            initValue = line["initial_value"]?.let(::parseSignedAmount) ?: BigDecimal.ZERO,
            color = line["color"]?.let(CategoryColor::fromRawOrNull) ?: defaults.color,
        )
        rememberSource(key, name, sink.createSource(source))
        return true
    }

    private suspend fun insertCategory(line: Line, sink: CsvImportSink): Boolean {
        val name = line["name"] ?: return false
        val kind = line["kind"]?.let(CategoryKind::fromRawOrNull) ?: return false
        val key = categoryKey(name, kind)
        if (key in categoryIds) return true

        val defaults = Category(name = name, kind = kind)
        val category = defaults.copy(
            systemImage = line["icon"] ?: defaults.systemImage,
            color = line["color"]?.let(CategoryColor::fromRawOrNull) ?: defaults.color,
        )
        categoryIds[key] = sink.createCategory(category)
        createdCategories++
        return true
    }

    private suspend fun insertTransaction(line: Line, kind: TransactionKind, sink: CsvImportSink): Boolean {
        val sourceName = if (kind == TransactionKind.INCOME) null else line["source"]
        val destinationName = when (kind) {
            TransactionKind.INCOME -> line["destination"]
            TransactionKind.TRANSFER -> line["target"]
            TransactionKind.EXPENSE -> null
        }
        val date = line["date"]?.let(::parseDate) ?: return false
        val amount = line["amount"]?.let(::parseAmount) ?: return false
        if (sourceName == null && destinationName == null) return false

        val transaction = Transaction(
            amount = amount,
            kind = kind,
            sourceId = sourceName?.let { sourceId(it, sink) },
            destinationId = destinationName?.let { sourceId(it, sink) },
            note = line["details"].orEmpty(),
            date = date,
            categoryId = line["category"]?.let { categoryId(it, kind, sink) },
        )
        val id = sink.insertTransaction(transaction)
        importedTransactions.getOrPut(transactionKey(transaction)) { mutableListOf() } += id
        importedCount++
        return true
    }

    private suspend fun insertPlannedTransaction(line: Line, sink: CsvImportSink): Boolean {
        val kind = line["kind"]?.let(TransactionKind::fromRawOrNull) ?: return false
        val amount = line["amount"]?.let(::parseAmount) ?: return false
        val frequency = line["frequency"]?.let(RecurrenceFrequency::fromRawOrNull) ?: return false
        val startDate = line["start_date"]?.let(::parseDate) ?: return false
        val interval = maxOf(line["interval"]?.toIntOrNull() ?: 1, 1)
        val sourceName = line["source"]
        val destinationName = line["destination"]
        val note = line["details"].orEmpty()
        if (sourceName == null && destinationName == null) return false

        // Checked before creating its accounts and category, so a duplicate line creates nothing.
        val key = plannedKey(kind, amount, sourceName, destinationName, frequency, interval, startDate, note)
        if (key in plannedKeys) return true

        val planned = PlannedTransaction(
            amount = amount,
            kind = kind,
            sourceId = sourceName?.let { sourceId(it, sink) },
            destinationId = destinationName?.let { sourceId(it, sink) },
            note = note,
            categoryId = line["category"]?.let { categoryId(it, kind, sink) },
            frequency = frequency,
            interval = interval,
            startDate = startDate,
            endDate = line["end_date"]?.let(::parseDate),
            isPaused = parseBool(line["paused"], default = false),
        )
        // Past occurrences are already in the exported transactions, so they count as recorded;
        // otherwise they would be recorded a second time. Paused plans too, as on iOS.
        val past = Recurrence.dueDates(planned.copy(isPaused = false), now, zone).size
        sink.insertPlannedTransaction(planned.copy(occurrencesGenerated = past))
        plannedKeys += key
        return true
    }

    /** Budgets always belong to an expense category; an empty category is the general limit. */
    private suspend fun insertBudget(line: Line, sink: CsvImportSink): Boolean {
        val limit = line["limit"]?.let(::parseAmount) ?: return false
        val categoryName = line["category"]
        val key = normalized(categoryName.orEmpty())
        if (key in budgetKeys) return true

        sink.insertBudget(
            Budget(
                monthlyLimit = limit,
                isActive = parseBool(line["active"], default = true),
                categoryId = categoryName?.let { categoryId(it, TransactionKind.EXPENSE, sink) },
            )
        )
        budgetKeys += key
        return true
    }

    private suspend fun insertCollection(line: Line, sink: CsvImportSink): Boolean {
        val name = line["name"] ?: return false
        if (normalized(name) in collectionIds) return true
        collectionId(name, sink, line["icon"], line["color"]?.let(CategoryColor::fromRawOrNull))
        return true
    }

    private suspend fun insertCollectionTransaction(line: Line, sink: CsvImportSink): Boolean {
        val name = line["collection"] ?: return false
        val matches = referencedTransactions(line)
        if (matches.isEmpty()) return false

        val collectionId = collectionId(name, sink)
        val members = collectionMembers.getOrPut(collectionId) { sink.collectionTransactionIds(collectionId).toMutableSet() }
        // Identical transactions are added one per line; when all of them are already in the
        // collection the line is a duplicate and is ignored.
        matches.firstOrNull { it !in members }?.let {
            sink.addToCollection(collectionId, it)
            members += it
        }
        return true
    }

    private suspend fun insertReceiptItem(line: Line, sink: CsvImportSink): Boolean {
        val name = line["name"] ?: return false
        val transactionId = referencedTransactions(line).firstOrNull() ?: return false
        val position = line["position"]?.toIntOrNull() ?: 0
        val keys = receiptItemKeys.getOrPut(transactionId) {
            sink.receiptItems(transactionId).mapTo(mutableSetOf()) { it.position to it.name }
        }
        if ((position to name) in keys) return true

        sink.insertReceiptItem(
            ReceiptItem(
                transactionId = transactionId,
                name = name,
                normalizedName = ReceiptItems.normalize(name),
                quantity = line["quantity"]?.let(::parseAmount) ?: BigDecimal.ONE,
                unitPrice = line["unit_price"]?.let(::parseAmount) ?: BigDecimal.ZERO,
                totalPrice = line["total_price"]?.let(::parseAmount) ?: BigDecimal.ZERO,
                position = position,
            )
        )
        keys += position to name
        return true
    }

    private suspend fun sourceId(name: String, sink: CsvImportSink): Long {
        val key = normalized(name)
        sourceIds[key]?.let { return it }
        return sink.createSource(Source(name = name)).also { rememberSource(key, name, it) }
    }

    private fun rememberSource(key: String, name: String, id: Long) {
        sourceIds[key] = id
        sourceNames[id] = name
        createdSources++
    }

    private suspend fun categoryId(name: String, transactionKind: TransactionKind, sink: CsvImportSink): Long? {
        val kind = when (transactionKind) {
            TransactionKind.INCOME -> CategoryKind.INCOME
            TransactionKind.EXPENSE -> CategoryKind.EXPENSE
            TransactionKind.TRANSFER -> return null
        }
        val key = categoryKey(name, kind)
        categoryIds[key]?.let { return it }
        return sink.createCategory(Category(name = name, kind = kind)).also {
            categoryIds[key] = it
            createdCategories++
        }
    }

    private suspend fun collectionId(
        name: String,
        sink: CsvImportSink,
        systemImage: String? = null,
        color: CategoryColor? = null,
    ): Long {
        val key = normalized(name)
        collectionIds[key]?.let { return it }
        val defaults = TransactionCollection(name = name)
        val collection = defaults.copy(systemImage = systemImage ?: defaults.systemImage, color = color ?: defaults.color)
        return sink.createCollection(collection).also {
            collectionIds[key] = it
            collectionMembers[it] = mutableSetOf()
        }
    }

    // Keys

    /** IDs of the transactions matching the `transaction_*` columns [CsvExporter] writes. */
    private fun referencedTransactions(line: Line): List<Long> {
        val date = line["transaction_date"]?.let(::parseDate) ?: return emptyList()
        val kind = line["transaction_kind"]?.let(TransactionKind::fromRawOrNull) ?: return emptyList()
        val amount = line["transaction_amount"]?.let(::parseAmount) ?: return emptyList()
        val key = transactionKey(
            date, kind, amount,
            line["transaction_source"], line["transaction_destination"], line["transaction_details"].orEmpty(),
        )
        return importedTransactions[key].orEmpty() + existingTransactions[key].orEmpty()
    }

    private fun transactionKey(transaction: Transaction): String = transactionKey(
        transaction.date, transaction.kind, transaction.amount,
        sourceNames[transaction.sourceId], sourceNames[transaction.destinationId], transaction.note,
    )

    /**
     * Exported dates have no time and exported text is escaped, so both sides of the comparison
     * are reduced to what survives a round trip through the CSV.
     */
    private fun transactionKey(
        date: Instant,
        kind: TransactionKind,
        amount: BigDecimal,
        sourceName: String?,
        destinationName: String?,
        note: String,
    ): String = listOf(
        CsvFormat.exportDate.format(date.atZone(zone)),
        kind.raw,
        CsvFormat.plain(amount),
        normalized(sourceName.orEmpty()),
        normalized(destinationName.orEmpty()),
        roundTripped(note),
    ).joinToString(KEY_SEPARATOR)

    private fun plannedKey(
        kind: TransactionKind,
        amount: BigDecimal,
        sourceName: String?,
        destinationName: String?,
        frequency: RecurrenceFrequency,
        interval: Int,
        startDate: Instant,
        note: String,
    ): String = listOf(
        kind.raw,
        CsvFormat.plain(amount),
        normalized(sourceName.orEmpty()),
        normalized(destinationName.orEmpty()),
        frequency.raw,
        interval.toString(),
        CsvFormat.exportDate.format(startDate.atZone(zone)),
        roundTripped(note),
    ).joinToString(KEY_SEPARATOR)

    /** The exporter's escaping followed by the importer's trimming. */
    private fun roundTripped(text: String): String = CsvFormat.escaped(text).trim()

    private fun normalized(name: String): String = name.trim().lowercase()

    private fun categoryKey(name: String, kind: CategoryKind): String = "${kind.raw}:${normalized(name)}"

    private companion object {
        const val KEY_SEPARATOR = "\u001F"
    }
}
