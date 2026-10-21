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
 * Opens a version 1 database with the current app database, so Room runs the migration and then
 * checks the result against the current schema. Room's MigrationTestHelper isn't used: it needs a
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
    fun version2KeepsTransactionsAndCollections() = runTest {
        createVersion1 { db ->
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
        } finally {
            database.close()
        }
    }

    /** Creates the tables of schema 1 exactly as Room exported them, then lets [fill] add rows. */
    private fun createVersion1(fill: (SQLiteDatabase) -> Unit) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val json = assets.open("${LadgerDatabase::class.java.canonicalName}/1.json").bufferedReader().use { it.readText() }
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
            db.version = 1
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}
