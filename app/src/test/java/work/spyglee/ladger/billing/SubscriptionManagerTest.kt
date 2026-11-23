package work.spyglee.ladger.billing

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionManagerTest {
    private var store = FakeStore()
    private val cache = MemoryProCache()

    /** The manager, with its purchase updates and events collected as they come. */
    private fun TestScope.manager(): Pair<SubscriptionManager, List<ProEvent>> {
        val manager = SubscriptionManager(store, cache, backgroundScope)
        val events = mutableListOf<ProEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.collect(events::add) }
        runCurrent()
        return manager to events
    }

    private suspend fun SubscriptionManager.monthlyPlan() = run {
        loadPlans()
        (plans.value as Plans.Ready).plans.first { it.productId == Pro.MONTHLY }
    }

    @Test
    fun theLastAnswerShowsUntilTheStoreAnswersAndStaysWhenItCant() = runTest {
        cache.save(Pro.YEARLY)
        store.isAvailable = false
        val (manager, _) = manager()
        assertTrue(manager.isPro.value)

        assertFalse(manager.refresh())
        assertTrue(manager.isPro.value)

        // Google Play answers: the subscription has ended.
        store.isAvailable = true
        assertTrue(manager.refresh())
        assertFalse(manager.isPro.value)
        assertNull(cache.load())
    }

    @Test
    fun buyingUnlocksAcknowledgesAndIsRemembered() = runTest {
        val (manager, events) = manager()
        manager.buy(Activity(), manager.monthlyPlan())
        runCurrent()

        assertTrue(manager.isPro.value)
        assertEquals(Pro.MONTHLY, manager.activeProduct.value)
        assertEquals(Pro.MONTHLY, cache.load())
        assertTrue(store.purchases()!!.single().isAcknowledged)
        assertEquals(listOf(ProEvent.Unlocked), events)
    }

    @Test
    fun aSubscriptionTheAccountAlreadyOwnsUnlocksInsteadOfFailing() = runTest {
        // Bought on another phone; this one hasn't asked Google Play yet, so it still looks locked.
        store = FakeStore(initialSubscription = Pro.YEARLY)
        val (manager, events) = manager()
        assertFalse(manager.isPro.value)

        manager.buy(Activity(), manager.monthlyPlan())
        runCurrent()

        assertTrue(manager.isPro.value)
        assertEquals(listOf(ProEvent.Unlocked), events)
    }

    @Test
    fun whenTheStoreCantBeAskedAfterThePurchaseSheetProIsNeverTakenAway() = runTest {
        cache.save(Pro.MONTHLY)
        store.outcome = FakeStore.Outcome.PENDING
        val (manager, _) = manager()
        val plan = manager.monthlyPlan()
        store.queriesFail = true

        manager.buy(Activity(), plan)
        runCurrent()

        // Only the sheet's pending purchase is known: not enough to take the remembered Pro away.
        assertTrue(manager.isPro.value)
        assertEquals(Pro.MONTHLY, cache.load())
    }

    @Test
    fun whenTheStoreCantBeAskedAfterThePurchaseSheetItsPurchaseStillUnlocks() = runTest {
        val (manager, events) = manager()
        val plan = manager.monthlyPlan()
        store.queriesFail = true

        manager.buy(Activity(), plan)
        runCurrent()

        assertTrue(manager.isPro.value)
        assertEquals(listOf(ProEvent.Unlocked), events)
    }

    @Test
    fun aPendingPaymentDoesntUnlockYet() = runTest {
        store.outcome = FakeStore.Outcome.PENDING
        val (manager, events) = manager()
        manager.buy(Activity(), manager.monthlyPlan())
        runCurrent()

        assertFalse(manager.isPro.value)
        assertEquals(listOf(ProEvent.Pending), events)
    }

    @Test
    fun aCancelledPurchaseSaysNothingAndAFailedOneSaysSo() = runTest {
        val (manager, events) = manager()
        store.outcome = FakeStore.Outcome.CANCEL
        manager.buy(Activity(), manager.monthlyPlan())
        runCurrent()
        assertEquals(emptyList<ProEvent>(), events)

        store.outcome = FakeStore.Outcome.FAIL
        manager.buy(Activity(), manager.monthlyPlan())
        runCurrent()
        assertEquals(listOf(ProEvent.Failed), events)
        assertFalse(manager.isPro.value)
    }

    @Test
    fun restoreFindsASubscriptionBoughtElsewhereOrSaysThereIsNone() = runTest {
        val (manager, events) = manager()
        manager.restore()
        store.isAvailable = false
        manager.restore()
        store.isAvailable = true
        // Bought on another phone with the same Google account.
        val elsewhere = FakeStore(initialSubscription = Pro.YEARLY)
        val (other, otherEvents) = run {
            val m = SubscriptionManager(elsewhere, MemoryProCache(), backgroundScope)
            val e = mutableListOf<ProEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { m.events.collect(e::add) }
            runCurrent()
            m to e
        }
        other.restore()

        assertEquals(listOf(ProEvent.NothingToRestore, ProEvent.Unavailable), events)
        assertEquals(listOf(ProEvent.Unlocked), otherEvents)
        assertTrue(other.isPro.value)
    }

    @Test
    fun thePlansAreUnavailableWithoutTheStore() = runTest {
        val (manager, _) = manager()
        store.isAvailable = false
        manager.loadPlans()
        assertEquals(Plans.Unavailable, manager.plans.value)

        store.isAvailable = true
        manager.loadPlans()
        assertEquals(listOf(Pro.MONTHLY, Pro.YEARLY), (manager.plans.value as Plans.Ready).plans.map { it.productId })
    }
}
