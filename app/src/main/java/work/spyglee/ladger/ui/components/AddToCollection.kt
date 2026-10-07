package work.spyglee.ladger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.ui.LocalAppContainer
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

/**
 * Remembers which transaction is being added to a collection and shows the collection picker for
 * it. After a pick the transaction is added and [onAdded] receives the collection id, so the caller
 * can open that collection, as iOS does.
 */
class AddToCollectionState {
    var transaction by mutableStateOf<Transaction?>(null)
        internal set

    fun start(transaction: Transaction) {
        this.transaction = transaction
    }
}

@Composable
fun rememberAddToCollection(onAdded: (collectionId: Long) -> Unit): AddToCollectionState {
    val state = remember { AddToCollectionState() }
    val transaction = state.transaction ?: return state
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    CollectionPickerSheet(
        onDismiss = { state.transaction = null },
        onPick = { collectionId ->
            state.transaction = null
            scope.launch {
                container.collections.addTransaction(collectionId, transaction.id)
                onAdded(collectionId)
            }
        },
    )
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPickerSheet(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val collectionsFlow = remember { container.database.collectionDao().observeAll() }
    val collections by collectionsFlow.collectAsStateWithLifecycle(emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.collections),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (collections.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_collections), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(collections, key = { it.id }) { collection ->
                    ListRow(
                        title = collection.name,
                        leading = { CategoryIcon(iconFor(collection.systemImage), collection.color.toColor()) },
                        onClick = { onPick(collection.id) },
                    )
                }
            }
        }
    }
}
