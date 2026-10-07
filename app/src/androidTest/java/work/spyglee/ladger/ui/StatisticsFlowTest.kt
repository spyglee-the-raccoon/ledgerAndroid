package work.spyglee.ladger.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import work.spyglee.ladger.ui.theme.LadgerTheme

@RunWith(AndroidJUnit4::class)
class StatisticsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()

    private val thisMonth = YearMonth.now()
    private val lastMonth = thisMonth.minusMonths(1)

    @After
    fun tearDown() {
        database.close()
    }

    private fun launch(withTransactions: Boolean) {
        if (withTransactions) {
            runBlocking {
                val cash = database.sourceDao().insert(Source(name = "Cash", initValue = BigDecimal("100")))
                val food = database.categoryDao().insert(Category(name = "Food"))
                database.transactionDao().insert(
                    Transaction(amount = BigDecimal("200"), kind = TransactionKind.INCOME, destinationId = cash, note = "Salary", date = middleOf(lastMonth))
                )
                database.transactionDao().insert(
                    Transaction(amount = BigDecimal("30"), kind = TransactionKind.EXPENSE, sourceId = cash, categoryId = food, note = "Konzum", date = middleOf(thisMonth))
                )
            }
        }
        val container = AppContainer(context, database)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                LadgerTheme { LadgerNavHost() }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
    }

    private fun middleOf(month: YearMonth) = month.atDay(15).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()

    private fun title(month: YearMonth): String {
        val locale = Locale.getDefault()
        return month.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale))
    }

    @Test
    fun selectingAMonthInSavingsListsItsBiggestTransactions() {
        launch(withTransactions = true)
        compose.onNodeWithText(context.getString(R.string.savings)).performClick()

        compose.onNodeWithContentDescription(title(thisMonth), substring = true).performClick()

        compose.onNodeWithText(context.getString(R.string.biggest_expenses, title(thisMonth))).assertExists()
        compose.onNodeWithText("Konzum").assertExists()
        // This month has no incomes.
        compose.onNodeWithText(context.getString(R.string.no_transactions)).assertExists()
    }

    @Test
    fun selectingAMonthInIncomesAndExpensesShowsItsTotals() {
        launch(withTransactions = true)
        compose.onNodeWithText(context.getString(R.string.income_expense)).performClick()

        compose.onNodeWithContentDescription(title(lastMonth), substring = true).performClick()

        compose.onNodeWithText(formatMoney(BigDecimal("200"))).assertExists()
    }

    @Test
    fun exportingWithNoTransactionsSaysThereIsNothingToExport() {
        launch(withTransactions = false)
        compose.onNodeWithText(context.getString(R.string.export_csv)).performClick()

        compose.onNodeWithText(context.getString(R.string.export_nothing)).assertExists()
    }
}
