package work.spyglee.ladger.scan

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The zoom buttons of the camera screen, like the system camera's 0.6× / 1× / 2× / 5×. */
object CameraZoom {
    private val TELE_PRESETS = listOf(2f, 5f)

    /**
     * Presets the camera supports: its widest zoom when that's below 1× (an ultra-wide lens),
     * then 1×, 2× and 5× while they're within [maxRatio].
     */
    fun presets(minRatio: Float, maxRatio: Float): List<Float> = buildList {
        if (minRatio < 0.95f) add(minRatio)
        add(1f)
        TELE_PRESETS.filter { it <= maxRatio }.forEach(::add)
    }

    /** The preset the current zoom belongs to: the largest one at or below it (with a little slack), or the smallest. */
    fun activeIndex(presets: List<Float>, ratio: Float): Int =
        presets.indexOfLast { it <= ratio + 0.05f }.coerceAtLeast(0)

    /** "1×", "2×" or "0.6×" / "1,4×" (one decimal, in the locale's format). */
    fun label(ratio: Float, locale: Locale = Locale.getDefault()): String {
        val rounded = (ratio * 10).roundToInt() / 10f
        return if (abs(rounded - rounded.roundToInt()) < 0.01f) {
            "${rounded.roundToInt()}×"
        } else {
            String.format(locale, "%.1f×", rounded)
        }
    }
}
