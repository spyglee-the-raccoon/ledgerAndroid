package work.spyglee.ladger.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit
import java.text.NumberFormat
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Debug builds only: a stand-in for Google Play with the iOS products (€2.99 a month, €29.99 a year,
 * each with a 1-week free trial), like iOS's `Ledger.storekit`. It lets the paywall and the locked
 * statistics be tried before the app exists in Play Console. Buying succeeds at once; "Manage
 * subscription" cancels. Build with `-Pledger.playBilling=true` to use Google Play instead.
 */
class FakeStore(
    private val preferences: SharedPreferences? = null,
    initialSubscription: String? = null,
    /** False makes the store unreachable, like a phone without Google Play. */
    var isAvailable: Boolean = true,
    /** What the purchase sheet does when a plan is bought. */
    var outcome: Outcome = Outcome.BUY,
    /** True makes the queries fail (a timeout, say) while the purchase sheet still works. */
    var queriesFail: Boolean = false,
) : Store {
    enum class Outcome { BUY, PENDING, CANCEL, FAIL }

    private var subscribedTo: String? = preferences?.getString(SUBSCRIBED_TO, null) ?: initialSubscription
        set(value) {
            field = value
            preferences?.edit { putString(SUBSCRIBED_TO, value) }
        }
    private var acknowledged = true

    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 4)
    override val updates: Flow<PurchaseUpdate> = _updates.asSharedFlow()

    override suspend fun purchases(): List<StorePurchase>? {
        if (!isAvailable || queriesFail) return null
        return listOfNotNull(subscribedTo?.let { purchase(it, StorePurchase.State.PURCHASED) })
    }

    override suspend fun products(productIds: List<String>): List<StoreProduct>? {
        if (!isAvailable || queriesFail) return null
        val euro = NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance("EUR") }
        return productIds.mapNotNull { id ->
            val (price, period) = when (id) {
                Pro.MONTHLY -> 2.99 to "P1M"
                Pro.YEARLY -> 29.99 to "P1Y"
                else -> return@mapNotNull null
            }
            val base = StorePhase((price * 1_000_000).toLong(), euro.format(price), period, isRecurring = true)
            val trial = StorePhase(0, euro.format(0), "P1W", isRecurring = false)
            StoreProduct(
                productId = id,
                offers = listOf(
                    StoreOffer(offerId = null, token = "$id:base", phases = listOf(base)),
                    StoreOffer(offerId = "trial", token = "$id:trial", phases = listOf(trial, base)),
                ),
            )
        }
    }

    override suspend fun acknowledge(purchase: StorePurchase): Boolean {
        acknowledged = true
        return true
    }

    override suspend fun launchPurchase(activity: Activity, plan: ProPlan): Boolean {
        // Like Google Play, which refuses at once (ITEM_ALREADY_OWNED) when the account owns Pro already.
        if (!isAvailable || subscribedTo != null) return false
        val update = when (outcome) {
            Outcome.BUY -> {
                subscribedTo = plan.productId
                acknowledged = false
                PurchaseUpdate.Purchased(listOf(purchase(plan.productId, StorePurchase.State.PURCHASED)))
            }
            Outcome.PENDING -> PurchaseUpdate.Purchased(listOf(purchase(plan.productId, StorePurchase.State.PENDING)))
            Outcome.CANCEL -> PurchaseUpdate.Cancelled
            Outcome.FAIL -> PurchaseUpdate.Failed("fake failure")
        }
        _updates.emit(update)
        return true
    }

    override fun openManagement(activity: Activity, productId: String) {
        subscribedTo = null
        Toast.makeText(activity, "Fake store: subscription cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun purchase(productId: String, state: StorePurchase.State) = StorePurchase(
        productIds = listOf(productId),
        state = state,
        isAcknowledged = acknowledged,
        isSuspended = false,
        token = "fake:$productId",
        isVerified = true,
    )

    companion object {
        private const val SUBSCRIBED_TO = "subscribedTo"

        /** The debug build's store, kept across launches in its own preferences file. */
        fun persistent(context: Context) = FakeStore(context.getSharedPreferences("fake_store", Context.MODE_PRIVATE))
    }
}
