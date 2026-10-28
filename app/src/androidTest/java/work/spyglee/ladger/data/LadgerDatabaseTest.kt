package work.spyglee.ladger.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.domain.PlannedTransactionDraft
import work.spyglee.ladger.domain.csv.CsvImportException
import work.spyglee.ladger.domain.csv.CsvImporter
import work.spyglee.ladger.domain.receipt.ParsedReceiptItem

@RunWith(AndroidJUnit4::class)
class LadgerDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: LadgerDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingSourceOrCategoryKeepsTransactions() = runTest {
        val cash = db.sourceDao().insert(Source(name = "Cash"))
        val bank = db.sourceDao().insert(Source(name = "Bank"))
        val food = db.categoryDao().insert(Category(name = "Food"))
        val expenseId = db.transactionDao().insert(
            Transaction(amount = BigDecimal("12.49"), kind = TransactionKind.EXPENSE, sourceId = cash, categoryId = food)
        )
        val transferId = db.transactionDao().insert(
            Transaction(amount = BigDecimal("5"), kind = TransactionKind.TRANSFER, sourceId = bank, destinationId = cash)
        )

        db.sourceDao().delete(db.sourceDao().getAll().first { it.id == cash })
        db.categoryDao().delete(db.categoryDao().getAll().single())

        val expense = db.transactionDao().getById(expenseId)!!
        assertNull(expense.sourceId)
        assertNull(expense.categoryId)
        assertEquals(0, BigDecimal("12.49").compareTo(expense.amount))
        val transfer = db.transactionDao().getById(transferId)!!
        assertEquals(bank, transfer.sourceId)
        assertNull(transfer.destinationId)
    }

    @Test
    fun deletingCategoryRemovesItsMerchantMappings() = runTest {
        val cash = db.sourceDao().insert(Source(name = "Cash"))
        val food = db.categoryDao().insert(Category(name = "Food"))
        val merchants = MerchantRepository(db.merchantCategoryDao())
        merchants.learn("Konzum d.o.o.", food, cash)

        db.sourceDao().delete(db.sourceDao().getAll().single())
        val mapping = merchants.learned("KONZUM")!!
        assertEquals(food, mapping.categoryId)
        assertNull(mapping.sourceId)

        db.categoryDao().delete(db.categoryDao().getAll().single())
        assertNull(merchants.learned("Konzum"))
    }

    @Test
    fun merchantLearningUpdatesExistingKeyAndMatchesPrefixes() = runTest {
        val food = db.categoryDao().insert(Category(name = "Food"))
        val shop = db.categoryDao().insert(Category(name = "Shopping"))
        val merchants = MerchantRepository(db.merchantCategoryDao())

        merchants.learn("Lidl Hrvatska d.o.o. k.d.", food, null)
        merchants.learn("LIDL HRVATSKA k.d.", shop, null)

        assertEquals(1, db.merchantCategoryDao().getAllRecentFirst().size)
        assertEquals(shop, merchants.learned("lidl hrvatska kd")?.categoryId)
        assertEquals(shop, merchants.learned("Lidl Hrvatska kd Zagreb 12")?.categoryId)
        assertNull(merchants.learned("d.o.o."))
    }

    @Test
    fun seedsDefaultsOnceAndNotAfterUserDeletesThem() = runTest {
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                File(context.cacheDir, "seed-test-${System.nanoTime()}.preferences_pb")
            }
        )
        val seeder = DefaultDataSeeder(db, settings, context::getString)

        seeder.seedIfNeeded()
        assertEquals(14, db.categoryDao().count())
        assertEquals(3, db.sourceDao().count())
        assertEquals(4, db.categoryDao().getAll().count { it.kind == CategoryKind.INCOME })

        db.categoryDao().getAll().forEach { db.categoryDao().delete(it) }
        seeder.seedIfNeeded()
        assertEquals(0, db.categoryDao().count())
        assertTrue(settings.didSeedDefaultData())
        assertEquals(false, settings.isAppLockOn.first())
    }

    @Test
    fun collectionsShareTransactionsAndOutliveDeletes() = runTest {
        val collections = CollectionRepository(db)
        val dao = db.collectionDao()
        val hotel = db.transactionDao().insert(Transaction(amount = BigDecimal("120"), kind = TransactionKind.EXPENSE))
        val taxi = db.transactionDao().insert(Transaction(amount = BigDecimal("30"), kind = TransactionKind.EXPENSE))

        val naples = collections.save(TransactionCollection(name = "Trip to Naples"), listOf(hotel, taxi))
        val business = collections.save(TransactionCollection(name = "Business 2026", color = CategoryColor.RED), listOf(hotel))
        collections.addTransaction(naples, hotel) // already there: ignored

        val all = dao.observeAllWithTransactions().first()
        assertEquals(listOf("Trip to Naples", "Business 2026"), all.map { it.collection.name })
        assertEquals(setOf(hotel, taxi), all[0].transactions.map { it.id }.toSet())
        assertEquals(listOf(hotel), all[1].transactions.map { it.id })
        assertEquals("globe", all[0].collection.systemImage)

        // Saving the editor replaces the contents and keeps the id.
        val renamed = all[0].collection.copy(name = "Naples")
        assertEquals(naples, collections.save(renamed, listOf(taxi)))
        assertEquals(listOf(taxi), dao.getTransactionIds(naples))

        // Deleting a transaction removes it from every collection.
        db.transactionDao().delete(db.transactionDao().getById(hotel)!!)
        assertTrue(dao.getTransactionIds(business).isEmpty())

        // Removing from a collection or deleting the collection keeps the transaction.
        collections.removeTransactions(naples, listOf(taxi))
        assertTrue(dao.getTransactionIds(naples).isEmpty())
        collections.addTransaction(naples, taxi)
        collections.delete(dao.getAll().first { it.id == naples })
        assertEquals(taxi, db.transactionDao().getById(taxi)?.id)
        assertNull(dao.observeWithTransactions(naples).first())
    }

    @Test
    fun failedCsvImportSavesNothing() = runTest {
        val csv = CsvRepository(db)
        try {
            csv.import(
                listOf(
                    CsvImporter.Input("expenses.csv", "date;amount;source;category\n01.10.2026;1;Cash;Food\n"),
                    CsvImporter.Input("bad.csv", "foo;bar\n1;2\n"),
                )
            )
            fail("expected an exception")
        } catch (_: CsvImportException) {
        }
        assertEquals(0, db.sourceDao().count())
        assertTrue(db.transactionDao().getRecent(10).isEmpty())

        val result = csv.import(listOf(CsvImporter.Input("expenses.csv", "date;amount;source;category\n01.10.2026;1;Cash;Food\n")))
        assertEquals(1, result.importedCount)
        val export = csv.export()
        assertEquals(listOf("expenses.csv", "accounts.csv"), export.files.map { it.fileName })
        assertEquals(1, export.transactionCount)
    }

    @Test
    fun plannedTransactionsRecordEachOccurrenceOnce() = runTest {
        val alarm = RecordingAlarm()
        val planned = PlannedTransactionRepository(db, alarm) { ZoneOffset.UTC }
        val cash = db.sourceDao().insert(Source(name = "Cash"))
        val food = db.categoryDao().insert(Category(name = "Food"))
        // Whole milliseconds, as the database stores them.
        val start = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofDays(20))
        planned.save(
            null,
            PlannedTransactionDraft(
                BigDecimal("5"), TransactionKind.EXPENSE, sourceId = cash, categoryId = food,
                frequency = RecurrenceFrequency.WEEKLY, startDate = start,
            ),
        )

        // Saving records what is already due: 20 days ago, 13 days ago and 6 days ago.
        val plan = planned.observeAll().first().single()
        assertEquals(3, plan.occurrencesGenerated)
        assertEquals(3, db.transactionDao().getRecent(10).count { it.plannedTransactionId == plan.id })
        assertEquals(start.plus(Duration.ofDays(21)), alarm.scheduledAt)
        assertTrue(planned.recordDue().isEmpty())

        // Nothing is recorded while paused.
        planned.setPaused(plan.id, true)
        assertNull(alarm.scheduledAt)
        assertTrue(planned.recordDue(Instant.now().plus(Duration.ofDays(8))).isEmpty())
        planned.setPaused(plan.id, false)
        assertEquals(3, db.transactionDao().count())

        // Deleting the plan keeps what it recorded.
        planned.delete(plan.id)
        assertNull(alarm.scheduledAt)
        val recorded = db.transactionDao().getRecent(10)
        assertEquals(3, recorded.size)
        assertTrue(recorded.all { it.plannedTransactionId == null && it.categoryId == food })
    }

    @Test
    fun editingAPlanKeepsTheOccurrencesAlreadyRecorded() = runTest {
        val planned = PlannedTransactionRepository(db, RecordingAlarm()) { ZoneOffset.UTC }
        val bank = db.sourceDao().insert(Source(name = "Bank"))
        val salary = db.categoryDao().insert(Category(name = "Salary", kind = CategoryKind.INCOME))
        val draft = PlannedTransactionDraft(
            BigDecimal("2000"), TransactionKind.INCOME, destinationId = bank, categoryId = salary,
            startDate = Instant.now().minus(Duration.ofDays(1)),
        )
        planned.save(null, draft)
        val id = planned.observeAll().first().single().id

        planned.save(id, draft.copy(amount = BigDecimal("2100"), note = "Raise"))
        val plan = planned.get(id)!!
        assertEquals(1, plan.occurrencesGenerated)
        assertEquals("Raise", plan.note)
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun budgetsBelongToTheirCategory() = runTest {
        val food = db.categoryDao().insert(Category(name = "Food"))
        db.budgetDao().insert(Budget(monthlyLimit = BigDecimal("100")))
        db.budgetDao().insert(Budget(monthlyLimit = BigDecimal("30"), categoryId = food))
        try {
            db.budgetDao().insert(Budget(monthlyLimit = BigDecimal("50"), categoryId = food))
            fail("A category has one budget")
        } catch (_: SQLiteConstraintException) {
        }

        db.categoryDao().delete(db.categoryDao().getAll().single())
        assertTrue(db.budgetDao().getAll().single().isGeneral)
    }

    @Test
    fun budgetAlertsOncePerMonthAndAgainAfterDroppingBelow() = runTest {
        val alerts = mutableListOf<String>()
        val budgets = BudgetRepository(db, { _, category -> alerts += category?.name ?: "general" }) { ZoneOffset.UTC }
        val food = db.categoryDao().insert(Category(name = "Food"))
        budgets.ensureGeneral()
        budgets.ensureGeneral()
        val general = db.budgetDao().getAll().single()
        assertEquals(false, general.isActive)
        budgets.save(general.id, BigDecimal("100"), isActive = true, categoryId = null)
        budgets.save(null, BigDecimal("20"), isActive = true, categoryId = food)

        val now = Instant.parse("2026-10-20T12:00:00Z")
        val lunch = db.transactionDao().insert(
            Transaction(amount = BigDecimal("25"), kind = TransactionKind.EXPENSE, categoryId = food, date = now.minus(Duration.ofDays(1)))
        )
        budgets.check(now)
        assertEquals(listOf("Food"), alerts)
        budgets.check(now)
        assertEquals(listOf("Food"), alerts)

        db.transactionDao().delete(db.transactionDao().getById(lunch)!!)
        budgets.check(now)
        assertNull(db.budgetDao().getAll().single { !it.isGeneral }.lastAlertDate)
        db.transactionDao().insert(Transaction(amount = BigDecimal("120"), kind = TransactionKind.EXPENSE, categoryId = food, date = now))
        budgets.check(now)
        assertEquals(listOf("Food", "general", "Food"), alerts)

        val usage = budgets.generalUsage(now)!!
        assertEquals(0, BigDecimal("120").compareTo(usage.spent))
    }

    @Test
    fun recordingPlannedTransactionsChecksBudgets() = runTest {
        var checks = 0
        val planned = PlannedTransactionRepository(db, RecordingAlarm(), onRecorded = { checks++ }) { ZoneOffset.UTC }
        planned.save(null, PlannedTransactionDraft(amount = BigDecimal("9.99"), startDate = Instant.now().minus(Duration.ofMinutes(1))))
        assertEquals(1, checks)
        planned.recordDue()
        assertEquals(1, checks)
    }

    @Test
    fun receiptsAreKeptWithTheirTransactionAndFilesSweptAfterDelete() = runTest {
        val directory = File(context.cacheDir, "receipts-test").apply { deleteRecursively() }
        val receipts = ReceiptRepository(db, directory)
        val id = db.transactionDao().insert(Transaction(amount = BigDecimal("3.07"), kind = TransactionKind.EXPENSE, note = "Konzum"))
        receipts.attach(
            id,
            photos = listOf(byteArrayOf(1, 2, 3)),
            items = listOf(
                ParsedReceiptItem("MLIJEKO  1L", BigDecimal("2"), BigDecimal("1.29"), BigDecimal("2.58")),
                ParsedReceiptItem("KRUH", BigDecimal.ONE, BigDecimal("0.49"), BigDecimal("0.49")),
            ),
        )

        val items = receipts.observeItems(id).first()
        assertEquals(listOf("MLIJEKO  1L", "KRUH"), items.map { it.name })
        assertEquals("mlijeko 1l", items.first().normalizedName)
        val photo = receipts.observePhotos(id).first().single()
        assertTrue(photo.exists())
        assertEquals(listOf("Konzum", "Konzum"), receipts.observeDatedItems().first().map { it.store })
        assertEquals(listOf(ReceiptItemName(id, "MLIJEKO  1L"), ReceiptItemName(id, "KRUH")), db.receiptItemDao().observeNames().first())

        receipts.deleteItem(items.last())
        assertEquals(1, receipts.observeItems(id).first().size)

        // A referenced file stays; once the transaction is gone the sweep deletes it, but only when it's old enough.
        receipts.sweepOrphans(Instant.now().plus(Duration.ofDays(1)))
        assertTrue(photo.exists())
        db.transactionDao().delete(db.transactionDao().getById(id)!!)
        assertTrue(receipts.observeItems(id).first().isEmpty())
        receipts.sweepOrphans()
        assertTrue(photo.exists())
        receipts.sweepOrphans(Instant.now().plus(Duration.ofDays(1)))
        assertFalse(photo.exists())
        directory.deleteRecursively()
    }

    private class RecordingAlarm : PlannedTransactionAlarm {
        var scheduledAt: Instant? = null

        override fun schedule(at: Instant) {
            scheduledAt = at
        }

        override fun cancel() {
            scheduledAt = null
        }
    }
}
