package work.spyglee.ladger.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/**
 * Where Ledger Pro is bought: Google Play ([PlayStore]), or in debug builds and tests a stand-in
 * that needs no Play Console (`FakeStore`, like iOS's `Ledger.storekit`). The models are plain
 * Kotlin, so the rules in [Pro] are tested without Play.
 */
interface Store {
    /** The Google account's subscriptions as the store sees them now; null when the store can't be reached. */
    suspend fun purchases(): List<StorePurchase>?

    /** The subscriptions on sale, with the offers this account can use; null when the store can't be reached. */
    suspend fun products(productIds: List<String>): List<StoreProduct>?

    /** Confirms a purchase; Google Play refunds one that isn't acknowledged within three days. */
    suspend fun acknowledge(purchase: StorePurchase): Boolean

    /** Opens the store's purchase sheet for [plan]; the outcome arrives through [updates]. False when it couldn't open. */
    suspend fun launchPurchase(activity: Activity, plan: ProPlan): Boolean

    /** Opens the store's own page for managing or cancelling the subscription to [productId]. */
    fun openManagement(activity: Activity, productId: String)

    /** Purchases finished, cancelled or failed in the purchase sheet. */
    val updates: Flow<PurchaseUpdate>
}

data class StorePurchase(
    val productIds: List<String>,
    val state: State,
    val isAcknowledged: Boolean,
    /** On hold or paused in Google Play: the subscription exists but isn't paid for. */
    val isSuspended: Boolean,
    val token: String,
    /** Signed by Google Play with the app's key (see [PurchaseSignature]). */
    val isVerified: Boolean,
) {
    enum class State { PURCHASED, PENDING, UNKNOWN }
}

data class StoreProduct(val productId: String, val offers: List<StoreOffer>)

/** A base plan (no [offerId]) or an offer on it, such as the free trial. */
data class StoreOffer(val offerId: String?, val token: String, val phases: List<StorePhase>)

data class StorePhase(
    val priceMicros: Long,
    val formattedPrice: String,
    /** ISO 8601, such as P1W, P1M or P1Y. */
    val billingPeriod: String,
    /** The phase that repeats until the subscription is cancelled. */
    val isRecurring: Boolean,
)

sealed interface PurchaseUpdate {
    /** The purchase sheet finished; [purchases] are the ones it reported. */
    data class Purchased(val purchases: List<StorePurchase>) : PurchaseUpdate

    data object Cancelled : PurchaseUpdate

    data class Failed(val reason: String) : PurchaseUpdate
}
