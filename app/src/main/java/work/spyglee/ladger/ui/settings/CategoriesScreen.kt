package work.spyglee.ladger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.AppearanceForm
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.DuplicateNameDialog
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.ListRow
import work.spyglee.ladger.ui.components.SectionHeader
import work.spyglee.ladger.ui.components.SegmentedSelector
import work.spyglee.ladger.ui.components.SwipeToDelete
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

class CategoriesViewModel(private val database: LadgerDatabase) : ViewModel() {
    val categories: StateFlow<List<Category>> =
        database.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Transactions keep their data and become uncategorized. */
    fun delete(category: Category) {
        viewModelScope.launch { database.categoryDao().delete(category) }
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel = appViewModel { CategoriesViewModel(it.database) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(R.string.categories), onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_category)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            for ((kind, title) in listOf(CategoryKind.EXPENSE to R.string.expense_categories, CategoryKind.INCOME to R.string.income_categories)) {
                item(key = "header-${kind.raw}") { SectionHeader(stringResource(title)) }
                items(categories.filter { it.kind == kind }, key = { it.id }) { category ->
                    SwipeToDelete(onDelete = { viewModel.delete(category) }) {
                        ListRow(
                            title = category.name,
                            leading = { CategoryIcon(iconFor(category.systemImage), category.color.toColor()) },
                            onClick = { onEdit(category.id) },
                        )
                    }
                }
            }
        }
    }
}

class CategoryEditorViewModel(private val database: LadgerDatabase, private val categoryId: Long?) : ViewModel() {
    private val _draft = MutableStateFlow(Category(name = ""))
    val draft: StateFlow<Category> = _draft.asStateFlow()
    val isEditing = categoryId != null

    init {
        if (categoryId != null) {
            viewModelScope.launch {
                database.categoryDao().getAll().firstOrNull { it.id == categoryId }?.let { _draft.value = it }
            }
        }
    }

    fun update(transform: (Category) -> Category) = _draft.update(transform)

    /** Saves unless another category already has this exact name (across both kinds, as on iOS). */
    fun save(onSaved: () -> Unit, onDuplicate: () -> Unit) {
        val draft = _draft.value.copy(name = _draft.value.name.trim())
        if (draft.name.isEmpty()) return
        viewModelScope.launch {
            val dao = database.categoryDao()
            if (dao.getAll().any { it.id != categoryId && it.name == draft.name }) {
                onDuplicate()
                return@launch
            }
            if (categoryId != null) dao.update(draft) else dao.insert(draft.copy(id = 0))
            onSaved()
        }
    }
}

@Composable
fun CategoryEditorScreen(categoryId: Long?, onDone: () -> Unit) {
    val viewModel = appViewModel { CategoryEditorViewModel(it.database, categoryId) }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var iconQuery by rememberSaveable { mutableStateOf("") }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(if (viewModel.isEditing) R.string.edit_category else R.string.new_category), onDone) {
                IconButton(onClick = { viewModel.save(onDone) { showDuplicate = true } }, enabled = draft.name.isNotBlank()) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedSelector(
                options = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
                selected = draft.kind,
                label = { stringResource(if (it == CategoryKind.EXPENSE) R.string.expense else R.string.income) },
                onSelect = { kind -> viewModel.update { it.copy(kind = kind) } },
            )
            AppearanceForm(
                nameLabel = stringResource(R.string.category_name),
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
    if (showDuplicate) {
        DuplicateNameDialog(stringResource(R.string.category_exists)) { showDuplicate = false }
    }
}
