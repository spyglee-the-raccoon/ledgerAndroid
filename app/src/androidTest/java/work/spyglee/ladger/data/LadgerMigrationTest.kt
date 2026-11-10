package work.spyglee.ladger.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens an older version's database with the current app database, so Room runs the migrations and
 * then checks the result against the current schema. Room's MigrationTestHelper isn't used: it needs a
 * newer kotlinx-serialization than the app's dependencies allow on the test classpath.
 */
@RunWith(AndroidJUnit4::class)
class LadgerMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun version1KeepsTransactionsAndCollections() = runTest {
        createVersion(1) { db ->
            db.execSQL("INSERT INTO sources (id, name, systemImage, initValue, color, createdAt) VALUES (1, 'Cash', 'banknote', '50', 'green', 0)")
            db.execSQL("INSERT INTO categories (id, name, systemImage, color, kind) VALUES (2, 'Food', 'cart', 'orange', 'expense')")
            db.execSQL(
                "INSERT INTO transactions (id, amount, kind, sourceId, destinationId, note, date, categoryId) " +
                    "VALUES (3, '12.49', 'expense', 1, NULL, 'Konzum', 1760000000000, 2)"
            )
            db.execSQL("INSERT INTO collections (id, name, systemImage, color) VALUES (4, 'Split', 'globe', 'blue')")
            db.execSQL("INSERT INTO collection_transactions (collectionId, transactionId) VALUES (4, 3)")
        }

        val database = Room.databaseBuilder(context, LadgerDatabase::class.java, DB_NAME).build()
        try {
            val transaction = database.transactionDao().getById(3)!!
            assertEquals(0, BigDecimal("12.49").compareTo(transaction.amount))
            assertEquals(1L, transaction.sourceId)
            assertEquals(2L, transaction.categoryId)
            assertEquals("Konzum", transaction.note)
            assertNull(transaction.plannedTransactionId)
            // Recreating the transactions table must not cascade into collection memberships.
            assertEquals(listOf(3L), database.collectionDao().observeWithTransactions(4).first()?.transactions?.map { it.id })
            assertTrue(database.plannedTransactionDao().getAll().isEmpty())
            assertTrue(database.budgetDao().getAll().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun version3KeepsPlannedTransactionsAndAddsBudgetsAndReceipts() = runTest {
        createVersion(2) { db ->
            db.execSQL("INSERT INTO categories (id, name, systemImage, color, kind) VALUES (2, 'Food', 'cart', 'orange', 'expense')")
            db.execSQL(
                "INSERT INTO planned_transactions (id, amount, kind, sourceId, destinationId, note, categoryId, frequency, `interval`, " +
                    "startDate, endDate, occurrencesGenerated, isPaused) VALUES (5, '9.99', 'expense', NULL, NULL, 'Netflix', 2, 'monthly', 1, " +
                    "1760000000000, NULL, 1, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (id, amount, kind, sourceId, destinationId, note, date, categoryId, plannedTransactionId) " +
                    "VALUES (3, '9.99', 'expense', NULL, NULL, 'Netflix', 1760000000000, 2, 5)"
            )
        }

        val database = Room.databaseBuilder(context, LadgerDatabase::class.java, DB_NAME).build()
        try {
            assertEquals(5L, database.transactionDao().getById(3)?.plannedTransactionId)
            assertEquals(1, database.plannedTransactionDao().getById(5)?.occurrencesGenerated)
            assertTrue(database.budgetDao().getAll().isEmpty())
            assertTrue(database.receiptItemDao().observeForTransaction(3).first().isEmpty())

            // The new tables work, including the cascade from categories.
            database.budgetDao().insert(Budget(monthlyLimit = BigDecimal("100"), categoryId = 2))
            database.receiptItemDao().insertAll(
                listOf(ReceiptItem(transactionId = 3, name = "NETFLIX", normalizedName = "netflix", unitPrice = BigDecimal("9.99"), totalPrice = BigDecimal("9.99")))
            )
            database.categoryDao().delete(database.categoryDao().getAll().single())
            assertTrue(database.budgetDao().getAll().isEmpty())
            assertEquals(1, database.receiptItemDao().observeForTransaction(3).first().size)
        } finally {
            database.close()
        }
    }

    /** Creates the tables of schema [version] exactly as Room exported them, then lets [fill] add rows. */
    private fun createVersion(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val json = assets.open("${LadgerDatabase::class.java.canonicalName}/$version.json").bufferedReader().use { it.readText() }
        val schema = JSONObject(json).getJSONObject("database")
        context.deleteDatabase(DB_NAME)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_NAME, table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            fill(db)
            db.version = version
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}
