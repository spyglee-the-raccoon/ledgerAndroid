package work.spyglee.ladger.billing

import java.time.Period
import java.time.format.DateTimeParseException

/** Ledger Pro unlocks the four statistics screens, as on iOS (decision D1). */
object Pro {
    /** The iOS product IDs, so both stores sell the same thing. */
    const val MONTHLY = "work.spyglee.ledger.pro.monthly"
    const val YEARLY = "work.spyglee.ledger.pro.yearly"
    val PRODUCT_IDS = listOf(MONTHLY, YEARLY)

    /**
     * The product that makes the user Pro, or null. Like iOS's verified current entitlements: a paid,
     * signed purchase of either product. A pending payment or a subscription on hold doesn't count.
     */
    fun activeProduct(purchases: List<StorePurchase>): String? = purchases
        .filter { it.isVerified && it.state == StorePurchase.State.PURCHASED && !it.isSuspended }
        .flatMap { it.productIds }
        .firstOrNull { it in PRODUCT_IDS }

    /** Paid purchases that Google Play still waits to have acknowledged. */
    fun toAcknowledge(purchases: List<StorePurchase>): List<StorePurchase> = purchases.filter {
        it.isVerified && it.state == StorePurchase.State.PURCHASED && !it.isAcknowledged && it.productIds.any(PRODUCT_IDS::contains)
    }

    /**
     * One plan per product, monthly first, as iOS lists them. Google Play only returns the offers this
     * account may use, so a free trial offer, when present, is the one to show; otherwise the base plan.
     */
    fun plans(products: List<StoreProduct>): List<ProPlan> = PRODUCT_IDS.mapNotNull { id ->
        val product = products.firstOrNull { it.productId == id } ?: return@mapNotNull null
        val offer = product.offers.firstOrNull { offer -> offer.phases.any { it.priceMicros == 0L } }
            ?: product.offers.firstOrNull { it.offerId == null }
            ?: product.offers.firstOrNull()
            ?: return@mapNotNull null
        val recurring = offer.phases.lastOrNull { it.isRecurring } ?: offer.phases.lastOrNull() ?: return@mapNotNull null
        ProPlan(
            productId = id,
            period = if (id == YEARLY) ProPlan.Period.YEAR else ProPlan.Period.MONTH,
            price = recurring.formattedPrice,
            freeTrial = offer.phases.firstOrNull { it.priceMicros == 0L }?.let { parsePeriod(it.billingPeriod) },
            offerToken = offer.token,
        )
    }

    private fun parsePeriod(iso: String): Period? = try {
        Period.parse(iso)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** A subscription as the paywall offers it. */
data class ProPlan(
    val productId: String,
    val period: Period,
    /** The price of each [period] after any trial, formatted by the store in the user's currency. */
    val price: String,
    /** The free trial this account can still use, such as one week; null without one. */
    val freeTrial: java.time.Period?,
    val offerToken: String,
) {
    enum class Period { MONTH, YEAR }
}
