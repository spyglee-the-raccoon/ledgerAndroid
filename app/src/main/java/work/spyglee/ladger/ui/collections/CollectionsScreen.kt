package work.spyglee.ladger.ui.collections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.math.BigDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CollectionRepository
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.TransactionCollection
import work.spyglee.ladger.domain.Collections
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.ListRow
import work.spyglee.ladger.ui.components.SwipeToDelete
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

data class CollectionTotal(val collection: TransactionCollection, val total: BigDecimal)

class CollectionsViewModel(database: LadgerDatabase, private val repository: CollectionRepository) : ViewModel() {
    val collections: StateFlow<List<CollectionTotal>> = database.collectionDao().observeAllWithTransactions()
        .map { list -> list.map { CollectionTotal(it.collection, Collections.total(it.transactions)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Deletes only the collection; its transactions stay. */
    fun delete(collection: TransactionCollection) {
        viewModelScope.launch { repository.delete(collection) }
    }
}

/** Collections with their totals. Swipe-to-delete is Android-only: iOS has no way to delete a collection yet. */
@Composable
fun CollectionsScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val viewModel = appViewModel { CollectionsViewModel(it.database, it.collections) }
    val collections by viewModel.collections.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(R.string.collections), onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_collection)) }
            }
        },
    ) { padding ->
        if (collections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_collections), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(collections, key = { it.collection.id }) { (collection, total) ->
                SwipeToDelete(onDelete = { viewModel.delete(collection) }) {
                    ListRow(
                        title = collection.name,
                        leading = { CategoryIcon(iconFor(collection.systemImage), collection.color.toColor()) },
                        trailing = formatMoney(total),
                        onClick = { onOpen(collection.id) },
                    )
                }
            }
        }
    }
}
