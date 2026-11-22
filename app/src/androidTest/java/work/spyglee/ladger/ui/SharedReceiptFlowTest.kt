package work.spyglee.ladger.ui

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.math.BigDecimal
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
import work.spyglee.ladger.ui.theme.LadgerTheme

/** A receipt shared from another app keeps its pages, like a scanned one, as on iOS since `1748a53`. */
@RunWith(AndroidJUnit4::class)
class SharedReceiptFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: LadgerDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()
        container = AppContainer(context, database)
        runBlocking {
            database.sourceDao().insert(Source(name = "Cash"))
            database.categoryDao().insert(Category(name = "Food"))
        }
    }

    @After
    fun tearDown() {
        // The pages were written to the app's real receipts folder.
        runBlocking {
            database.transactionDao().getRecent(10).forEach { transaction ->
                container.receiptFiles.observePhotos(transaction.id).first().forEach { it.delete() }
            }
        }
        database.close()
    }

    /** Two empty PDF pages: no text to read, yet both pages are shown and saved, as on iOS. */
    @Test
    fun aSharedPdfWithoutTextKeepsItsPages() {
        val document = PdfDocument()
        repeat(2) { document.finishPage(document.startPage(PdfDocument.PageInfo.Builder(300, 500, it + 1).create())) }
        val file = File(context.cacheDir, "shared-receipt.pdf")
        file.outputStream().use(document::writeTo)
        document.close()

        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                LadgerTheme { LadgerNavHost(sharedReceipt = Uri.fromFile(file)) }
            }
        }
        val secondPage = context.getString(R.string.receipt_page, 2)
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(secondPage)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText(context.getString(R.string.amount)).performTextInput("4")
        compose.onNodeWithText(context.getString(R.string.source)).performClick()
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText(context.getString(R.string.category)).performClick()
        compose.onNodeWithText("Food").performClick()
        val save = context.getString(R.string.save)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription(save) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(save).performClick()

        compose.waitUntil(5_000) { runBlocking { database.transactionDao().getRecent(1).isNotEmpty() } }
        val saved = runBlocking { database.transactionDao().getRecent(1).single() }
        assertEquals(0, BigDecimal("4").compareTo(saved.amount))
        val pages = runBlocking { container.receiptFiles.observePhotos(saved.id).first() }
        assertEquals(2, pages.size)
    }
}
