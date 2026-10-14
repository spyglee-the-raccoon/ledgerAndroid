package work.spyglee.ladger.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import work.spyglee.ladger.R
import work.spyglee.ladger.domain.IncomeExpenseMonth
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.monthTitle
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.IncomeColor

/** Monthly incomes and expenses as two lines, with why they matter; tapping a month shows its totals. */
@Composable
fun IncomeExpenseScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { IncomeExpenseViewModel(it.database) }
    val months by viewModel.months.collectAsStateWithLifecycle()

    Scaffold(topBar = { LadgerTopBar(stringResource(R.string.income_expense), onBack) }) { padding ->
        when {
            months == null -> Unit
            months!!.isEmpty() -> NoTransactions(Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                IncomeExpenseChart(months!!)
                ChartExplanation(
                    title = stringResource(R.string.income_expense_about_title),
                    paragraphs = listOf(
                        stringResource(R.string.income_expense_about_chart),
                        stringResource(R.string.income_expense_about_importance),
                    ),
                )
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
            LegendItem(incomesLabel, IncomeColor, ChartSymbol.CIRCLE)
            LegendItem(expensesLabel, ExpenseColor, ChartSymbol.SQUARE)
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
                    val month = months[selectedIndex]
                    SelectionPopover(selectedIndex, columnWidth, scroll, monthTitle(month.month)) {
                        PopoverAmount(formatMoney(month.incomes), IncomeColor)
                        PopoverAmount(formatMoney(month.expenses), ExpenseColor)
                    }
                }
            },
        ) { geometry ->
            selectedIndex?.let { drawSelectionRule(geometry, it, ruleColor) }
            drawSeries(months.mapIndexed { i, month -> Offset(geometry.centerX(i), geometry.y(month.incomes.toDouble())) }, IncomeColor, ChartSymbol.CIRCLE)
            drawSeries(months.mapIndexed { i, month -> Offset(geometry.centerX(i), geometry.y(month.expenses.toDouble())) }, ExpenseColor, ChartSymbol.SQUARE)
        }
    }
}

/** What a statistics chart shows and why it matters, under the chart, as on iOS. */
@Composable
internal fun ChartExplanation(title: String, paragraphs: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        paragraphs.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun NoTransactions(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.no_transactions), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
