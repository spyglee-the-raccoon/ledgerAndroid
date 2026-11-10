package work.spyglee.ladger.domain.csv

import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.CollectionTransaction
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.ReceiptItem
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionCollection
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.UTC
import work.spyglee.ladger.domain.assertAmount
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
        assertEquals(listOf("incomes.csv", "transfers.csv", "accounts.csv", "categories.csv"), files.map { it.fileName })
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
            emptyList(), UTC,
        )
        assertEquals(
            listOf(CsvFile("accounts.csv", "name;initial_value;icon;color\nCash, wallet;12.5;creditcard;blue\nSavings;1000;building.columns;indigo\n")),
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
            files.single { it.fileName == "planned_transactions.csv" },
        )
    }

    @Test
    fun `nothing to export writes no files`() {
        assertEquals(emptyList<CsvFile>(), CsvExporter.export(emptyList(), emptyList(), emptyList(), UTC))
    }

    @Test
    fun `the general budget has an empty category`() {
        val files = CsvExporter.export(
            emptyList(), emptyList(), categories, UTC,
            budgets = listOf(
                Budget(id = 1, monthlyLimit = BigDecimal("200.50"), isActive = false, categoryId = 10),
                Budget(id = 2, monthlyLimit = BigDecimal("500")),
            ),
        )
        assertEquals(
            CsvFile("budgets.csv", "category;limit;active\n;500;true\nGroceries;200.5;false\n"),
            files.single { it.fileName == "budgets.csv" },
        )
    }

    @Test
    fun `writes categories, collections and receipt items`() {
        val lunch = expense("12.5", sourceId = 1, categoryId = 10, date = day(2026, 10, 5), id = 1).copy(note = "Lunch; with \"friends\"")
        val atm = transfer("50", sourceId = 2, destinationId = 1, date = day(2026, 10, 2), id = 2).copy(note = "ATM")
        val files = CsvExporter.export(
            listOf(lunch, atm), sources, categories, UTC,
            collections = listOf(TransactionCollection(id = 5, name = "Trip", systemImage = "airplane", color = CategoryColor.TEAL)),
            collectionTransactions = listOf(CollectionTransaction(5, 1), CollectionTransaction(5, 2)),
            receiptItems = listOf(
                ReceiptItem(id = 9, transactionId = 1, name = "Juice", normalizedName = "juice", quantity = BigDecimal("2"),
                    unitPrice = BigDecimal("1.50"), totalPrice = BigDecimal("3.00"), position = 1),
                ReceiptItem(id = 8, transactionId = 1, name = "Soup", normalizedName = "soup",
                    unitPrice = BigDecimal("9.5"), totalPrice = BigDecimal("9.5"), position = 0),
            ),
        )
        val texts = files.associate { it.fileName to it.text }
        val reference = "transaction_date;transaction_kind;transaction_amount;transaction_source;transaction_destination;transaction_details"

        assertEquals(
            listOf(
                "transfers.csv", "expenses.csv", "accounts.csv", "categories.csv",
                "collections.csv", "collection_transactions.csv", "receipt_items.csv",
            ).sorted(),
            files.map { it.fileName }.sorted(),
        )
        assertEquals("name;kind;icon;color\nGroceries;expense;tag;primary\nSalary;income;tag;primary\n", texts["categories.csv"])
        assertEquals("name;icon;color\nTrip;airplane;teal\n", texts["collections.csv"])
        assertEquals(
            "collection;$reference\n" +
                "Trip;02.10.2026;transfer;50;Bank Card;Cash;ATM\n" +
                "Trip;05.10.2026;expense;12.5;Cash;;Lunch, with 'friends'\n",
            texts["collection_transactions.csv"],
        )
        assertEquals(
            "$reference;position;name;quantity;unit_price;total_price\n" +
                "05.10.2026;expense;12.5;Cash;;Lunch, with 'friends';0;Soup;1;9.5;9.5\n" +
                "05.10.2026;expense;12.5;Cash;;Lunch, with 'friends';1;Juice;2;1.5;3\n",
            texts["receipt_items.csv"],
        )
    }

    /** Exports everything and imports it into an empty database, as iOS's `CSVRoundTripTests`. */
    @Test
    fun `everything survives export and import`() = runTest {
        val cash = Source(id = 1, name = "Cash", systemImage = "banknote", initValue = BigDecimal("100"), color = CategoryColor.GREEN,
            createdAt = Instant.ofEpochSecond(1))
        val card = Source(id = 2, name = "Card", systemImage = "creditcard", initValue = BigDecimal("-20.5"), color = CategoryColor.RED,
            createdAt = Instant.ofEpochSecond(2))
        val food = Category(id = 10, name = "Food", systemImage = "fork.knife", color = CategoryColor.ORANGE)
        val salary = Category(id = 11, name = "Salary", systemImage = "briefcase", color = CategoryColor.MINT, kind = CategoryKind.INCOME)
        val lunch = expense("12.5", sourceId = 1, categoryId = 10, date = day(2026, 10, 5, hour = 13), id = 1).copy(note = "Lunch; with \"friends\"")
        val pay = income("2000", destinationId = 2, categoryId = 11, date = day(2026, 10, 1), id = 2).copy(note = "October")
        val atm = transfer("50", sourceId = 2, destinationId = 1, date = day(2026, 10, 2), id = 3).copy(note = "ATM")
        val export = CsvExporter.export(
            transactions = listOf(lunch, pay, atm),
            sources = listOf(cash, card),
            categories = listOf(food, salary),
            zone = UTC,
            plannedTransactions = listOf(
                PlannedTransaction(id = 1, amount = BigDecimal("2000"), kind = TransactionKind.INCOME, destinationId = 2, note = "Salary",
                    categoryId = 11, startDate = day(2100, 1, 1)),
            ),
            budgets = listOf(Budget(id = 1, monthlyLimit = BigDecimal("500")), Budget(id = 2, monthlyLimit = BigDecimal("200"), isActive = false, categoryId = 10)),
            collections = listOf(TransactionCollection(id = 1, name = "Trip", systemImage = "airplane", color = CategoryColor.TEAL)),
            collectionTransactions = listOf(CollectionTransaction(1, 1), CollectionTransaction(1, 3)),
            receiptItems = listOf(ReceiptItem(id = 1, transactionId = 1, name = "Soup", normalizedName = "soup",
                unitPrice = BigDecimal("12.5"), totalPrice = BigDecimal("12.5"))),
        )

        val sink = FakeSink()
        val result = CsvImporter(emptyList(), emptyList(), UTC).import(export.map { CsvImporter.Input(it.fileName, it.text) }, sink)

        assertEquals(3, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(
            listOf(Triple("Cash", "100", CategoryColor.GREEN), Triple("Card", "-20.5", CategoryColor.RED)),
            sink.sources.map { Triple(it.name, it.initValue.toPlainString(), it.color) },
        )
        assertEquals(listOf("banknote", "creditcard"), sink.sources.map { it.systemImage })
        assertEquals(
            listOf(Triple("Food", CategoryKind.EXPENSE, CategoryColor.ORANGE), Triple("Salary", CategoryKind.INCOME, CategoryColor.MINT)),
            sink.categories.map { Triple(it.name, it.kind, it.color) },
        )

        val transactions = sink.transactions.sortedBy { it.date }
        assertEquals(listOf(TransactionKind.INCOME, TransactionKind.TRANSFER, TransactionKind.EXPENSE), transactions.map { it.kind })
        assertEquals(listOf(null, "Card", "Cash"), transactions.map { sink.sourceName(it.sourceId) })
        assertEquals(listOf("Card", "Cash", null), transactions.map { sink.sourceName(it.destinationId) })
        assertEquals(listOf("Salary", null, "Food"), transactions.map { sink.categoryName(it.categoryId) })
        val importedLunch = transactions.last()
        assertEquals("Lunch, with 'friends'", importedLunch.note)
        assertEquals(listOf("Soup"), sink.receiptItems.filter { it.transactionId == importedLunch.id }.map { it.name })

        val trip = sink.collections.single()
        assertEquals("Trip", trip.name)
        assertEquals(setOf("ATM", "Lunch, with 'friends'"), sink.membersOf(trip.id).map { it.note }.toSet())

        assertEquals(2, sink.budgets.size)
        assertAmount("500", sink.budgets.single { it.isGeneral }.monthlyLimit)
        val foodBudget = sink.budgets.single { !it.isGeneral }
        assertEquals("Food", sink.categoryName(foodBudget.categoryId))
        assertEquals(false, foodBudget.isActive)

        val planned = sink.planned.single()
        assertAmount("2000", planned.amount)
        assertEquals(TransactionKind.INCOME, planned.kind)
        assertEquals("Card", sink.sourceName(planned.destinationId))
        assertEquals("Salary", sink.categoryName(planned.categoryId))
        assertEquals(day(2100, 1, 1), planned.startDate)
    }

    private val transactionFiles = setOf("expenses.csv", "incomes.csv", "transfers.csv")

    @Test
    fun `decodes UTF-8, then Windows-1250, then Latin-1`() {
        assertEquals("Režije", CsvText.decode("Režije".toByteArray(Charsets.UTF_8)))
        assertEquals("Režije", CsvText.decode("Režije".toByteArray(charset("windows-1250"))))
        assertEquals("\u0081", CsvText.decode(byteArrayOf(0x81.toByte())))
    }
}
