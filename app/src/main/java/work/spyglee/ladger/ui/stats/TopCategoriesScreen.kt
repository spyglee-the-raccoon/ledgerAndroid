package work.spyglee.ladger.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import work.spyglee.ladger.R
import work.spyglee.ladger.domain.TopCategories
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.monthTitle
import work.spyglee.ladger.ui.theme.toColor

/**
 * Monthly spending in the three categories with the biggest all-time spending, one line each, with
 * why it matters; tapping a month shows its amounts. Ported from iOS's TopCategoriesStatisticView.
 */
@Composable
fun TopCategoriesScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { TopCategoriesViewModel(it.database) }
    val topCategories by viewModel.topCategories.collectAsStateWithLifecycle()

    Scaffold(topBar = { LadgerTopBar(stringResource(R.string.top_categories), onBack) }) { padding ->
        val current = topCategories ?: return@Scaffold
        if (current.months.isEmpty()) {
            NoTransactions(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            TopCategoriesChart(current)
            ChartExplanation(
                title = stringResource(R.string.top_categories_about_title),
                paragraphs = listOf(
                    stringResource(R.string.top_categories_about_chart),
                    stringResource(R.string.top_categories_about_importance),
                ),
            )
        }
    }
}

@Composable
private fun TopCategoriesChart(topCategories: TopCategories) {
    val (categories, months) = topCategories
    var selectedMonth by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    val selectedIndex = months.indexOfFirst { it.month == selectedMonth }.takeIf { it >= 0 }
    val axis = remember(months) { ChartMath.yAxis(0.0, months.maxOf { it.amounts.max() }.toDouble()) }
    val ruleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val colors = categories.map { it.color.toColor() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            categories.forEachIndexed { index, category ->
                LegendItem(category.name, colors[index], ChartSymbol.forSeries(index))
            }
        }
        MonthChart(
            months = months.map { it.month },
            axis = axis,
            selectedIndex = selectedIndex,
            onSelect = { index -> selectedMonth = index?.let { months[it].month } },
            describeMonth = { index ->
                val month = months[index]
                val amounts = categories.indices.joinToString { "${categories[it].name}: ${formatMoney(month.amounts[it])}" }
                "${monthTitle(month.month)}, $amounts"
            },
            overlay = { columnWidth, scroll ->
                if (selectedIndex != null) {
                    val month = months[selectedIndex]
                    SelectionPopover(selectedIndex, columnWidth, scroll, monthTitle(month.month)) {
                        month.amounts.forEachIndexed { index, amount -> PopoverAmount(formatMoney(amount), colors[index]) }
                    }
                }
            },
        ) { geometry ->
            selectedIndex?.let { drawSelectionRule(geometry, it, ruleColor) }
            categories.indices.forEach { series ->
                val points = months.mapIndexed { i, month -> Offset(geometry.centerX(i), geometry.y(month.amounts[series].toDouble())) }
                drawSeries(points, colors[series], ChartSymbol.forSeries(series))
            }
        }
    }
}
