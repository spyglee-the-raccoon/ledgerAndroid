package work.spyglee.ladger.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import java.time.Instant
import java.util.Locale
import work.spyglee.ladger.LadgerApp
import work.spyglee.ladger.R
import work.spyglee.ladger.domain.WidgetSnapshot
import work.spyglee.ladger.ui.formatMoney

/** This month's spending by category around the month balance, like the main screen chart (iOS `MonthChartWidget`). */
class MonthChartWidget : GlanceAppWidget() {
    // Exact rather than Responsive: launchers' cells are often larger than the minimum sizes, and the
    // ring should fill the widget.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = (context.applicationContext as LadgerApp).container.widgetData
        val initial = data.load()
        provideContent {
            val state by remember { data.observe() }.collectAsState(initial)
            WidgetFrame(link = null) { MonthChartContent(state) }
        }
    }

    companion object {
        /** Wider than this times its height (4 × 2 cells, iOS's medium size), the widget also lists the top categories. */
        const val LEGEND_ASPECT = 1.5f
    }
}

class MonthChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MonthChartWidget()
}

@Composable
private fun MonthChartContent(state: WidgetState) {
    val snapshot = state.snapshot.current(Instant.now())
    val size = LocalSize.current
    if (size.width >= size.height * MonthChartWidget.LEGEND_ASPECT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(snapshot, state.hideAmounts)
            Spacer(GlanceModifier.width(16.dp))
            Legend(snapshot, state.hideAmounts)
        }
    } else {
        Ring(snapshot, state.hideAmounts)
    }
}

/** The ring with "This month balance" in its hole; with the app lock on, a lock instead of the amount (D5). */
@Composable
private fun Ring(snapshot: WidgetSnapshot, hideAmounts: Boolean) {
    val context = LocalContext.current
    val widgetSize = LocalSize.current
    val diameter = min(widgetSize.height, widgetSize.width) - 24.dp
    val px = (diameter.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    // The text stays inside the hole of the ring.
    val holeWidth = diameter * 0.92f * 0.618f * 0.8f
    Box(modifier = GlanceModifier.size(diameter), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(WidgetBitmaps.ring(context, snapshot.slices, px)),
            contentDescription = null,
            modifier = GlanceModifier.size(diameter),
        )
        Column(modifier = GlanceModifier.width(holeWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = context.getString(R.string.this_month_balance).uppercase(Locale.getDefault()),
                style = secondaryText(size = 8.sp).copy(textAlign = TextAlign.Center),
                maxLines = 2,
            )
            if (hideAmounts) {
                LockIcon()
            } else {
                Text(
                    text = formatMoney(snapshot.monthBalance, wholeUnits = true),
                    style = primaryText(size = 14.sp, bold = true).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The month's top four categories next to the ring, amounts left out with the app lock on (D5). */
@Composable
private fun Legend(snapshot: WidgetSnapshot, hideAmounts: Boolean) {
    val context = LocalContext.current
    if (snapshot.slices.isEmpty()) {
        Text(context.getString(R.string.widget_chart_description), style = secondaryText())
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        snapshot.slices.take(4).forEachIndexed { index, slice ->
            if (index > 0) Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.size(8.dp).cornerRadius(4.dp).background(slice.color.provider())) {}
                Spacer(GlanceModifier.width(6.dp))
                Text(slice.name, style = primaryText(size = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                if (!hideAmounts) {
                    Spacer(GlanceModifier.width(4.dp))
                    Text(formatMoney(slice.amount, wholeUnits = true), style = primaryText(size = 12.sp, bold = true), maxLines = 1)
                }
            }
        }
    }
}

/** Stands in for amounts while the app lock is on (D5). */
@Composable
fun LockIcon() {
    val context = LocalContext.current
    val px = (14 * context.resources.displayMetrics.density).toInt()
    Image(
        provider = ImageProvider(WidgetBitmaps.badge(Icons.Outlined.Lock, android.graphics.Color.WHITE, px, circle = false)),
        contentDescription = context.getString(R.string.widget_locked),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        modifier = GlanceModifier.size(14.dp),
    )
}
