package work.spyglee.ladger.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.BudgetRepository
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.domain.Balances
import work.spyglee.ladger.domain.DayGrouping
import work.spyglee.ladger.domain.DaySection
import work.spyglee.ladger.domain.byTransaction
import work.spyglee.ladger.domain.matches
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.ListRow
import work.spyglee.ladger.ui.components.SearchField
import work.spyglee.ladger.ui.components.rememberAddToCollection
import work.spyglee.ladger.ui.components.transactionSections
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

data class SourceBalance(val source: Source, val balance: BigDecimal)

data class SourcesOverviewState(
    val sources: List<SourceBalance> = emptyList(),
    /** Balance of transactions without a source or destination, or null when there are none. */
    val unassignedBalance: BigDecimal? = null,
    val sections: List<DaySection> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
)

class SourcesOverviewViewModel(private val database: LadgerDatabase, private val budgets: BudgetRepository) : ViewModel() {
    val query = MutableStateFlow("")

    val state: StateFlow<SourcesOverviewState> = combine(
        database.sourceDao().observeAll(),
        database.transactionDao().observeAll(),
        database.categoryDao().observeAll(),
        database.receiptItemDao().observeNames(),
        query,
    ) { sources, transactions, categories, itemNames, query ->
        val byId = categories.associateBy { it.id }
        SourcesOverviewState(
            sources = sources.map { SourceBalance(it, Balances.forSource(it.id, it.initValue, transactions)) },
            unassignedBalance = if (transactions.any(Balances::isUnassigned)) Balances.forSource(null, BigDecimal.ZERO, transactions) else null,
            sections = DayGrouping.groupByDay(transactions.filter { it.matches(query, byId, itemNames.byTransaction()) }, ZoneId.systemDefault()),
            categories = byId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesOverviewState())

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            database.transactionDao().delete(transaction)
            budgets.check()
        }
    }
}

/** Every source with its balance, then all transactions with search; both sections collapse. */
@Composable
fun SourcesOverviewScreen(
    onBack: () -> Unit,
    onOpenSource: (Long) -> Unit,
    onOpenUnassigned: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
) {
    val viewModel = appViewModel { SourcesOverviewViewModel(it.database, it.budgets) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var sourcesExpanded by rememberSaveable { mutableStateOf(true) }
    var transactionsExpanded by rememberSaveable { mutableStateOf(true) }
    val addToCollection = rememberAddToCollection(onAdded = onOpenCollection)

    Scaffold(topBar = { LadgerTopBar(stringResource(R.string.total_balance), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "sources-header") {
                CollapsibleHeader(stringResource(R.string.sources), sourcesExpanded) { sourcesExpanded = !sourcesExpanded }
            }
            if (sourcesExpanded) {
                items(state.sources, key = { "source-${it.source.id}" }) { (source, balance) ->
                    ListRow(
                        title = source.name,
                        leading = { CategoryIcon(iconFor(source.systemImage), source.color.toColor()) },
                        trailing = formatMoney(balance),
                        onClick = { onOpenSource(source.id) },
                    )
                }
            }
            item(key = "transactions-header") {
                CollapsibleHeader(stringResource(R.string.transactions), transactionsExpanded) { transactionsExpanded = !transactionsExpanded }
            }
            if (transactionsExpanded) {
                item(key = "search") {
                    SearchField(
                        query,
                        { viewModel.query.value = it },
                        stringResource(R.string.search_transactions),
                        Modifier.padding(horizontal = 16.dp),
                    )
                }
                transactionSections(
                    sections = state.sections,
                    categories = state.categories,
                    onClick = { onOpenTransaction(it.id) },
                    onSwipe = viewModel::delete,
                    onAddToCollection = addToCollection::start,
                )
            }
            state.unassignedBalance?.let { balance ->
                item(key = "unassigned") {
                    ListRow(
                        title = stringResource(R.string.no_source),
                        leading = { CategoryIcon(Icons.Outlined.QuestionMark, CategoryColor.GRAY.toColor()) },
                        trailing = formatMoney(balance),
                        onClick = onOpenUnassigned,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
    }
}
