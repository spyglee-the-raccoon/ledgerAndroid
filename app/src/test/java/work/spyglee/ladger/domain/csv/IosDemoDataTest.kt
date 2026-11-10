package work.spyglee.ladger.domain.csv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import work.spyglee.ladger.domain.UTC
import work.spyglee.ladger.domain.day

/**
 * The iOS repository's `DemoData` (a year of data in all CSV files, written by `DemoData/generate.py` in
 * iOS `8a76b5d`), copied to `src/test/resources/ios-demo-data`. Android must import every line, and
 * exporting the result must give back the same lines, so the format stays the same on both platforms.
 */
class IosDemoDataTest {
    private val files = listOf(
        "accounts.csv", "budgets.csv", "categories.csv", "collection_transactions.csv", "collections.csv",
        "expenses.csv", "incomes.csv", "planned_transactions.csv", "receipt_items.csv", "transfers.csv",
    )

    private fun read(name: String): String =
        CsvText.decode(checkNotNull(javaClass.getResourceAsStream("/ios-demo-data/$name")) { name }.use { it.readBytes() })

    private fun lines(text: String): List<String> = text.lines().filter { it.isNotBlank() }

    @Test
    fun `imports every line and exports the same lines again`() = runTest {
        val inputs = files.map { CsvImporter.Input(it, read(it)) }
        val sink = FakeSink()
        val result = CsvImporter(emptyList(), emptyList(), UTC, now = day(2026, 11, 10)).import(inputs, sink)

        assertEquals(emptyMap<String, List<Int>>(), result.skippedLines)
        val rows = inputs.associate { it.fileName to lines(it.text).size - 1 }
        assertEquals(rows.getValue("expenses.csv") + rows.getValue("incomes.csv") + rows.getValue("transfers.csv"), result.importedCount)
        assertEquals(rows.getValue("accounts.csv"), sink.sources.size)
        assertEquals(rows.getValue("categories.csv"), sink.categories.size)
        assertEquals(rows.getValue("budgets.csv"), sink.budgets.size)
        assertEquals(rows.getValue("planned_transactions.csv"), sink.planned.size)
        assertEquals(rows.getValue("collections.csv"), sink.collections.size)
        assertEquals(rows.getValue("collection_transactions.csv"), sink.memberships.size)
        assertEquals(rows.getValue("receipt_items.csv"), sink.receiptItems.size)

        val exported = CsvExporter.export(
            transactions = sink.transactions,
            sources = sink.sources,
            categories = sink.categories,
            zone = UTC,
            plannedTransactions = sink.planned,
            budgets = sink.budgets,
            collections = sink.collections,
            collectionTransactions = sink.memberships,
            receiptItems = sink.receiptItems,
        ).associate { it.fileName to it.text }

        assertEquals(files.toSet(), exported.keys)
        for (input in inputs) {
            // The generator's row order isn't always the exporter's, so the lines are compared as sets.
            val expected = lines(input.text)
            val actual = lines(exported.getValue(input.fileName))
            assertEquals(input.fileName, expected.first(), actual.first())
            assertEquals(input.fileName, expected.sorted(), actual.sorted())
        }
    }
}
