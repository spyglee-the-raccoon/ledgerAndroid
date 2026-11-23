package work.spyglee.ladger.billing

import java.time.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProTest {
    private fun purchase(
        product: String = Pro.MONTHLY,
        state: StorePurchase.State = StorePurchase.State.PURCHASED,
        acknowledged: Boolean = true,
        suspended: Boolean = false,
        verified: Boolean = true,
    ) = StorePurchase(listOf(product), state, acknowledged, suspended, token = "t-$product", isVerified = verified)

    @Test
    fun aPaidSignedPurchaseOfEitherProductMakesTheUserPro() {
        assertEquals(Pro.MONTHLY, Pro.activeProduct(listOf(purchase(Pro.MONTHLY))))
        assertEquals(Pro.YEARLY, Pro.activeProduct(listOf(purchase(Pro.YEARLY))))
    }

    @Test
    fun pendingSuspendedUnsignedOrOtherPurchasesDont() {
        assertNull(Pro.activeProduct(emptyList()))
        assertNull(Pro.activeProduct(listOf(purchase(state = StorePurchase.State.PENDING))))
        assertNull(Pro.activeProduct(listOf(purchase(suspended = true))))
        assertNull(Pro.activeProduct(listOf(purchase(verified = false))))
        assertNull(Pro.activeProduct(listOf(purchase(product = "something.else"))))
    }

    @Test
    fun onlyPaidUnacknowledgedProPurchasesAreAcknowledged() {
        val waiting = purchase(acknowledged = false)
        val purchases = listOf(
            waiting,
            purchase(Pro.YEARLY, acknowledged = true),
            purchase(Pro.YEARLY, state = StorePurchase.State.PENDING, acknowledged = false),
            purchase("something.else", acknowledged = false),
        )
        assertEquals(listOf(waiting), Pro.toAcknowledge(purchases))
    }

    private fun phase(micros: Long, price: String, period: String, recurring: Boolean) = StorePhase(micros, price, period, recurring)

    @Test
    fun theTrialOfferIsShownWhenGooglePlayOffersItMonthlyFirst() {
        val monthly = StoreProduct(
            Pro.MONTHLY,
            listOf(
                StoreOffer(null, "m-base", listOf(phase(2_990_000, "€2.99", "P1M", true))),
                StoreOffer("trial", "m-trial", listOf(phase(0, "Free", "P1W", false), phase(2_990_000, "€2.99", "P1M", true))),
            ),
        )
        val yearly = StoreProduct(Pro.YEARLY, listOf(StoreOffer(null, "y-base", listOf(phase(29_990_000, "€29.99", "P1Y", true)))))

        val plans = Pro.plans(listOf(yearly, monthly))

        assertEquals(
            listOf(
                ProPlan(Pro.MONTHLY, ProPlan.Period.MONTH, "€2.99", Period.ofDays(7), "m-trial"),
                // No trial offer: this account has used it, so Google Play returns only the base plan.
                ProPlan(Pro.YEARLY, ProPlan.Period.YEAR, "€29.99", null, "y-base"),
            ),
            plans,
        )
    }

    @Test
    fun aProductMissingFromTheStoreIsLeftOut() {
        val yearly = StoreProduct(Pro.YEARLY, listOf(StoreOffer(null, "y", listOf(phase(29_990_000, "€29.99", "P1Y", true)))))
        assertEquals(listOf(Pro.YEARLY), Pro.plans(listOf(yearly)).map { it.productId })
        assertEquals(emptyList<ProPlan>(), Pro.plans(listOf(StoreProduct(Pro.MONTHLY, emptyList()))))
    }
}
