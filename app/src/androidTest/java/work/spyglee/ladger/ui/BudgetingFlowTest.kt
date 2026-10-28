package work.spyglee.ladger.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.ui.theme.LadgerTheme

@RunWith(AndroidJUnit4::class)
class BudgetingFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: LadgerDatabase
    private var food = 0L

    @Before
    fun setUp() {
        // Saving a budget asks for the notification permission, as for planned transactions; see PlannedTransactionsFlowTest.
        assumeTrue(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
        database = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()
        runBlocking {
            food = database.categoryDao().insert(Category(name = "Food"))
            val fuel = database.categoryDao().insert(Category(name = "Fuel"))
            database.transactionDao().insert(Transaction(amount = BigDecimal("25"), kind = TransactionKind.EXPENSE, categoryId = food, date = Instant.now()))
            database.transactionDao().insert(Transaction(amount = BigDecimal("40"), kind = TransactionKind.EXPENSE, categoryId = fuel, date = Instant.now()))
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
        if (::database.isInitialized) database.close()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun settingLimitsShowsSpendingAgainstThem() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.budgeting)).performClick()

        // The general limit is always there, switched off until it is set up.
        val general = context.getString(R.string.general_budget)
        waitForText(general)
        compose.onNodeWithText(context.getString(R.string.no_category_budgets)).assertExists()
        compose.onNodeWithText(general).performClick()
        compose.onNodeWithText(context.getString(R.string.category)).assertDoesNotExist()
        val save = compose.onNodeWithContentDescription(context.getString(R.string.save))
        save.assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.monthly_limit)).performTextInput("100")
        compose.onNodeWithText(context.getString(R.string.active)).performClick()
        save.assertIsEnabled()
        save.performClick()

        // 65 of 100 spent across both categories.
        waitForText(context.getString(R.string.budget_left, formatMoney(BigDecimal("35"))))
        compose.onNodeWithText(NumberFormat.getPercentInstance().format(0.65)).assertExists()

        compose.onNodeWithContentDescription(context.getString(R.string.add_budget)).performClick()
        compose.onNodeWithText(context.getString(R.string.add_budget)).assertExists()
        compose.onNodeWithText(context.getString(R.string.category)).performClick()
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText(context.getString(R.string.monthly_limit)).performTextInput("20")
        save.performClick()

        waitForText(context.getString(R.string.budget_over, formatMoney(BigDecimal("5"))))
        val budgets = runBlocking { database.budgetDao().getAll() }
        assertEquals(2, budgets.size)
        val foodBudget = budgets.single { it.categoryId == food }
        assertEquals(0, BigDecimal("20").compareTo(foodBudget.monthlyLimit))
        // Saving checked the budgets: Food is over its limit and was notified about.
        compose.waitUntil(5_000) { runBlocking { database.budgetDao().getById(foodBudget.id)?.lastAlertDate } != null }
        assertNull(runBlocking { database.budgetDao().getAll().single { it.isGeneral }.lastAlertDate })

        compose.onNodeWithContentDescription("Food, ${context.getString(R.string.active)}").performClick()
        compose.waitUntil(5_000) { runBlocking { database.budgetDao().getById(foodBudget.id)?.isActive } == false }
    }
}
