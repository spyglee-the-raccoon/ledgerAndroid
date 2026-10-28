package work.spyglee.ladger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.receipt.ParsedReceiptItem
import work.spyglee.ladger.ui.theme.LadgerTheme

/** A transaction saved with a scanned receipt: searching by what was bought, and the receipt in the editor. */
@RunWith(AndroidJUnit4::class)
class ReceiptFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: LadgerDatabase
    private lateinit var container: AppContainer
    private var transactionId = 0L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()
        container = AppContainer(context, database)
        runBlocking {
            val cash = database.sourceDao().insert(Source(name = "Cash"))
            val food = database.categoryDao().insert(Category(name = "Food"))
            transactionId = database.transactionDao().insert(
                Transaction(amount = BigDecimal("3.07"), kind = TransactionKind.EXPENSE, sourceId = cash, categoryId = food, note = "Konzum", date = Instant.now())
            )
            container.receiptFiles.attach(
                transactionId,
                photos = listOf(receiptJpeg()),
                items = listOf(
                    ParsedReceiptItem("MLIJEKO 1L", BigDecimal("2"), BigDecimal("1.29"), BigDecimal("2.58")),
                    ParsedReceiptItem("KRUH", BigDecimal.ONE, BigDecimal("0.49"), BigDecimal("0.49")),
                ),
            )
        }
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                LadgerTheme { LadgerNavHost() }
            }
        }
    }

    @After
    fun tearDown() {
        // The page was written to the app's real receipts folder.
        runBlocking { container.receiptFiles.observePhotos(transactionId).first() }.forEach { it.delete() }
        database.close()
    }

    private fun receiptJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 60, it); it.toByteArray() }
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    @Test
    fun searchFindsTransactionsByWhatWasBought() {
        compose.onNodeWithText(context.getString(R.string.expense)).performClick()
        compose.waitUntil(5_000) { count("Konzum") > 0 }

        val search = compose.onNodeWithText(context.getString(R.string.search_transactions))
        search.performTextInput("mlijeko")
        compose.waitUntil(5_000) { count("Konzum") > 0 }
        compose.onNodeWithText("mlijeko").performTextClearance()
        compose.onNodeWithText(context.getString(R.string.search_transactions)).performTextInput("sir")
        compose.waitUntil(5_000) { count("Konzum") == 0 }
    }

    @Test
    fun theEditorShowsTheReceiptAndItsItems() {
        compose.onNodeWithText(context.getString(R.string.expense)).performClick()
        compose.waitUntil(5_000) { count("Konzum") > 0 }
        compose.onNodeWithText("Konzum").performClick()

        compose.waitUntil(5_000) { count("MLIJEKO 1L") > 0 }
        compose.onNodeWithText(context.getString(R.string.receipt)).assertExists()
        compose.onNodeWithText(context.getString(R.string.receipt_items)).assertExists()
        compose.onNodeWithText("2 × ${formatMoney(BigDecimal("1.29"))}").assertExists()
        compose.onNodeWithText(formatMoney(BigDecimal("2.58"))).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.save)).assertIsEnabled()

        // The page opens full screen and closes again.
        val page = context.getString(R.string.receipt_page, 1)
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription(page)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(page).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()

        // Swiping an item away deletes it.
        compose.onNodeWithText("KRUH").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { runBlocking { container.receiptFiles.observeItems(transactionId).first() }.size == 1 }
        assertEquals("MLIJEKO 1L", runBlocking { container.receiptFiles.observeItems(transactionId).first() }.single().name)
    }
}
