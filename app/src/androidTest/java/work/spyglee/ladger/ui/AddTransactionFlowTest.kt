package work.spyglee.ladger.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
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
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.ui.theme.LadgerTheme

@RunWith(AndroidJUnit4::class)
class AddTransactionFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: LadgerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()
        runBlocking {
            database.sourceDao().insert(Source(name = "Cash", initValue = BigDecimal("100")))
            database.categoryDao().insert(Category(name = "Food"))
        }
        val container = AppContainer(context, database)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                LadgerTheme { LadgerNavHost() }
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addingAnExpenseUpdatesTheBalances() {
        compose.onNodeWithText(context.getString(R.string.add_transaction)).performClick()

        val save = compose.onNodeWithContentDescription(context.getString(R.string.save))
        save.assertIsNotEnabled()

        compose.onNodeWithText(context.getString(R.string.amount)).performTextInput("12,49")
        compose.onNodeWithText(context.getString(R.string.source)).performClick()
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText(context.getString(R.string.category)).performClick()
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText(context.getString(R.string.note)).performTextInput("Konzum")
        save.assertIsEnabled()
        save.performClick()

        // Back on the main screen: 100 starting value minus the expense.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextCount(formatMoney(BigDecimal("87.51"))) > 0
        }
        val saved = runBlocking { database.transactionDao().getRecent(1).single() }
        assertEquals(0, BigDecimal("12.49").compareTo(saved.amount))
        assertEquals(TransactionKind.EXPENSE, saved.kind)
        assertEquals("Konzum", saved.note)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
