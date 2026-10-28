package work.spyglee.ladger.domain

import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import kotlin.math.abs
import work.spyglee.ladger.data.DatedReceiptItem

/** One purchase of a product, from a scanned receipt line. [store] is the transaction's note. */
data class PricePoint(val id: Long, val date: Instant, val unitPrice: BigDecimal, val store: String)

/** Whether a price went up (bad for the user), down, or stayed about the same. */
enum class PriceTrend { UP, DOWN, FLAT }

/** The price development of a product bought often enough to compare, oldest purchase first. */
data class PriceHistory(val key: String, val name: String, val points: List<PricePoint>) {
    val startPrice: BigDecimal get() = average(points.take(SAMPLE_SIZE))
    val currentPrice: BigDecimal get() = average(points.takeLast(SAMPLE_SIZE))

    /** Relative change from the first to the latest purchases, e.g. 0.12 for +12 %. */
    val change: Double
        get() = if (startPrice.signum() > 0) (currentPrice - startPrice).divide(startPrice, MathContext.DECIMAL64).toDouble() else 0.0

    companion object {
        /** Purchases needed before a product is compared ("more than 10 times"). */
        const val MINIMUM_PURCHASES = 11

        /** Purchases averaged at each end, so a single sale or OCR error doesn't skew the change. */
        private const val SAMPLE_SIZE = 3

        private fun average(points: List<PricePoint>): BigDecimal =
            if (points.isEmpty()) BigDecimal.ZERO
            else points.sumOf { it.unitPrice }.divide(BigDecimal(points.size), MathContext.DECIMAL64)

        /** Receipt lines grouped by product, keeping the often bought ones, biggest change first. */
        fun histories(items: List<DatedReceiptItem>): List<PriceHistory> =
            items.filter { it.unitPrice.signum() > 0 }
                .groupBy { it.normalizedName }
                .mapNotNull { (key, purchases) ->
                    if (purchases.size < MINIMUM_PURCHASES) return@mapNotNull null
                    val dated = purchases.sortedWith(compareBy({ it.date }, { it.id }))
                    PriceHistory(key, dated.last().name, dated.map { PricePoint(it.id, it.date, it.unitPrice, it.store) })
                }
                .sortedWith(compareByDescending<PriceHistory> { abs(it.change) }.thenBy { it.key })

        /** Average change across all tracked products. */
        fun personalInflation(histories: List<PriceHistory>): Double =
            if (histories.isEmpty()) 0.0 else histories.sumOf { it.change } / histories.size

        /** Changes within half a percent count as unchanged. */
        fun trend(change: Double): PriceTrend = when {
            change > 0.005 -> PriceTrend.UP
            change < -0.005 -> PriceTrend.DOWN
            else -> PriceTrend.FLAT
        }
    }
}
