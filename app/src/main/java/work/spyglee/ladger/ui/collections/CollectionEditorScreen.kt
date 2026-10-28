package work.spyglee.ladger.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CollectionRepository
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.TransactionCollection
import work.spyglee.ladger.domain.DayGrouping
import work.spyglee.ladger.domain.DaySection
import work.spyglee.ladger.domain.byTransaction
import work.spyglee.ladger.domain.matches
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.AppearanceForm
import work.spyglee.ladger.ui.components.DuplicateNameDialog
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.SearchField
import work.spyglee.ladger.ui.components.SectionHeader
import work.spyglee.ladger.ui.components.TransactionRow
import work.spyglee.ladger.ui.dayTitle

data class TransactionChoices(val sections: List<DaySection> = emptyList(), val categories: Map<Long, Category> = emptyMap())

class CollectionEditorViewModel(
    private val database: LadgerDatabase,
    private val repository: CollectionRepository,
    private val collectionId: Long?,
) : ViewModel() {
    private val _draft = MutableStateFlow(TransactionCollection(name = ""))
    val draft: StateFlow<TransactionCollection> = _draft.asStateFlow()
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()
    val query = MutableStateFlow("")
    val isEditing = collectionId != null

    /** Every transaction, newest first, filtered by the search query. */
    val choices: StateFlow<TransactionChoices> = combine(
        database.transactionDao().observeAll(),
        database.categoryDao().observeAll(),
        database.receiptItemDao().observeNames(),
        query,
    ) { transactions, categories, itemNames, query ->
        val byId = categories.associateBy { it.id }
        TransactionChoices(DayGrouping.groupByDay(transactions.filter { it.matches(query, byId, itemNames.byTransaction()) }, ZoneId.systemDefault()), byId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionChoices())

    init {
        if (collectionId != null) {
            viewModelScope.launch {
                val dao = database.collectionDao()
                dao.getAll().firstOrNull { it.id == collectionId }?.let { _draft.value = it }
                _selected.value = dao.getTransactionIds(collectionId).toSet()
            }
        }
    }

    fun update(transform: (TransactionCollection) -> TransactionCollection) = _draft.update(transform)

    fun toggle(transactionId: Long) = _selected.update { if (transactionId in it) it - transactionId else it + transactionId }

    /** Saves unless another collection already has this exact name, as on iOS. */
    fun save(onSaved: () -> Unit, onDuplicate: () -> Unit) {
        val draft = _draft.value.copy(name = _draft.value.name.trim())
        if (draft.name.isEmpty()) return
        viewModelScope.launch {
            if (database.collectionDao().getAll().any { it.id != collectionId && it.name == draft.name }) {
                onDuplicate()
                return@launch
            }
            repository.save(draft, _selected.value)
            onSaved()
        }
    }
}

@Composable
fun CollectionEditorScreen(collectionId: Long?, onDone: () -> Unit) {
    val viewModel = appViewModel { CollectionEditorViewModel(it.database, it.collections, collectionId) }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var iconQuery by rememberSaveable { mutableStateOf("") }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(if (viewModel.isEditing) R.string.edit_collection else R.string.add_collection), onDone) {
                IconButton(onClick = { viewModel.save(onDone) { showDuplicate = true } }, enabled = draft.name.isNotBlank()) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding()) {
            item(key = "appearance") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppearanceForm(
                        nameLabel = stringResource(R.string.collection_name),
                        name = draft.name,
                        onNameChange = { name -> viewModel.update { it.copy(name = name) } },
                        systemImage = draft.systemImage,
                        onIconChange = { icon -> viewModel.update { it.copy(systemImage = icon) } },
                        color = draft.color,
                        onColorChange = { color -> viewModel.update { it.copy(color = color) } },
                        iconQuery = iconQuery,
                        onIconQueryChange = { iconQuery = it },
                    )
                }
            }
            item(key = "transactions-header") { SectionHeader(stringResource(R.string.transactions)) }
            item(key = "search") {
                SearchField(query, { viewModel.query.value = it }, stringResource(R.string.search_transactions), Modifier.padding(horizontal = 16.dp))
            }
            choices.sections.forEach { section ->
                item(key = "day-${section.day}") { SectionHeader(dayTitle(section.day), Modifier.padding(top = 8.dp)) }
                items(section.items, key = { "transaction-${it.id}" }) { transaction ->
                    val isSelected = transaction.id in selected
                    TransactionRow(
                        transaction = transaction,
                        category = transaction.categoryId?.let(choices.categories::get),
                        onClick = { viewModel.toggle(transaction.id) },
                        leading = {
                            Icon(
                                if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = if (isSelected) stringResource(R.string.selected) else null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
    if (showDuplicate) {
        DuplicateNameDialog(stringResource(R.string.collection_exists)) { showDuplicate = false }
    }
}
