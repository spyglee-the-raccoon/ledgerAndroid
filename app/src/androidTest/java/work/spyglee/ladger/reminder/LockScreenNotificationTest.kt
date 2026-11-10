package work.spyglee.ladger.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.BudgetUsage

/** Notifications that show amounts stay off the lock screen while the app lock is on (decision D5). */
@RunWith(AndroidJUnit4::class)
class LockScreenNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        assumeTrue(
            "Allow notifications for the app to run this test",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        manager.cancelAll()
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    /**
     * The visibility of the notification posted with [tag] and [id]. Posting is asynchronous, and other
     * tests' notifications may arrive meanwhile, so it waits for this one.
     */
    private fun postedVisibility(tag: String, id: Int): Int {
        repeat(100) {
            manager.activeNotifications.firstOrNull { it.tag == tag && it.id == id }?.let { return it.notification.visibility }
            SystemClock.sleep(50)
        }
        throw AssertionError("No notification $tag/$id was posted")
    }

    private val planned = PlannedTransaction(id = PLANNED_ID.toLong(), amount = BigDecimal("12.99"), kind = TransactionKind.EXPENSE, note = "Netflix")
    private val usage = BudgetUsage(Budget(id = BUDGET_ID.toLong(), monthlyLimit = BigDecimal("500")), spent = BigDecimal("510"))

    @Test
    fun plannedTransactionStaysOffTheLockScreenWhileTheAppLockIsOn() = runTest {
        PlannedTransactionNotifier(context) { true }.showRecorded(planned, category = null)
        assertEquals(Notification.VISIBILITY_SECRET, postedVisibility(PLANNED_TAG, PLANNED_ID))
    }

    @Test
    fun plannedTransactionKeepsTheSystemDefaultWithTheAppLockOff() = runTest {
        PlannedTransactionNotifier(context) { false }.showRecorded(planned, category = null)
        assertEquals(Notification.VISIBILITY_PRIVATE, postedVisibility(PLANNED_TAG, PLANNED_ID))
    }

    @Test
    fun budgetAlertStaysOffTheLockScreenWhileTheAppLockIsOn() = runTest {
        BudgetNotifier(context) { true }.showReached(usage, category = null)
        assertEquals(Notification.VISIBILITY_SECRET, postedVisibility(BUDGET_TAG, BUDGET_ID))
    }

    @Test
    fun budgetAlertKeepsTheSystemDefaultWithTheAppLockOff() = runTest {
        BudgetNotifier(context) { false }.showReached(usage, category = null)
        assertEquals(Notification.VISIBILITY_PRIVATE, postedVisibility(BUDGET_TAG, BUDGET_ID))
    }

    private companion object {
        // The tags the notifiers post with. The ids are unlike any the other tests' plans and budgets get,
        // so a late notification from another test can't be mistaken for this one.
        const val PLANNED_TAG = "planned_transaction"
        const val BUDGET_TAG = "budget"
        const val PLANNED_ID = 7_001
        const val BUDGET_ID = 3_001
    }
}
