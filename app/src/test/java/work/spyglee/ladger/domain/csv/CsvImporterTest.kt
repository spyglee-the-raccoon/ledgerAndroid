package work.spyglee.ladger.domain.csv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.UTC
import work.spyglee.ladger.domain.assertAmount
import work.spyglee.ladger.domain.day

class CsvImporterTest {
    private val cash = Source(id = 1, name = "Cash")
    private val bankCard = Source(id = 2, name = "Bank Card")
    private val groceries = Category(id = 10, name = "Groceries", kind = CategoryKind.EXPENSE)

    private fun importer() = CsvImporter(listOf(cash, bankCard), listOf(groceries), UTC)

    @Test
    fun `imports expenses, matching existing names case-insensitively`() = runTest {
        val sink = FakeSink()
        val result = importer().import(
            listOf(
                CsvImporter.Input(
                    "expenses.csv",
                    """
                    date;amount;source;category;details
                    01.10.2026;12,49;Cash;Groceries;Konzum
                    2.10.2026.;1.234,50;bank card;Rent;
                    03.10.2026;-5.5;Cash;groceries;refund
                    """.trimIndent(),
                )
            ),
            sink,
        )

        assertEquals(3, result.importedCount)
        assertEquals(0, result.createdSources)
        assertEquals(1, result.createdCategories)
        assertEquals(0, result.skippedCount)
        assertEquals(listOf("Rent"), sink.categories.map { it.name })
        assertEquals(CategoryKind.EXPENSE, sink.categories.single().kind)

        val (first, second, third) = sink.transactions
        assertTrue(sink.transactions.all { it.kind == TransactionKind.EXPENSE })
        assertAmount("12.49", first.amount)
        assertEquals(day(2026, 10, 1), first.date)
        assertEquals(1L, first.sourceId)
        assertEquals(10L, first.categoryId)
        assertEquals("Konzum", first.note)
        assertAmount("1234.50", second.amount)
        assertEquals(day(2026, 10, 2), second.date)
        assertEquals(2L, second.sourceId)
        assertEquals(sink.categories.single().id, second.categoryId)
        assertAmount("5.5", third.amount)
        assertEquals(10L, third.categoryId)
    }

    @Test
    fun `imports incomes and transfers, creating each missing source once`() = runTest {
        val sink = FakeSink()
        val result = importer().import(
            listOf(
                CsvImporter.Input(
                    "incomes.csv",
                    "date;amount;destination;category;details\n01.10.2026;2000;Revolut;Salary;\n15.10.2026;100;revolut;Groceries;refund\n",
                ),
                CsvImporter.Input("transfers.csv", "date;amount;source;target;details\n05.10.2026;300;Revolut;Cash;ATM\n"),
            ),
            sink,
        )

        assertEquals(3, result.importedCount)
        assertEquals(1, result.createdSources)
        assertEquals(listOf("Revolut"), sink.sources.map { it.name })
        // "Groceries" exists only as an expense category, so an income category is created for it.
        assertEquals(listOf("Salary" to CategoryKind.INCOME, "Groceries" to CategoryKind.INCOME), sink.categories.map { it.name to it.kind })

        val revolut = sink.sources.single().id
        val (salary, refund, atm) = sink.transactions
        assertEquals(TransactionKind.INCOME, salary.kind)
        assertEquals(revolut, salary.destinationId)
        assertNull(salary.sourceId)
        assertEquals(revolut, refund.destinationId)
        assertEquals(TransactionKind.TRANSFER, atm.kind)
        assertEquals(revolut, atm.sourceId)
        assertEquals(1L, atm.destinationId)
        assertNull(atm.categoryId)
    }

    @Test
    fun `reports unreadable lines by file line number`() = runTest {
        val sink = FakeSink()
        val text = "﻿date;amount;source;category;details\r\n\r\n" +
            "01.10.2026;10;Cash;Groceries;\r\n" +
            "not a date;10;Cash;Groceries;\r\n" +
            "02.10.2026;abc;Cash;Groceries;\r\n" +
            "03.10.2026;10;;Groceries;\r\n"
        val result = importer().import(listOf(CsvImporter.Input("expenses.csv", text)), sink)

        assertEquals(1, result.importedCount)
        assertEquals(mapOf("expenses.csv" to listOf(4, 5, 6)), result.skippedLines)
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun `accepts quoted fields and any column order`() = runTest {
        val sink = FakeSink()
        importer().import(
            listOf(CsvImporter.Input("x.csv", "\"Category\";\"Source\";\"Amount\";\"Date\"\n\"Groceries\";\"Cash\";\"7,00\";\"09.10.2026\"\n")),
            sink,
        )
        val transaction = sink.transactions.single()
        assertAmount("7", transaction.amount)
        assertEquals(10L, transaction.categoryId)
        assertEquals("", transaction.note)
    }

    @Test
    fun `an unknown file format imports nothing`() = runTest {
        val sink = FakeSink()
        try {
            importer().import(
                listOf(
                    CsvImporter.Input("expenses.csv", "date;amount;source;category\n01.10.2026;1;Cash;Groceries\n"),
                    CsvImporter.Input("notes.csv", "date;amount;memo\n01.10.2026;1;hello\n"),
                ),
                sink,
            )
            fail("expected an exception")
        } catch (e: CsvImportException) {
            assertEquals(CsvImportException.Reason.UNKNOWN_FORMAT, e.reason)
            assertEquals("notes.csv", e.fileName)
        }
        assertTrue(sink.transactions.isEmpty())
    }

    @Test
    fun `an empty file is an unknown format`() = runTest {
        try {
            importer().import(listOf(CsvImporter.Input("empty.csv", "\n  \n")), FakeSink())
            fail("expected an exception")
        } catch (e: CsvImportException) {
            assertEquals(CsvImportException.Reason.UNKNOWN_FORMAT, e.reason)
        }
    }
}
