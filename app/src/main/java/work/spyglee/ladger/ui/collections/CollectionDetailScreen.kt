package work.spyglee.ladger.ui.collections

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CollectionRepository
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionCollection
import work.spyglee.ladger.domain.CategoryTotal
import work.spyglee.ladger.domain.Collections
import work.spyglee.ladger.domain.DayGrouping
import work.spyglee.ladger.domain.DaySection
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.ListRow
import work.spyglee.ladger.ui.components.SearchField
import work.spyglee.ladger.ui.components.SectionHeader
import work.spyglee.ladger.ui.components.rememberAddToCollection
import work.spyglee.ladger.ui.components.transactionSections
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.sources.matches
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

data class CollectionDetailState(
    val loaded: Boolean = false,
    /** Null once the collection has been deleted. */
    val collection: TransactionCollection? = null,
    val total: BigDecimal = BigDecimal.ZERO,
    val topCategories: List<CategoryTotal> = emptyList(),
    val sections: List<DaySection> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
)

class CollectionDetailViewModel(database: LadgerDatabase, private val repository: CollectionRepository, private val collectionId: Long) : ViewModel() {
    val query = MutableStateFlow("")

    val state: StateFlow<CollectionDetailState> = combine(
        database.collectionDao().observeWithTransactions(collectionId),
        database.categoryDao().observeAll(),
        query,
    ) { withTransactions, categories, query ->
        val transactions = withTransactions?.transactions.orEmpty()
        val byId = categories.associateBy { it.id }
        CollectionDetailState(
            loaded = true,
            collection = withTransactions?.collection,
            total = Collections.total(transactions),
            topCategories = Collections.topCategories(transactions, categories),
            sections = DayGrouping.groupByDay(transactions.filter { it.matches(query, byId) }, ZoneId.systemDefault()),
            categories = byId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailState())

    /** Takes the transaction out of this collection only; it stays everywhere else. */
    fun remove(transaction: Transaction) {
        viewModelScope.launch { repository.removeTransactions(collectionId, listOf(transaction.id)) }
    }
}

@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
) {
    val viewModel = appViewModel { CollectionDetailViewModel(it.database, it.collections, collectionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val addToCollection = rememberAddToCollection(onAdded = { if (it != collectionId) onOpenCollection(it) })

    LaunchedEffect(state.loaded, state.collection) {
        if (state.loaded && state.collection == null) onBack()
    }
    val collection = state.collection ?: return

    Scaffold(
        topBar = {
            LadgerTopBar(collection.name, onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_collection)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "summary") {
                ListRow(
                    title = collection.name,
                    leading = { CategoryIcon(iconFor(collection.systemImage), collection.color.toColor()) },
                    trailing = formatMoney(state.total),
                )
                HorizontalDivider()
            }
            if (state.topCategories.isNotEmpty()) {
                item(key = "top-header") { SectionHeader(stringResource(R.string.top_categories), Modifier.padding(top = 8.dp)) }
                items(state.topCategories, key = { "top-${it.category.id}" }) { (category, total) ->
                    ListRow(
                        title = category.name,
                        leading = { CategoryIcon(iconFor(category.systemImage), category.color.toColor()) },
                        trailing = formatMoney(total),
                    )
                }
            }
            item(key = "transactions-header") { SectionHeader(stringResource(R.string.transactions), Modifier.padding(top = 8.dp)) }
            item(key = "search") {
                SearchField(query, { viewModel.query.value = it }, stringResource(R.string.search_transactions), Modifier.padding(horizontal = 16.dp))
            }
            transactionSections(
                sections = state.sections,
                categories = state.categories,
                onClick = { onOpenTransaction(it.id) },
                onSwipe = viewModel::remove,
                onAddToCollection = addToCollection::start,
            )
            if (state.sections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_transactions),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
        }
    }
}
