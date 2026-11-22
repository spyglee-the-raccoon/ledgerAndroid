package work.spyglee.ladger.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.billing.FakeStore
import work.spyglee.ladger.billing.MemoryProCache
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.ui.theme.LadgerTheme

/** Ledger Pro (D1) with the debug build's stand-in store: locked statistics, the paywall, buying. */
@RunWith(AndroidJUnit4::class)
class ProFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: LadgerDatabase
    private lateinit var store: FakeStore
    private lateinit var container: AppContainer
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LadgerDatabase::class.java).build()
        store = FakeStore()
        container = AppContainer(context, database, store = store, proCache = MemoryProCache())
        compose.setContent {
            navController = rememberNavController()
            CompositionLocalProvider(LocalAppContainer provides container) {
                LadgerTheme { LadgerNavHost(navController) }
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** The statistics rows' lock icons, read out as the paywall's title. */
    private fun lockCount() = compose.onAllNodes(hasContentDescription(context.getString(R.string.paywall_title))).fetchSemanticsNodes().size

    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun openPaywallFromSettings() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.income_expense)).performScrollTo().performClick()
        waitFor(context.getString(R.string.paywall_title))
    }

    @Test
    fun buyingFromTheLockedStatisticsUnlocksThem() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        assertEquals(4, lockCount())

        compose.onNodeWithText(context.getString(R.string.income_expense)).performScrollTo().performClick()
        waitFor(context.getString(R.string.monthly))
        compose.onNodeWithText(context.getString(R.string.paywall_subtitle)).assertExists()
        compose.onNodeWithText(context.getString(R.string.yearly)).assertExists()
        // Both plans come with the iOS trial of one week.
        val trial = context.resources.getQuantityString(R.plurals.pro_free_trial_weeks, 1, 1)
        assertEquals(2, compose.onAllNodes(hasText(trial)).fetchSemanticsNodes().size)

        compose.onNodeWithText(context.getString(R.string.yearly)).performClick()
        compose.onNodeWithText(context.getString(R.string.pro_subscribe)).performScrollTo().performClick()

        // As on iOS, the paywall closes; Settings now has no locks and offers "Manage subscription".
        waitFor(context.getString(R.string.pro_manage))
        assertEquals(0, lockCount())
        assertTrue(container.subscriptions.isPro.value)
        assertEquals("work.spyglee.ledger.pro.yearly", container.subscriptions.activeProduct.value)
        // Google Play refunds purchases that aren't acknowledged.
        assertTrue(runBlocking { store.purchases()!!.single().isAcknowledged })

        compose.onNodeWithText(context.getString(R.string.income_expense)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.paywall_title)).assertDoesNotExist()
    }

    @Test
    fun aStatisticsScreenOpenedWithoutProShowsThePaywallInItsPlace() {
        compose.runOnIdle { navController.navigate("stats/top_categories") }
        waitFor(context.getString(R.string.pro_subscribe))

        compose.onNodeWithText(context.getString(R.string.pro_subscribe)).performScrollTo().performClick()

        // The statistics screen itself appears, without going back.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(context.getString(R.string.pro_subscribe))).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.top_categories)).assertExists()
        assertEquals("stats/top_categories", navController.currentDestination?.route)
    }

    @Test
    fun aFailedPurchaseOrNothingToRestoreKeepsTheStatisticsLocked() {
        store.outcome = FakeStore.Outcome.FAIL
        openPaywallFromSettings()
        waitFor(context.getString(R.string.pro_subscribe))

        compose.onNodeWithText(context.getString(R.string.pro_subscribe)).performScrollTo().performClick()
        waitFor(context.getString(R.string.pro_failed))

        compose.onNodeWithText(context.getString(R.string.pro_restore)).performScrollTo().performClick()
        waitFor(context.getString(R.string.pro_restore_none))
        assertFalse(container.subscriptions.isPro.value)
    }

    @Test
    fun withoutGooglePlayThePaywallSaysSo() {
        store.isAvailable = false
        openPaywallFromSettings()

        waitFor(context.getString(R.string.pro_unavailable))
        compose.onNodeWithText(context.getString(R.string.pro_subscribe)).assertDoesNotExist()

        // Google Play is back (installed, or signed in): trying again shows the plans.
        store.isAvailable = true
        compose.onNodeWithText(context.getString(R.string.pro_try_again)).performClick()
        waitFor(context.getString(R.string.pro_subscribe))
    }
}
