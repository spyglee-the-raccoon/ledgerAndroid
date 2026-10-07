package work.spyglee.ladger.ui.stats

import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** The value range of a chart's y axis and where its grid lines go. */
data class YAxis(val min: Double, val max: Double, val gridLines: List<Double>)

/** One cubic Bézier piece of a curve, from the previous end point to [end]. */
data class CurveSegment(val control1: Offset, val control2: Offset, val end: Offset)

object ChartMath {
    /**
     * A y axis that covers [low]..[high] with about [targetLines] grid lines at round values
     * (1, 2, 2.5 or 5 times a power of ten). Both ends are moved out to the nearest grid line.
     */
    fun yAxis(low: Double, high: Double, targetLines: Int = 4): YAxis {
        val top = if (high > low) high else low + 1.0
        val step = niceStep((top - low) / targetLines)
        val min = floor(low / step) * step
        val max = ceil(top / step) * step
        val count = ((max - min) / step).roundToInt()
        return YAxis(min, max, (0..count).map { min + it * step })
    }

    private fun niceStep(raw: Double): Double {
        val magnitude = 10.0.pow(floor(log10(raw)))
        val fraction = raw / magnitude
        val nice = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    /**
     * A smooth curve through all [points] (uniform Catmull-Rom, like Swift Charts' `.catmullRom`),
     * as Bézier segments starting from the first point. The end points are repeated so the curve
     * starts and ends exactly on them.
     */
    fun catmullRom(points: List<Offset>): List<CurveSegment> =
        (0 until points.size - 1).map { i ->
            val p0 = points[maxOf(i - 1, 0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[minOf(i + 2, points.size - 1)]
            CurveSegment(
                control1 = p1 + (p2 - p0) / 6f,
                control2 = p2 - (p3 - p1) / 6f,
                end = p2,
            )
        }
}
