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
     * Fails unless the notification posted with [tag] and [id] gets [expected] visibility. Posting is
     * asynchronous, and other tests' notifications may arrive meanwhile, so it waits for this one.
     */
    private fun assertVisibility(expected: Int, tag: String, id: Int) {
        var actual: Int? = null
        repeat(100) {
            actual = manager.activeNotifications.firstOrNull { it.tag == tag && it.id == id }?.notification?.visibility
            if (actual == expected) return
            SystemClock.sleep(50)
        }
        assertEquals("visibility of $tag/$id", expected, actual)
    }

    private val planned = PlannedTransaction(id = PLANNED_ID.toLong(), amount = BigDecimal("12.99"), kind = TransactionKind.EXPENSE, note = "Netflix")
    private val usage = BudgetUsage(Budget(id = BUDGET_ID.toLong(), monthlyLimit = BigDecimal("500")), spent = BigDecimal("510"))

    @Test
    fun plannedTransactionStaysOffTheLockScreenWhileTheAppLockIsOn() = runTest {
        PlannedTransactionNotifier(context) { true }.showRecorded(planned, category = null)
        assertVisibility(Notification.VISIBILITY_SECRET, PLANNED_TAG, PLANNED_ID)
    }

    @Test
    fun plannedTransactionKeepsTheSystemDefaultWithTheAppLockOff() = runTest {
        PlannedTransactionNotifier(context) { false }.showRecorded(planned, category = null)
        assertVisibility(Notification.VISIBILITY_PRIVATE, PLANNED_TAG, PLANNED_ID)
    }

    @Test
    fun budgetAlertStaysOffTheLockScreenWhileTheAppLockIsOn() = runTest {
        BudgetNotifier(context) { true }.showReached(usage, category = null)
        assertVisibility(Notification.VISIBILITY_SECRET, BUDGET_TAG, BUDGET_ID)
    }

    @Test
    fun budgetAlertKeepsTheSystemDefaultWithTheAppLockOff() = runTest {
        BudgetNotifier(context) { false }.showReached(usage, category = null)
        assertVisibility(Notification.VISIBILITY_PRIVATE, BUDGET_TAG, BUDGET_ID)
    }

    @Test
    fun turningTheAppLockOnMovesShownNotificationsOffTheLockScreen() = runTest {
        PlannedTransactionNotifier(context) { false }.showRecorded(planned, category = null)
        BudgetNotifier(context) { false }.showReached(usage, category = null)
        assertVisibility(Notification.VISIBILITY_PRIVATE, PLANNED_TAG, PLANNED_ID)
        assertVisibility(Notification.VISIBILITY_PRIVATE, BUDGET_TAG, BUDGET_ID)

        keepShownAmountsOffLockScreen(context)

        assertVisibility(Notification.VISIBILITY_SECRET, PLANNED_TAG, PLANNED_ID)
        assertVisibility(Notification.VISIBILITY_SECRET, BUDGET_TAG, BUDGET_ID)
    }

    private companion object {
        // The ids are unlike any the other tests' plans and budgets get,
        // so a late notification from another test can't be mistaken for this one.
        const val PLANNED_TAG = PlannedTransactionNotifier.NOTIFICATION_TAG
        const val BUDGET_TAG = BudgetNotifier.NOTIFICATION_TAG
        const val PLANNED_ID = 7_001
        const val BUDGET_ID = 3_001
    }
}
