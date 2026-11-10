package work.spyglee.ladger.domain.csv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.domain.UTC
import work.spyglee.ladger.domain.assertAmount
import work.spyglee.ladger.domain.day
import work.spyglee.ladger.domain.expense

/** The files besides transactions that iOS imports since `8a76b5d`; ported from iOS's `CSVTests`. */
class CsvFileTypesTest {
    /** Imports into [sink], with what it already holds as the existing data. */
    private suspend fun importInto(sink: FakeSink, vararg files: Pair<String, String>): CsvImportResult =
        CsvImporter(
            sink.sources.toList(), sink.categories.toList(), UTC,
            sink.collections.toList(), sink.budgets.toList(), sink.planned.toList(),
            now = NOW,
        ).import(files.map { (name, text) -> CsvImporter.Input(name, text) }, sink)

    @Test
    fun `parses amount formats`() = runTest {
        val cases = listOf("12,50" to "12.5", "12.50" to "12.5", "1.234,56" to "1234.56", "-45,00" to "45", "€ 7,99" to "7.99")
        for ((raw, expected) in cases) {
            val sink = FakeSink()
            importInto(sink, "expenses.csv" to "date;amount;source;category;details\n01.10.2026;$raw;Cash;Food;\n")
            assertAmount(expected, sink.transactions.single().amount)
        }
    }

    @Test
    fun `the accounts file sets initial value, icon and color`() = runTest {
        val sink = FakeSink()
        val result = importInto(sink, "accounts.csv" to "name;initial_value;icon;color\nSavings;-1.500,25;banknote;green\nWallet;;;not-a-color\n")

        assertEquals(2, result.createdSources)
        val (savings, wallet) = sink.sources
        assertEquals("Savings", savings.name)
        assertAmount("-1500.25", savings.initValue) // initial values keep their sign
        assertEquals("banknote", savings.systemImage)
        assertEquals(CategoryColor.GREEN, savings.color)
        assertAmount("0", wallet.initValue)
        assertEquals(Source(name = "x").systemImage, wallet.systemImage)
        assertEquals(Source(name = "x").color, wallet.color)
    }

    @Test
    fun `accounts are imported before transactions whatever the file order`() = runTest {
        val sink = FakeSink()
        val result = importInto(
            sink,
            "expenses.csv" to "date;amount;source;category;details\n05.10.2026;10;Savings;Food;\n",
            "accounts.csv" to "name;initial_value;icon;color\nSavings;100;banknote;green\n",
        )
        assertEquals(1, result.createdSources)
        val savings = sink.sources.single()
        assertEquals(savings.id, sink.transactions.single().sourceId)
        assertAmount("100", savings.initValue)
        assertEquals(CategoryColor.GREEN, savings.color)
    }

    @Test
    fun `the categories file needs a valid kind`() = runTest {
        val sink = FakeSink()
        val result = importInto(
            sink,
            "categories.csv" to "name;kind;icon;color\nFood;expense;fork.knife;orange\nSalary;income;briefcase;green\nBroken;spending;tag;red\n",
        )
        assertEquals(2, result.createdCategories)
        assertEquals(mapOf("categories.csv" to listOf(4)), result.skippedLines)
        assertEquals(
            listOf(Triple("Food", CategoryKind.EXPENSE, CategoryColor.ORANGE), Triple("Salary", CategoryKind.INCOME, CategoryColor.GREEN)),
            sink.categories.map { Triple(it.name, it.kind, it.color) },
        )
    }

    @Test
    fun `budgets link to expense categories`() = runTest {
        val sink = FakeSink()
        importInto(sink, "budgets.csv" to "category;limit;active\n;500;true\nFood;200;false\n")

        val general = sink.budgets.single { it.isGeneral }
        val food = sink.budgets.single { !it.isGeneral }
        assertAmount("500", general.monthlyLimit)
        assertTrue(general.isActive)
        assertAmount("200", food.monthlyLimit)
        assertFalse(food.isActive)
        val category = sink.categories.single { it.id == food.categoryId }
        assertEquals("Food" to CategoryKind.EXPENSE, category.name to category.kind)
    }

    @Test
    fun `planned transactions count past occurrences as recorded`() = runTest {
        val sink = FakeSink()
        importInto(
            sink,
            "planned_transactions.csv" to """
                kind;amount;source;destination;category;frequency;interval;start_date;end_date;paused;details
                expense;9,99;Card;;Streaming;monthly;1;01.01.2020;31.12.2020;false;Netflix
                income;100;;Bank;Salary;weekly;2;01.01.2100;;true;Future
            """.trimIndent(),
        )

        val (netflix, future) = sink.planned
        assertEquals(12, netflix.occurrencesGenerated)
        assertEquals("Netflix", netflix.note)
        assertEquals("Card", sink.sourceName(netflix.sourceId))
        assertEquals("Streaming", sink.categoryName(netflix.categoryId))
        assertEquals(day(2020, 12, 31), netflix.endDate)
        assertEquals(0, future.occurrencesGenerated)
        assertEquals(2, future.interval)
        assertEquals(RecurrenceFrequency.WEEKLY, future.frequency)
        assertTrue(future.isPaused)
        assertEquals("Bank", sink.sourceName(future.destinationId))
    }

    @Test
    fun `paused plans count past occurrences as recorded too`() = runTest {
        val sink = FakeSink()
        importInto(sink, "planned_transactions.csv" to "kind;amount;source;frequency;start_date;paused\nexpense;5;Cash;monthly;15.08.2026;true\n")
        // 15 August, September and October are before NOW (1 November 2026).
        assertEquals(3, sink.planned.single().occurrencesGenerated)
    }

    @Test
    fun `planned transactions need an account and a known frequency`() = runTest {
        val sink = FakeSink()
        val result = importInto(
            sink,
            "planned_transactions.csv" to """
                kind;amount;source;destination;category;frequency;interval;start_date;end_date;paused;details
                expense;10;;;Food;monthly;1;01.01.2100;;false;
                expense;10;Cash;;Food;fortnightly;1;01.01.2100;;false;
            """.trimIndent(),
        )
        assertEquals(mapOf("planned_transactions.csv" to listOf(2, 3)), result.skippedLines)
        assertTrue(sink.sources.isEmpty())
    }

    @Test
    fun `importing again doesn't duplicate accounts, categories, budgets or plans`() = runTest {
        val files = arrayOf(
            "accounts.csv" to "name;initial_value;icon;color\nCash;0;banknote;green\n",
            "categories.csv" to "name;kind;icon;color\nFood;expense;fork.knife;orange\n",
            "budgets.csv" to "category;limit;active\nFood;200;true\n",
            "planned_transactions.csv" to "kind;amount;source;destination;category;frequency;interval;start_date;end_date;paused;details\n" +
                "expense;10;Cash;;Food;monthly;1;01.01.2100;;false;Gym\n",
        )
        val sink = FakeSink()
        importInto(sink, *files)
        val second = importInto(sink, *files)

        assertEquals(0, second.createdSources)
        assertEquals(0, second.createdCategories)
        assertEquals(0, second.skippedCount)
        assertEquals(1, sink.sources.size)
        assertEquals(1, sink.categories.size)
        assertEquals(1, sink.budgets.size)
        assertEquals(1, sink.planned.size)
    }

    @Test
    fun `collection rows link matching transactions`() = runTest {
        val sink = FakeSink()
        val result = importInto(
            sink,
            "expenses.csv" to """
                date;amount;source;category;details
                05.10.2026;10;Cash;Food;Coffee
                05.10.2026;10;Cash;Food;Coffee
                06.10.2026;99;Cash;Food;Hotel
            """.trimIndent(),
            "collections.csv" to "name;icon;color\nTrip;airplane;teal\n",
            "collection_transactions.csv" to """
                collection;transaction_date;transaction_kind;transaction_amount;transaction_source;transaction_destination;transaction_details
                Trip;05.10.2026;expense;10;Cash;;Coffee
                Trip;05.10.2026;expense;10;Cash;;Coffee
                Trip;05.10.2026;expense;10;Cash;;Coffee
                Trip;07.10.2026;expense;10;Cash;;Missing
            """.trimIndent(),
        )

        val trip = sink.collections.single()
        assertEquals(Triple("Trip", "airplane", CategoryColor.TEAL), Triple(trip.name, trip.systemImage, trip.color))
        // The two identical coffees are added once each; the third line is a duplicate.
        val members = sink.membersOf(trip.id)
        assertEquals(2, members.size)
        assertEquals(2, members.map { it.id }.toSet().size)
        assertTrue(members.all { it.note == "Coffee" })
        assertEquals(mapOf("collection_transactions.csv" to listOf(5)), result.skippedLines)
    }

    @Test
    fun `receipt items attach to their transaction`() = runTest {
        val sink = FakeSink()
        val result = importInto(
            sink,
            "expenses.csv" to "date;amount;source;category;details\n05.10.2026;3,48;Cash;Food;Konzum\n",
            "receipt_items.csv" to """
                transaction_date;transaction_kind;transaction_amount;transaction_source;transaction_destination;transaction_details;position;name;quantity;unit_price;total_price
                05.10.2026;expense;3.48;Cash;;Konzum;0;MLIJEKO 1L;2;1,29;2,58
                05.10.2026;expense;3.48;Cash;;Konzum;1;KRUH;;;0,90
                05.10.2026;expense;3.48;Cash;;Konzum;1;KRUH;;;0,90
                05.10.2026;expense;9.99;Cash;;Other;0;ORPHAN;1;1;1
            """.trimIndent(),
        )

        val transactionId = sink.transactions.single().id
        assertEquals(2, sink.receiptItems.size)
        assertTrue(sink.receiptItems.all { it.transactionId == transactionId })
        val (milk, bread) = sink.receiptItems.sortedBy { it.position }
        assertEquals("MLIJEKO 1L", milk.name)
        assertEquals("mlijeko 1l", milk.normalizedName)
        assertAmount("2", milk.quantity)
        assertAmount("1.29", milk.unitPrice)
        assertAmount("2.58", milk.totalPrice)
        assertAmount("1", bread.quantity) // a missing quantity is one
        assertAmount("0.90", bread.totalPrice)
        assertEquals(mapOf("receipt_items.csv" to listOf(5)), result.skippedLines)
    }

    @Test
    fun `rows can refer to transactions already in the database`() = runTest {
        val existing = expense("10", sourceId = 1, date = day(2026, 10, 5, hour = 9), id = 7).copy(note = "Coffee")
        val sink = FakeSink(sources = mutableListOf(Source(id = 1, name = "Cash")), existingTransactions = listOf(existing))
        importInto(
            sink,
            "collection_transactions.csv" to """
                collection;transaction_date;transaction_kind;transaction_amount;transaction_source;transaction_destination;transaction_details
                Morning;05.10.2026;expense;10;cash;;Coffee
            """.trimIndent(),
        )

        val morning = sink.collections.single()
        assertEquals("Morning", morning.name)
        assertEquals(listOf(existing), sink.membersOf(morning.id))
        assertTrue(sink.imported.isEmpty())
    }

    @Test
    fun `rows prefer transactions imported in the same run`() = runTest {
        val existing = expense("10", sourceId = 1, date = day(2026, 10, 5), id = 7).copy(note = "Coffee")
        val sink = FakeSink(sources = mutableListOf(Source(id = 1, name = "Cash")), existingTransactions = listOf(existing))
        importInto(
            sink,
            "expenses.csv" to "date;amount;source;category;details\n05.10.2026;10;Cash;;Coffee\n",
            "receipt_items.csv" to "transaction_date;transaction_kind;transaction_amount;transaction_source;transaction_destination;transaction_details;name\n" +
                "05.10.2026;expense;10;Cash;;Coffee;ESPRESSO\n",
        )
        assertEquals(sink.imported.single().id, sink.receiptItems.single().transactionId)
    }

    private companion object {
        val NOW = day(2026, 11, 1, hour = 12)
    }
}
