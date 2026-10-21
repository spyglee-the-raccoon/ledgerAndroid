package work.spyglee.ladger.domain.csv

import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.UTC
import work.spyglee.ladger.domain.day
import work.spyglee.ladger.domain.expense
import work.spyglee.ladger.domain.income
import work.spyglee.ladger.domain.transfer

class CsvExporterTest {
    private val sources = listOf(Source(id = 1, name = "Cash"), Source(id = 2, name = "Bank Card"))
    private val categories = listOf(
        Category(id = 10, name = "Groceries"),
        Category(id = 11, name = "Salary", kind = CategoryKind.INCOME),
    )

    @Test
    fun `writes the iOS layout and replaces separators in values`() {
        val files = CsvExporter.export(
            listOf(
                expense("3", sourceId = 2, date = day(2026, 10, 2), id = 2),
                expense("12.50", sourceId = 1, categoryId = 10, date = day(2026, 10, 1), id = 1)
                    .copy(note = "Konzum; big\nshop \"x\""),
            ),
            sources, categories, UTC,
        )
        assertEquals(
            CsvFile(
                "expenses.csv",
                "date;amount;source;category;details\n" +
                    "01.10.2026;12.5;Cash;Groceries;Konzum, big shop 'x'\n" +
                    "02.10.2026;3;Bank Card;;\n",
            ),
            files.first(),
        )
    }

    @Test
    fun `one file per kind that has transactions`() {
        val files = CsvExporter.export(
            listOf(transfer("5", 1, 2), income("100", destinationId = 2, categoryId = 11)),
            sources, categories, UTC,
        )
        assertEquals(listOf("incomes.csv", "transfers.csv", "sources.csv"), files.map { it.fileName })
        assertEquals("date;amount;source;target;details\n01.10.2026;5;Cash;Bank Card;\n", files[1].text)
    }

    @Test
    fun `export then import gives back the same transactions`() = runTest {
        val original = listOf(
            expense("12.49", sourceId = 1, categoryId = 10, date = day(2026, 9, 30)).copy(note = "Konzum"),
            income("2000", destinationId = 2, categoryId = 11, date = day(2026, 10, 1)),
            transfer("300.10", sourceId = 2, destinationId = 1, date = day(2026, 10, 2)).copy(note = "ATM"),
        )
        val files = CsvExporter.export(original, sources, categories, UTC).filter { it.fileName in transactionFiles }

        val sink = FakeSink()
        val result = CsvImporter(sources, categories, UTC)
            .import(files.map { CsvImporter.Input(it.fileName, it.text) }, sink)

        assertEquals(0, result.createdSources + result.createdCategories + result.skippedCount)
        val normalize = { list: List<Transaction> ->
            list.map { it.copy(id = 0, amount = it.amount.stripTrailingZeros()) }.sortedBy { it.date }
        }
        assertEquals(normalize(original), normalize(sink.transactions))
    }

    @Test
    fun `lists sources by creation date`() {
        val files = CsvExporter.export(
            emptyList(),
            listOf(
                Source(id = 1, name = "Savings", systemImage = "building.columns", initValue = BigDecimal("1000.00"),
                    color = CategoryColor.INDIGO, createdAt = Instant.ofEpochSecond(20)),
                Source(id = 2, name = "Cash; wallet", initValue = BigDecimal("12.50"), createdAt = Instant.ofEpochSecond(10)),
            ),
            categories, UTC,
        )
        assertEquals(
            listOf(CsvFile("sources.csv", "name;initial_value;icon;color\nCash, wallet;12.5;creditcard;blue\nSavings;1000;building.columns;indigo\n")),
            files,
        )
    }

    @Test
    fun `lists planned transactions by start date`() {
        val netflix = PlannedTransaction(
            id = 1, amount = BigDecimal("12.99"), kind = TransactionKind.EXPENSE, sourceId = 1, note = "Netflix",
            categoryId = 10, startDate = day(2026, 10, 5), occurrencesGenerated = 1,
        )
        val savings = PlannedTransaction(
            id = 2, amount = BigDecimal("200"), kind = TransactionKind.TRANSFER, sourceId = 1, destinationId = 2,
            frequency = RecurrenceFrequency.WEEKLY, interval = 2, startDate = day(2026, 9, 1), endDate = day(2027, 9, 1),
            isPaused = true,
        )
        val files = CsvExporter.export(emptyList(), sources, categories, UTC, listOf(netflix, savings))
        assertEquals(
            CsvFile(
                "planned_transactions.csv",
                "kind;amount;source;destination;category;frequency;interval;start_date;end_date;paused;details\n" +
                    "transfer;200;Cash;Bank Card;;weekly;2;01.09.2026;01.09.2027;true;\n" +
                    "expense;12.99;Cash;;Groceries;monthly;1;05.10.2026;;false;Netflix\n",
            ),
            files.last(),
        )
    }

    @Test
    fun `importing the sources file fails, as on iOS`() = runTest {
        val sourcesFile = CsvExporter.export(emptyList(), sources, categories, UTC).single()
        val error = runCatching {
            CsvImporter(sources, categories, UTC).import(listOf(CsvImporter.Input(sourcesFile.fileName, sourcesFile.text)), FakeSink())
        }.exceptionOrNull()
        assertEquals(CsvImportException.Reason.UNKNOWN_FORMAT, (error as CsvImportException).reason)
    }

    private val transactionFiles = setOf("expenses.csv", "incomes.csv", "transfers.csv")

    @Test
    fun `decodes UTF-8, then Windows-1250, then Latin-1`() {
        assertEquals("Režije", CsvText.decode("Režije".toByteArray(Charsets.UTF_8)))
        assertEquals("Režije", CsvText.decode("Režije".toByteArray(charset("windows-1250"))))
        assertEquals("\u0081", CsvText.decode(byteArrayOf(0x81.toByte())))
    }
}
