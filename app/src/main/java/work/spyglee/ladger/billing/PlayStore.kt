package work.spyglee.ladger.billing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google Play Billing. Everything goes through the Play Store app on the phone, which does the
 * network part; the library itself asks for no network permission (only its usage logging would,
 * and the manifest removes that, as for ML Kit).
 */
class PlayStore(context: Context, private val licenseKey: String) : Store {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseUpdate> = _updates.asSharedFlow()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> _updates.tryEmit(update(result, purchases)) }
        // Required by the library; Ledger sells no one-time products.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = Mutex()

    /** The products last read, which a purchase needs. */
    private val details = ConcurrentHashMap<String, ProductDetails>()

    override suspend fun purchases(): List<StorePurchase>? {
        if (!connect()) return null
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build())
        if (result.billingResult.responseCode != BillingResponseCode.OK) return null.also { log("purchases", result.billingResult) }
        return result.purchasesList.map(::toStorePurchase)
    }

    override suspend fun products(productIds: List<String>): List<StoreProduct>? {
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productIds.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.SUBS).build() })
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) return null.also { log("products", result.billingResult) }
        return result.productDetailsList.orEmpty().map { product ->
            details[product.productId] = product
            StoreProduct(
                productId = product.productId,
                offers = product.subscriptionOfferDetails.orEmpty().map { offer ->
                    StoreOffer(
                        offerId = offer.offerId,
                        token = offer.offerToken,
                        phases = offer.pricingPhases.pricingPhaseList.map {
                            StorePhase(
                                priceMicros = it.priceAmountMicros,
                                formattedPrice = it.formattedPrice,
                                billingPeriod = it.billingPeriod,
                                isRecurring = it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                            )
                        },
                    )
                },
            )
        }
    }

    override suspend fun acknowledge(purchase: StorePurchase): Boolean {
        if (!connect()) return false
        val result = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build())
        return (result.responseCode == BillingResponseCode.OK).also { if (!it) log("acknowledge", result) }
    }

    override suspend fun launchPurchase(activity: Activity, plan: ProPlan): Boolean {
        val product = details[plan.productId] ?: run {
            products(listOf(plan.productId))
            details[plan.productId]
        } ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(plan.offerToken).build())
            )
            .build()
        val result = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, params) }
        return (result.responseCode == BillingResponseCode.OK).also { if (!it) log("launch", result) }
    }

    /** Google Play's subscription page for this app's product, where it can be cancelled or changed. */
    override fun openManagement(activity: Activity, productId: String) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=$productId&package=${activity.packageName}")
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can open $uri", e)
        }
    }

    /** Connects to the Play Store app once; later calls reconnect on their own (automatic service reconnection). */
    private suspend fun connect(): Boolean = connecting.withLock {
        if (client.isReady) return true
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode != BillingResponseCode.OK) log("connect", result)
                        if (continuation.isActive) continuation.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(false)
                    }
                })
            }
        } ?: false
    }

    private fun update(result: BillingResult, purchases: List<Purchase>?): PurchaseUpdate = when (result.responseCode) {
        BillingResponseCode.OK -> PurchaseUpdate.Purchased(purchases.orEmpty().map(::toStorePurchase))
        // Bought before, for example on another phone with the same Google account: ask Play again.
        BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.Purchased(emptyList())
        BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
        else -> PurchaseUpdate.Failed("${result.responseCode}: ${result.debugMessage}").also { log("purchase", result) }
    }

    private fun toStorePurchase(purchase: Purchase) = StorePurchase(
        productIds = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> StorePurchase.State.PURCHASED
            Purchase.PurchaseState.PENDING -> StorePurchase.State.PENDING
            else -> StorePurchase.State.UNKNOWN
        },
        isAcknowledged = purchase.isAcknowledged,
        isSuspended = purchase.isSuspended,
        token = purchase.purchaseToken,
        isVerified = PurchaseSignature.isValid(licenseKey, purchase.originalJson, purchase.signature),
    )

    private fun log(call: String, result: BillingResult) = Log.w(TAG, "$call: ${result.responseCode} ${result.debugMessage}")

    private companion object {
        const val TAG = "PlayStore"
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
