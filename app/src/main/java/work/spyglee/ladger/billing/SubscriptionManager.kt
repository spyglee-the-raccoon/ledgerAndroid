package work.spyglee.ladger.billing

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ledger Pro, the Android side of iOS's `SubscriptionManager`: whether the user may open the
 * statistics, the plans on sale, and buying one. The store is asked when the app starts and whenever
 * it comes back; until it answers, and whenever it can't be reached, the last answer stands (D1).
 */
class SubscriptionManager(
    private val store: Store,
    private val cache: ProCache,
    private val scope: CoroutineScope,
) {
    private val _activeProduct = MutableStateFlow(cache.load())

    /** The product of the subscription that makes the user Pro; for "Manage subscription". */
    val activeProduct: StateFlow<String?> = _activeProduct.asStateFlow()

    private val _isPro = MutableStateFlow(_activeProduct.value != null)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _plans = MutableStateFlow<Plans>(Plans.Loading)
    val plans: StateFlow<Plans> = _plans.asStateFlow()

    private val _events = MutableSharedFlow<ProEvent>(extraBufferCapacity = 4)

    /** What the paywall tells the user after a purchase or restore. */
    val events: SharedFlow<ProEvent> = _events.asSharedFlow()

    private val applying = Mutex()

    init {
        scope.launch { store.updates.collect(::onUpdate) }
    }

    /**
     * Asks the store again; false when it couldn't be reached and the last answer was kept. Asking and
     * applying the answer happen under one lock, so an older answer is never applied after a newer one
     * (a refresh started just before a purchase would otherwise lock the statistics again).
     */
    suspend fun refresh(): Boolean = applying.withLock {
        val purchases = store.purchases() ?: return@withLock false
        apply(purchases)
        true
    }

    fun refreshInBackground() {
        scope.launch { refresh() }
    }

    suspend fun loadPlans() {
        _plans.value = Plans.Loading
        val plans = store.products(Pro.PRODUCT_IDS)?.let(Pro::plans).orEmpty()
        _plans.value = if (plans.isEmpty()) Plans.Unavailable else Plans.Ready(plans)
    }

    suspend fun buy(activity: Activity, plan: ProPlan) {
        if (store.launchPurchase(activity, plan)) return
        // The sheet didn't open. Google Play also refuses here when this account owns Pro already (bought on
        // another phone, say), so ask again before calling it a failure.
        _events.emit(if (refresh() && _isPro.value) ProEvent.Unlocked else ProEvent.Failed)
    }

    /** Restore purchases, as on iOS. Google Play keeps purchases with the Google account, so this asks it again. */
    suspend fun restore() {
        _events.emit(
            when {
                !refresh() -> ProEvent.Unavailable
                _isPro.value -> ProEvent.Unlocked
                else -> ProEvent.NothingToRestore
            }
        )
    }

    fun openManagement(activity: Activity) {
        store.openManagement(activity, _activeProduct.value ?: Pro.MONTHLY)
    }

    private suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchased -> {
                applying.withLock {
                    // Asks again for the whole picture; the sheet only reports what it just sold. If the store
                    // can't answer, what the sheet reported may unlock Pro, but never takes it away (D1).
                    val purchases = store.purchases()
                    when {
                        purchases != null -> apply(purchases)
                        Pro.activeProduct(update.purchases) != null -> apply(update.purchases)
                    }
                }
                _events.emit(
                    when {
                        _isPro.value -> ProEvent.Unlocked
                        update.purchases.any { it.state == StorePurchase.State.PENDING } -> ProEvent.Pending
                        else -> ProEvent.Failed
                    }
                )
            }
            PurchaseUpdate.Cancelled -> Unit
            is PurchaseUpdate.Failed -> _events.emit(ProEvent.Failed)
        }
    }

    /** Takes [purchases] as the whole picture. Called with [applying] held. */
    private suspend fun apply(purchases: List<StorePurchase>) {
        Pro.toAcknowledge(purchases).forEach { store.acknowledge(it) }
        val active = Pro.activeProduct(purchases)
        cache.save(active)
        _activeProduct.value = active
        _isPro.value = active != null
    }
}

sealed interface Plans {
    data object Loading : Plans

    /** The store can't be reached, or has nothing on sale (before the subscriptions exist in Play Console). */
    data object Unavailable : Plans

    data class Ready(val plans: List<ProPlan>) : Plans
}

enum class ProEvent { Unlocked, Pending, Failed, NothingToRestore, Unavailable }
