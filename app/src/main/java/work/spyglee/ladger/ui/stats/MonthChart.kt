package work.spyglee.ladger.ui.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import work.spyglee.ladger.ui.monthAxisLabel

/** Months visible at once; older months are reached by scrolling, as on iOS. */
const val VISIBLE_MONTHS = 6

val CHART_HEIGHT = 300.dp
private val AXIS_LABEL_HEIGHT = 24.dp

/** Space between the plot and whatever sits above it, so symbols at the top value aren't cut off. */
private val PLOT_TOP_PADDING = 8.dp

/** Where things go in the chart's scrolling content: one column per month, values mapped to y. */
class ChartGeometry(val columnWidth: Float, val plotTop: Float, val plotBottom: Float, private val axis: YAxis) {
    fun centerX(index: Int): Float = columnWidth * (index + 0.5f)

    fun y(value: Double): Float =
        plotBottom - ((value - axis.min) / (axis.max - axis.min)).toFloat() * (plotBottom - plotTop)
}

/**
 * A horizontally scrolling chart with one column per month, [VISIBLE_MONTHS] visible at a time and
 * scrolled to the latest month at first. Tapping a month selects it; tapping it again clears the
 * selection. Each month is a separate accessibility node described by [describeMonth].
 *
 * [draw] paints the data behind the columns. [topLabel] is optional content at the top of each
 * column, in a row [topLabelHeight] tall above the plot. [overlay] is drawn over the visible part
 * of the chart, not the scrolling content, and gets the scroll position to line things up.
 */
@Composable
fun MonthChart(
    months: List<YearMonth>,
    axis: YAxis,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    describeMonth: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    topLabelHeight: Dp = 0.dp,
    topLabel: @Composable (Int) -> Unit = {},
    overlay: @Composable BoxScope.(columnWidth: Dp, scroll: ScrollState) -> Unit = { _, _ -> },
    draw: DrawScope.(ChartGeometry) -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        val viewportWidth = maxWidth
        val columnWidth = viewportWidth / VISIBLE_MONTHS
        val scroll = rememberScrollState()
        LaunchedEffect(months.size) {
            // The scroll range is known after the first layout.
            scroll.scrollTo(snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE })
        }

        Row(
            Modifier
                .fillMaxHeight()
                .horizontalScroll(scroll)
                .widthIn(min = viewportWidth)
                .drawBehind {
                    val geometry = ChartGeometry(
                        columnWidth = columnWidth.toPx(),
                        plotTop = (topLabelHeight + PLOT_TOP_PADDING).toPx(),
                        plotBottom = size.height - AXIS_LABEL_HEIGHT.toPx(),
                        axis = axis,
                    )
                    for (value in axis.gridLines) {
                        val y = geometry.y(value)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    draw(geometry)
                },
        ) {
            months.forEachIndexed { index, month ->
                val isSelected = index == selectedIndex
                val description = describeMonth(index)
                Column(
                    Modifier
                        .width(columnWidth)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, interactionSource = null, indication = null) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(if (isSelected) null else index)
                        }
                        .clearAndSetSemantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(topLabelHeight), contentAlignment = Alignment.Center) { topLabel(index) }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.height(AXIS_LABEL_HEIGHT), contentAlignment = Alignment.Center) {
                        Text(
                            monthAxisLabel(month),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        overlay(columnWidth, scroll)
    }
}
