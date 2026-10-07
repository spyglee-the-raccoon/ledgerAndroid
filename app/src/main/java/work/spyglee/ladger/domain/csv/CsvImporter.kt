package work.spyglee.ladger.domain.csv

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.lineBreak

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

/** Where imported data is written; implemented on top of the database inside one transaction. */
interface CsvImportSink {
    suspend fun createSource(source: Source): Long
    suspend fun createCategory(category: Category): Long
    suspend fun insertTransaction(transaction: Transaction)
}

/**
 * Imports transactions from the CSV files [CsvExporter] writes (and the iOS app reads).
 * The transaction kind is detected from each file's header. Sources and categories are matched
 * by name, ignoring case, and created when missing.
 */
class CsvImporter(
    existingSources: List<Source>,
    existingCategories: List<Category>,
    private val zone: ZoneId,
) {
    data class Input(val fileName: String, val text: String)

    private val sourceIds = existingSources.associate { normalized(it.name) to it.id }.toMutableMap()
    private val categoryIds = existingCategories.associate { categoryKey(it.name, it.kind) to it.id }.toMutableMap()

    private var createdSources = 0
    private var createdCategories = 0

    /**
     * Every file is parsed before anything is written, so a file in an unknown format imports nothing.
     * Call once per importer: it remembers the sources and categories it created.
     */
    suspend fun import(inputs: List<Input>, sink: CsvImportSink): CsvImportResult {
        val parsed = inputs.map { it.fileName to parse(it.text, it.fileName) }

        var imported = 0
        val skipped = linkedMapOf<String, MutableList<Int>>()
        for ((fileName, lines) in parsed) {
            for (line in lines) {
                val row = line.row
                if (row == null) {
                    skipped.getOrPut(fileName) { mutableListOf() } += line.number
                    continue
                }
                sink.insertTransaction(
                    Transaction(
                        amount = row.amount,
                        kind = row.kind,
                        sourceId = row.sourceName?.let { sourceId(it, sink) },
                        destinationId = row.destinationName?.let { sourceId(it, sink) },
                        note = row.note,
                        date = row.date,
                        categoryId = row.categoryName?.let { categoryId(it, row.kind, sink) },
                    )
                )
                imported++
            }
        }
        return CsvImportResult(imported, createdSources, createdCategories, skipped)
    }

    private suspend fun sourceId(name: String, sink: CsvImportSink): Long {
        val key = normalized(name)
        sourceIds[key]?.let { return it }
        return sink.createSource(Source(name = name)).also {
            sourceIds[key] = it
            createdSources++
        }
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

    private class Row(
        val kind: TransactionKind,
        val date: Instant,
        val amount: BigDecimal,
        val sourceName: String?,
        val destinationName: String?,
        val categoryName: String?,
        val note: String,
    )

    /** A data line; [row] is null when the line couldn't be read. */
    private class Line(val number: Int, val row: Row?)

    private fun parse(text: String, fileName: String): List<Line> {
        val lines = text.replace("﻿", "")
            .split(lineBreak)
            .withIndex()
            .filter { it.value.isNotBlank() }

        val header = lines.firstOrNull() ?: throw CsvImportException(CsvImportException.Reason.UNKNOWN_FORMAT, fileName)
        val columns = fields(header.value).map { it.lowercase() }
        fun index(name: String): Int? = columns.indexOf(name).takeIf { it >= 0 }

        val dateIndex = index("date")
        val amountIndex = index("amount")
        if (dateIndex == null || amountIndex == null) {
            throw CsvImportException(CsvImportException.Reason.UNKNOWN_FORMAT, fileName)
        }
        val detailsIndex = index("details")

        val kind: TransactionKind
        val sourceIndex: Int?
        val destinationIndex: Int?
        val categoryIndex: Int?
        when {
            index("source") != null && index("target") != null -> {
                kind = TransactionKind.TRANSFER
                sourceIndex = index("source"); destinationIndex = index("target"); categoryIndex = null
            }
            index("destination") != null && index("category") != null -> {
                kind = TransactionKind.INCOME
                sourceIndex = null; destinationIndex = index("destination"); categoryIndex = index("category")
            }
            index("source") != null && index("category") != null -> {
                kind = TransactionKind.EXPENSE
                sourceIndex = index("source"); destinationIndex = null; categoryIndex = index("category")
            }
            else -> throw CsvImportException(CsvImportException.Reason.UNKNOWN_FORMAT, fileName)
        }

        return lines.drop(1).map { (offset, line) ->
            val fields = fields(line)
            fun value(index: Int?): String? = index?.let { fields.getOrNull(it) }?.takeIf { it.isNotEmpty() }

            val date = value(dateIndex)?.let(::parseDate)
            val amount = value(amountIndex)?.let(::parseAmount)
            val sourceName = value(sourceIndex)
            val destinationName = value(destinationIndex)
            val row = if (date == null || amount == null || (sourceName == null && destinationName == null)) {
                null
            } else {
                Row(kind, date, amount, sourceName, destinationName, value(categoryIndex), value(detailsIndex).orEmpty())
            }
            Line(offset + 1, row)
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

    /** Accepts "12,49", "1.234,56" and "12.49"; the sign is dropped because the kind comes from the file. */
    private fun parseAmount(string: String): BigDecimal? {
        var cleaned = string.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        if (cleaned.contains(',')) {
            cleaned = cleaned.replace(".", "").replace(",", ".")
        }
        return cleaned.toBigDecimalOrNull()?.abs()
    }

    private fun normalized(name: String): String = name.trim().lowercase()

    private fun categoryKey(name: String, kind: CategoryKind): String = "${kind.raw}:${normalized(name)}"
}
