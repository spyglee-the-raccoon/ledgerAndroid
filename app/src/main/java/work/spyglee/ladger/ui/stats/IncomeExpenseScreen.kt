package work.spyglee.ladger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import kotlin.math.roundToInt
import work.spyglee.ladger.R
import work.spyglee.ladger.domain.IncomeExpenseMonth
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.monthTitle
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.IncomeColor

/** Monthly incomes and expenses as two lines; tapping a month shows its totals. */
@Composable
fun IncomeExpenseScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { IncomeExpenseViewModel(it.database) }
    val months by viewModel.months.collectAsStateWithLifecycle()

    Scaffold(topBar = { LadgerTopBar(stringResource(R.string.income_expense), onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                months == null -> Unit
                months!!.isEmpty() -> NoTransactions()
                else -> IncomeExpenseChart(months!!)
            }
        }
    }
}

@Composable
private fun IncomeExpenseChart(months: List<IncomeExpenseMonth>) {
    var selectedMonth by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    val selectedIndex = months.indexOfFirst { it.month == selectedMonth }.takeIf { it >= 0 }
    val axis = remember(months) { ChartMath.yAxis(0.0, months.maxOf { maxOf(it.incomes, it.expenses) }.toDouble()) }
    val ruleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val incomesLabel = stringResource(R.string.incomes)
    val expensesLabel = stringResource(R.string.expenses)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(incomesLabel, IncomeColor, CircleShape)
            LegendItem(expensesLabel, ExpenseColor, RectangleShape)
        }
        MonthChart(
            months = months.map { it.month },
            axis = axis,
            selectedIndex = selectedIndex,
            onSelect = { index -> selectedMonth = index?.let { months[it].month } },
            describeMonth = { index ->
                val month = months[index]
                "${monthTitle(month.month)}, $incomesLabel: ${formatMoney(month.incomes)}, $expensesLabel: ${formatMoney(month.expenses)}"
            },
            overlay = { columnWidth, scroll ->
                if (selectedIndex != null) {
                    val columnWidthPx = with(LocalDensity.current) { columnWidth.toPx() }
                    // Centered over the selected month, but kept inside the visible part of the chart.
                    Layout(content = { SelectionPopover(months[selectedIndex]) }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
                        val popover = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.maxWidth, popover.height) {
                            val center = columnWidthPx * (selectedIndex + 0.5f) - scroll.value
                            val x = (center - popover.width / 2f).roundToInt().coerceIn(0, maxOf(0, constraints.maxWidth - popover.width))
                            popover.place(x, 0)
                        }
                    }
                }
            },
        ) { geometry ->
            selectedIndex?.let { index ->
                val x = geometry.centerX(index)
                drawLine(
                    ruleColor,
                    Offset(x, geometry.plotTop),
                    Offset(x, geometry.plotBottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            drawSeries(months.mapIndexed { i, month -> Offset(geometry.centerX(i), geometry.y(month.incomes.toDouble())) }, IncomeColor, square = false)
            drawSeries(months.mapIndexed { i, month -> Offset(geometry.centerX(i), geometry.y(month.expenses.toDouble())) }, ExpenseColor, square = true)
        }
    }
}

/** A smooth line through [points] with a symbol on each: circles for incomes, squares for expenses, as Swift Charts picks them. */
private fun DrawScope.drawSeries(points: List<Offset>, color: Color, square: Boolean) {
    if (points.size > 1) {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            ChartMath.catmullRom(points).forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
    val symbol = 7.dp.toPx()
    for (point in points) {
        if (square) {
            drawRect(color, topLeft = point - Offset(symbol / 2, symbol / 2), size = Size(symbol, symbol))
        } else {
            drawCircle(color, radius = symbol / 2, center = point)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, shape: Shape) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, shape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectionPopover(month: IncomeExpenseMonth) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(monthTitle(month.month), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        PopoverAmount(formatMoney(month.incomes), IncomeColor)
        PopoverAmount(formatMoney(month.expenses), ExpenseColor)
    }
}

@Composable
private fun PopoverAmount(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun NoTransactions(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.no_transactions), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
