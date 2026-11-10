package work.spyglee.ladger.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.receipt.ParsedReceiptItem
import work.spyglee.ladger.ui.theme.LadgerTheme

@RunWith(AndroidJUnit4::class)
class PriceComparisonFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: LadgerDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()
        container = AppContainer(context, database)
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

    /** Milk bought [count] times, a week apart, its price rising by 2 cents each time from 1.00. */
    private fun buyMilk(count: Int) = runBlocking {
        val start = Instant.now().minus(Duration.ofDays(7L * count))
        repeat(count) { i ->
            val price = BigDecimal("1.00") + BigDecimal("0.02") * BigDecimal(i)
            val id = database.transactionDao().insert(
                Transaction(amount = price, kind = TransactionKind.EXPENSE, note = "Konzum", date = start.plus(Duration.ofDays(7L * i)))
            )
            container.receiptFiles.attach(id, emptyList(), listOf(ParsedReceiptItem("MLIJEKO 1L", BigDecimal.ONE, price, price)))
        }
    }

    private fun openPriceComparison() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.price_comparison)).performScrollTo().performClick()
    }

    @Test
    fun aProductBoughtTenTimesIsNotComparedYet() {
        buyMilk(10)
        openPriceComparison()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(context.getString(R.string.no_price_history))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun showsPersonalInflationAndThePriceHistory() {
        buyMilk(11)
        openPriceComparison()

        // First three purchases average 1.02, the last three 1.18.
        val change = "+" + NumberFormat.getPercentInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(0.16 / 1.02)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("MLIJEKO 1L")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.personal_inflation)).assertExists()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(change)).fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("${formatMoney(BigDecimal("1.02"))} → ${formatMoney(BigDecimal("1.18"))}").assertExists()

        compose.onNodeWithText("MLIJEKO 1L").performClick()
        compose.onNodeWithText(context.getString(R.string.price_history_description)).assertExists()
        compose.onNodeWithText(context.getString(R.string.purchases)).assertExists()
        compose.onNodeWithText(formatMoney(BigDecimal("1.20"))).assertExists()
    }
}
