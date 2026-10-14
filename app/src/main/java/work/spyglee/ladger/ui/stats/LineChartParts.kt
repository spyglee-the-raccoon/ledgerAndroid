package work.spyglee.ladger.ui.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The symbols Swift Charts gives a chart's series, in its order: circle, square, triangle. */
enum class ChartSymbol(val shape: Shape) {
    CIRCLE(CircleShape),
    SQUARE(RectangleShape),
    TRIANGLE(GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }),
    ;

    companion object {
        fun forSeries(index: Int): ChartSymbol = entries[index % entries.size]
    }
}

/** A smooth line through [points] with a [symbol] on each, as Swift Charts' `.catmullRom` lines. */
fun DrawScope.drawSeries(points: List<Offset>, color: Color, symbol: ChartSymbol) {
    if (points.size > 1) {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            ChartMath.catmullRom(points).forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
    val size = 7.dp.toPx()
    val outline = symbol.shape.createOutline(Size(size, size), layoutDirection, this)
    for (point in points) {
        translate(point.x - size / 2f, point.y - size / 2f) { drawOutline(outline, color) }
    }
}

/** The dashed vertical line through the selected month. */
fun DrawScope.drawSelectionRule(geometry: ChartGeometry, index: Int, color: Color) {
    val x = geometry.centerX(index)
    drawLine(
        color,
        Offset(x, geometry.plotTop),
        Offset(x, geometry.plotBottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
}

@Composable
fun LegendItem(label: String, color: Color, symbol: ChartSymbol) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, symbol.shape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The popover over the month at [selectedIndex], for [MonthChart]'s overlay: centered over the
 * month, but kept inside the visible part of the chart.
 */
@Composable
fun SelectionPopover(selectedIndex: Int, columnWidth: Dp, scroll: ScrollState, title: String, content: @Composable ColumnScope.() -> Unit) {
    val columnWidthPx = with(LocalDensity.current) { columnWidth.toPx() }
    Layout(
        content = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                content()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val popover = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, popover.height) {
            val center = columnWidthPx * (selectedIndex + 0.5f) - scroll.value
            val x = (center - popover.width / 2f).roundToInt().coerceIn(0, maxOf(0, constraints.maxWidth - popover.width))
            popover.place(x, 0)
        }
    }
}

/** One amount in a [SelectionPopover], marked with its series' color. */
@Composable
fun PopoverAmount(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
