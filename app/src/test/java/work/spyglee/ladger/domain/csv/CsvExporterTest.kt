package work.spyglee.ladger.domain.csv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
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
            listOf(
                CsvFile(
                    "expenses.csv",
                    "date;amount;source;category;details\n" +
                        "01.10.2026;12.5;Cash;Groceries;Konzum, big shop 'x'\n" +
                        "02.10.2026;3;Bank Card;;\n",
                )
            ),
            files,
        )
    }

    @Test
    fun `one file per kind that has transactions`() {
        val files = CsvExporter.export(
            listOf(transfer("5", 1, 2), income("100", destinationId = 2, categoryId = 11)),
            sources, categories, UTC,
        )
        assertEquals(listOf("incomes.csv", "transfers.csv"), files.map { it.fileName })
        assertEquals("date;amount;source;target;details\n01.10.2026;5;Cash;Bank Card;\n", files[1].text)
    }

    @Test
    fun `export then import gives back the same transactions`() = runTest {
        val original = listOf(
            expense("12.49", sourceId = 1, categoryId = 10, date = day(2026, 9, 30)).copy(note = "Konzum"),
            income("2000", destinationId = 2, categoryId = 11, date = day(2026, 10, 1)),
            transfer("300.10", sourceId = 2, destinationId = 1, date = day(2026, 10, 2)).copy(note = "ATM"),
        )
        val files = CsvExporter.export(original, sources, categories, UTC)

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
    fun `decodes UTF-8, then Windows-1250, then Latin-1`() {
        assertEquals("Režije", CsvText.decode("Režije".toByteArray(Charsets.UTF_8)))
        assertEquals("Režije", CsvText.decode("Režije".toByteArray(charset("windows-1250"))))
        assertEquals("\u0081", CsvText.decode(byteArrayOf(0x81.toByte())))
    }
}
