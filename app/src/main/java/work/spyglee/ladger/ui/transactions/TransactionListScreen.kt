package work.spyglee.ladger.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.Balances
import work.spyglee.ladger.domain.DayGrouping
import work.spyglee.ladger.domain.DaySection
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.rememberAddToCollection
import work.spyglee.ladger.ui.components.transactionSections

/** Which transactions a list shows. */
sealed interface TransactionFilter {
    data object Expenses : TransactionFilter
    data object Incomes : TransactionFilter
    /** Transactions missing the source or destination their kind needs. */
    data object Unassigned : TransactionFilter
    data class Source(val sourceId: Long) : TransactionFilter
}

data class TransactionListState(
    val sections: List<DaySection> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val sourceName: String? = null,
)

class TransactionListViewModel(private val database: LadgerDatabase, val filter: TransactionFilter) : ViewModel() {
    private val dao = database.transactionDao()

    private val transactions = when (filter) {
        TransactionFilter.Expenses -> dao.observeByKind(TransactionKind.EXPENSE)
        TransactionFilter.Incomes -> dao.observeByKind(TransactionKind.INCOME)
        TransactionFilter.Unassigned -> dao.observeAll().map { all -> all.filter(Balances::isUnassigned) }
        is TransactionFilter.Source -> dao.observeForSource(filter.sourceId)
    }

    val state: StateFlow<TransactionListState> = combine(
        transactions,
        database.categoryDao().observeAll(),
        database.sourceDao().observeAll(),
    ) { items, categories, sources ->
        TransactionListState(
            sections = DayGrouping.groupByDay(items, ZoneId.systemDefault()),
            categories = categories.associateBy { it.id },
            sourceName = (filter as? TransactionFilter.Source)?.let { f -> sources.firstOrNull { it.id == f.sourceId }?.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionListState())

    fun delete(transaction: Transaction) {
        viewModelScope.launch { dao.delete(transaction) }
    }
}

@Composable
fun TransactionListScreen(
    filter: TransactionFilter,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onAddTransaction: (TransactionKind) -> Unit,
    onOpenCollection: (Long) -> Unit,
) {
    val viewModel = appViewModel { TransactionListViewModel(it.database, filter) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addToCollection = rememberAddToCollection(onAdded = onOpenCollection)

    // Expense and income lists can add and delete; per-source lists only show, as on iOS.
    val addKind = when (filter) {
        TransactionFilter.Expenses -> TransactionKind.EXPENSE
        TransactionFilter.Incomes -> TransactionKind.INCOME
        else -> null
    }
    val title = when (filter) {
        TransactionFilter.Expenses -> stringResource(R.string.expenses)
        TransactionFilter.Incomes -> stringResource(R.string.incomes)
        TransactionFilter.Unassigned -> stringResource(R.string.transactions_without_source)
        is TransactionFilter.Source -> stringResource(R.string.transactions_for, state.sourceName.orEmpty())
    }

    Scaffold(
        topBar = {
            LadgerTopBar(title, onBack) {
                if (addKind != null) {
                    IconButton(onClick = { onAddTransaction(addKind) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_transaction))
                    }
                }
            }
        },
    ) { padding ->
        if (state.sections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_transactions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                transactionSections(
                    sections = state.sections,
                    categories = state.categories,
                    onClick = { onOpenTransaction(it.id) },
                    onSwipe = if (addKind != null) viewModel::delete else null,
                    onAddToCollection = addToCollection::start,
                )
            }
        }
    }
}
