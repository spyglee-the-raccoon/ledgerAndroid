package work.spyglee.ladger.ui.stats

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {
    @Test
    fun axisUsesRoundStepsAndCoversTheRange() {
        val axis = ChartMath.yAxis(0.0, 2_350.0)
        assertEquals(listOf(0.0, 1_000.0, 2_000.0, 3_000.0), axis.gridLines)
        assertEquals(0.0, axis.min, 0.0)
        assertEquals(3_000.0, axis.max, 0.0)
    }

    @Test
    fun axisIncludesNegativeValues() {
        val axis = ChartMath.yAxis(-420.0, 1_800.0)
        assertEquals(-1_000.0, axis.min, 0.0)
        assertEquals(2_000.0, axis.max, 0.0)
        assertEquals(listOf(-1_000.0, 0.0, 1_000.0, 2_000.0), axis.gridLines)
    }

    @Test
    fun axisForAllZeroValuesStillHasARange() {
        val axis = ChartMath.yAxis(0.0, 0.0)
        assertEquals(0.0, axis.min, 0.0)
        assertEquals(1.0, axis.max, 1e-9)
    }

    @Test
    fun axisUsesTwoAndAHalfSteps() {
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), ChartMath.yAxis(0.0, 90.0).gridLines)
    }

    @Test
    fun catmullRomPassesThroughEveryPoint() {
        val points = listOf(Offset(0f, 10f), Offset(10f, 0f), Offset(20f, 5f), Offset(30f, 5f))
        val segments = ChartMath.catmullRom(points)
        assertEquals(points.drop(1), segments.map { it.end })
    }

    @Test
    fun catmullRomOnAStraightLineStaysOnIt() {
        val points = listOf(Offset(0f, 0f), Offset(10f, 10f), Offset(20f, 20f))
        ChartMath.catmullRom(points).forEach { segment ->
            assertEquals(segment.control1.x, segment.control1.y, 1e-4f)
            assertEquals(segment.control2.x, segment.control2.y, 1e-4f)
        }
    }

    @Test
    fun catmullRomWithOnePointHasNoSegments() {
        assertEquals(emptyList<CurveSegment>(), ChartMath.catmullRom(listOf(Offset(1f, 1f))))
    }
}
