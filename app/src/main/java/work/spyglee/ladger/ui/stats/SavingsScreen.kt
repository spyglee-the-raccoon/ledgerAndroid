package work.spyglee.ladger.ui.stats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.SavingsMonth
import work.spyglee.ladger.domain.Statistics
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.AddToCollectionState
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.SectionHeader
import work.spyglee.ladger.ui.components.TransactionRow
import work.spyglee.ladger.ui.components.rememberAddToCollection
import work.spyglee.ladger.ui.formatCompactSigned
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.monthTitle
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.IncomeColor
import work.spyglee.ladger.ui.theme.toColor

private val DIFFERENCE_LABEL_HEIGHT = 20.dp
private const val BAR_WIDTH_RATIO = 0.6f

/**
 * The total balance month by month: each bar runs from 0 to the lower of the month's start and end
 * balance, with the month's change on top in green or red. Tapping a month lists its biggest
 * expenses and incomes.
 */
@Composable
fun SavingsScreen(onBack: () -> Unit, onOpenTransaction: (Long) -> Unit, onOpenCollection: (Long) -> Unit) {
    val viewModel = appViewModel { SavingsViewModel(it.database) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedMonth by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    val addToCollection = rememberAddToCollection(onAdded = onOpenCollection)

    Scaffold(topBar = { LadgerTopBar(stringResource(R.string.savings), onBack) }) { padding ->
        val current = state ?: return@Scaffold
        if (current.months.isEmpty()) {
            NoTransactions(Modifier.padding(padding))
            return@Scaffold
        }
        val months = current.months
        val selectedIndex = months.indexOfFirst { it.month == selectedMonth }.takeIf { it >= 0 }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "chart") {
                SavingsChart(
                    months,
                    selectedIndex,
                    onSelect = { index -> selectedMonth = index?.let { months[it].month } },
                    modifier = Modifier.padding(16.dp),
                )
            }
            selectedIndex?.let { months[it].month }?.let { month ->
                val zone = ZoneId.systemDefault()
                biggestSection(
                    key = "expenses",
                    titleRes = R.string.biggest_expenses,
                    month = month,
                    transactions = Statistics.biggest(current.transactions, TransactionKind.EXPENSE, month, zone),
                    state = current,
                    onOpenTransaction = onOpenTransaction,
                    addToCollection = addToCollection,
                )
                biggestSection(
                    key = "incomes",
                    titleRes = R.string.biggest_incomes,
                    month = month,
                    transactions = Statistics.biggest(current.transactions, TransactionKind.INCOME, month, zone),
                    state = current,
                    onOpenTransaction = onOpenTransaction,
                    addToCollection = addToCollection,
                )
            }
        }
    }
}

private fun LazyListScope.biggestSection(
    key: String,
    titleRes: Int,
    month: YearMonth,
    transactions: List<Transaction>,
    state: SavingsUiState,
    onOpenTransaction: (Long) -> Unit,
    addToCollection: AddToCollectionState,
) {
    item(key = "$key-header") {
        SectionHeader(stringResource(titleRes, monthTitle(month)), Modifier.padding(top = 8.dp))
    }
    if (transactions.isEmpty()) {
        item(key = "$key-empty") {
            Text(
                stringResource(R.string.no_transactions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    items(transactions, key = { "$key-${it.id}" }) { transaction ->
        TransactionRow(
            transaction = transaction,
            category = transaction.categoryId?.let(state.categories::get),
            onClick = { onOpenTransaction(transaction.id) },
            onAddToCollection = { addToCollection.start(transaction) },
        )
    }
}

@Composable
private fun SavingsChart(months: List<SavingsMonth>, selectedIndex: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val axis = remember(months) {
        ChartMath.yAxis(min(months.minOf { it.base }.toDouble(), 0.0), months.maxOf { it.top }.toDouble())
    }
    val baseColor = CategoryColor.BLUE.toColor()
    val unchangedColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun differenceColor(difference: BigDecimal): Color = when (difference.signum()) {
        1 -> IncomeColor
        -1 -> ExpenseColor
        else -> unchangedColor
    }

    MonthChart(
        months = months.map { it.month },
        axis = axis,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        describeMonth = { index ->
            val month = months[index]
            "${monthTitle(month.month)}, ${formatMoney(month.endBalance)}, ${formatCompactSigned(month.difference)}"
        },
        modifier = modifier,
        topLabelHeight = DIFFERENCE_LABEL_HEIGHT,
        topLabel = { index ->
            val difference = months[index].difference
            Text(
                formatCompactSigned(difference),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = differenceColor(difference),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        },
    ) { geometry ->
        months.forEachIndexed { index, month ->
            val dimmed = selectedIndex != null && selectedIndex != index
            val x = geometry.centerX(index)
            drawBar(x, geometry.y(0.0), geometry.y(month.base.toDouble()), geometry.columnWidth, baseColor.copy(alpha = if (dimmed) 0.2f else 0.5f))
            drawBar(
                x,
                geometry.y(month.base.toDouble()),
                geometry.y(month.top.toDouble()),
                geometry.columnWidth,
                differenceColor(month.difference).copy(alpha = if (dimmed) 0.3f else 1f),
            )
        }
    }
}

/** A bar centered on [centerX] between two y positions, in either order. */
private fun DrawScope.drawBar(centerX: Float, y1: Float, y2: Float, columnWidth: Float, color: Color) {
    val width = columnWidth * BAR_WIDTH_RATIO
    drawRect(color, topLeft = Offset(centerX - width / 2f, min(y1, y2)), size = Size(width, abs(y2 - y1)))
}
