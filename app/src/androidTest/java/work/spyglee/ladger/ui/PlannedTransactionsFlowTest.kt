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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.ui.theme.LadgerTheme

@RunWith(AndroidJUnit4::class)
class PlannedTransactionsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: LadgerDatabase

    @Before
    fun setUp() {
        // Saving asks for the notification permission, which a test can't answer. Grant it first
        // (adb shell pm grant work.spyglee.ladger android.permission.POST_NOTIFICATIONS); otherwise the test is skipped.
        assumeTrue(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
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
        if (::database.isInitialized) database.close()
    }

    @Test
    fun addingAPlanRecordsItsFirstOccurrence() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.planned_transactions)).performClick()
        compose.onNodeWithText(context.getString(R.string.no_planned_transactions)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.add_planned_transaction)).performClick()

        val save = compose.onNodeWithContentDescription(context.getString(R.string.save))
        save.assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.amount)).performTextInput("9,99")
        compose.onNodeWithText(context.getString(R.string.source)).performClick()
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText(context.getString(R.string.category)).performClick()
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText(context.getString(R.string.note)).performTextInput("Netflix")
        repeat(2) { compose.onNodeWithContentDescription(context.getString(R.string.increase)).performClick() }
        val everyThreeMonths = context.resources.getQuantityString(R.plurals.every_n_months, 3, 3)
        compose.onNodeWithText(everyThreeMonths).assertExists()
        save.assertIsEnabled()
        save.performClick()

        // Back on the list; a plan starting today is recorded straight away, as on iOS.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Netflix")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(everyThreeMonths, substring = true).assertExists()
        val plan = runBlocking { database.plannedTransactionDao().getAll().single() }
        assertEquals(RecurrenceFrequency.MONTHLY, plan.frequency)
        assertEquals(3, plan.interval)
        assertEquals(1, plan.occurrencesGenerated)
        val recorded = runBlocking { database.transactionDao().getRecent(10).single() }
        assertEquals(plan.id, recorded.plannedTransactionId)
        assertEquals(0, BigDecimal("9.99").compareTo(recorded.amount))
        assertEquals("Netflix", recorded.note)
    }
}
