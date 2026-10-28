package work.spyglee.ladger.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.domain.PriceHistory
import work.spyglee.ladger.domain.PricePoint
import work.spyglee.ladger.domain.PriceTrend
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.SectionHeader
import work.spyglee.ladger.ui.formatDate
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.formatMonthYear
import work.spyglee.ladger.ui.formatPercent
import work.spyglee.ladger.ui.theme.toColor

/** Products from scanned receipts bought often enough to compare, as iOS's `PriceComparisonView`. */
class PriceComparisonViewModel(container: AppContainer) : ViewModel() {
    /** Null until loaded, so the empty state doesn't flash. */
    val histories: StateFlow<List<PriceHistory>?> = container.receiptFiles.observeDatedItems()
        .map { PriceHistory.histories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Personal inflation, then each tracked product with its price change, biggest change first. */
@Composable
fun PriceComparisonScreen(onBack: () -> Unit, onOpenHistory: (String) -> Unit) {
    val viewModel = appViewModel { PriceComparisonViewModel(it) }
    val histories by viewModel.histories.collectAsStateWithLifecycle()

    Scaffold(topBar = { LadgerTopBar(stringResource(R.string.price_comparison), onBack) }) { padding ->
        val loaded = histories ?: return@Scaffold
        if (loaded.isEmpty()) {
            NoPriceHistory(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "inflation") {
                PersonalInflation(loaded)
                HorizontalDivider()
            }
            item(key = "header") { SectionHeader(stringResource(R.string.tracked_items), Modifier.padding(top = 8.dp)) }
            items(loaded, key = { it.key }) { history ->
                PriceHistoryRow(history, onClick = { onOpenHistory(history.key) })
            }
        }
    }
}

@Composable
private fun NoPriceHistory(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.no_price_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            stringResource(R.string.no_price_history_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The average price change across all tracked products. */
@Composable
private fun PersonalInflation(histories: List<PriceHistory>) {
    val average = PriceHistory.personalInflation(histories)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.personal_inflation), style = MaterialTheme.typography.titleMedium)
        Text(
            formatPercent(average, fractionDigits = 1, signed = true),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = changeColor(average),
        )
        Text(
            pluralStringResource(R.plurals.personal_inflation_description, histories.size, histories.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PriceHistoryRow(history: PriceHistory, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(history.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(R.plurals.purchases_since, history.points.size, history.points.size, formatMonthYear(history.points.first().date)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatPercent(history.change, fractionDigits = 1, signed = true),
                fontWeight = FontWeight.Bold,
                color = changeColor(history.change),
            )
            Text(
                "${formatMoney(history.startPrice)} → ${formatMoney(history.currentPrice)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

class PriceHistoryViewModel(container: AppContainer, key: String) : ViewModel() {
    val history: StateFlow<PriceHistory?> = container.receiptFiles.observeDatedItems()
        .map { items -> PriceHistory.histories(items).firstOrNull { it.key == key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** One product's unit price over time, then every purchase, newest first. */
@Composable
fun PriceHistoryScreen(key: String, onBack: () -> Unit) {
    val viewModel = appViewModel { PriceHistoryViewModel(it, key) }
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(topBar = { LadgerTopBar(history?.name.orEmpty(), onBack) }) { padding ->
        val loaded = history ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "chart") {
                PriceChart(
                    loaded.points,
                    Modifier.padding(16.dp).semantics {
                        contentDescription = "${loaded.name}: ${loaded.points.first().unitPrice} → ${loaded.points.last().unitPrice}"
                    },
                )
                Text(
                    stringResource(R.string.price_history_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "header") { SectionHeader(stringResource(R.string.purchases), Modifier.padding(top = 16.dp)) }
            items(loaded.points.asReversed(), key = { it.id }) { point ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDate(point.date))
                        if (point.store.isNotEmpty()) {
                            Text(point.store, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(formatMoney(point.unitPrice))
                }
            }
        }
    }
}

/**
 * Unit price over time as a smooth line with a dot per purchase, purchases placed by date, the y axis
 * fitted to the prices rather than starting at zero, as iOS's chart with `includesZero: false`.
 */
@Composable
private fun PriceChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val prices = points.map { it.unitPrice.toDouble() }
    val axis = ChartMath.yAxis(prices.min(), prices.max())
    val yLabels = axis.gridLines.map { formatMoney(BigDecimal.valueOf(it)) }
    val firstDate = formatDate(points.first().date)
    val lastDate = formatDate(points.last().date)

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val labelWidth = yLabels.maxOf { measurer.measure(it, labelStyle).size.width } + 8.dp.toPx()
        val xLabelHeight = measurer.measure(firstDate, labelStyle).size.height + 6.dp.toPx()
        val inset = 8.dp.toPx()
        val plotLeft = inset
        val plotRight = size.width - labelWidth - inset
        val plotTop = inset
        val plotBottom = size.height - xLabelHeight
        fun y(value: Double) = plotBottom - ((value - axis.min) / (axis.max - axis.min)).toFloat() * (plotBottom - plotTop)

        axis.gridLines.forEachIndexed { index, value ->
            drawLine(gridColor, Offset(0f, y(value)), Offset(size.width - labelWidth, y(value)), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(yLabels[index], labelStyle)
            drawText(label, topLeft = Offset(size.width - label.size.width, y(value) - label.size.height / 2f))
        }

        val start = points.first().date.toEpochMilli()
        val span = (points.last().date.toEpochMilli() - start).coerceAtLeast(1)
        val offsets = points.map { point ->
            val x = if (points.size == 1) (plotLeft + plotRight) / 2f
            else plotLeft + (point.date.toEpochMilli() - start).toFloat() / span * (plotRight - plotLeft)
            Offset(x, y(point.unitPrice.toDouble()))
        }
        drawSeries(offsets, lineColor, ChartSymbol.CIRCLE)

        drawText(measurer.measure(firstDate, labelStyle), topLeft = Offset(plotLeft, size.height - xLabelHeight + 6.dp.toPx()))
        val last = measurer.measure(lastDate, labelStyle)
        drawText(last, topLeft = Offset(plotRight - last.size.width, size.height - xLabelHeight + 6.dp.toPx()))
    }
}

/** Rising prices are bad for the user, so they're red; falling prices are green. */
@Composable
private fun changeColor(change: Double): Color = when (PriceHistory.trend(change)) {
    PriceTrend.UP -> CategoryColor.RED.toColor()
    PriceTrend.DOWN -> CategoryColor.GREEN.toColor()
    PriceTrend.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
}
