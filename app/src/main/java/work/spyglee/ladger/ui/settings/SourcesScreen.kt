package work.spyglee.ladger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.domain.AmountInput
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.AppearanceForm
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.DuplicateNameDialog
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.ListRow
import work.spyglee.ladger.ui.components.SwipeToDelete
import work.spyglee.ladger.ui.localDecimalSeparator
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

class SourcesViewModel(private val database: LadgerDatabase) : ViewModel() {
    val sources: StateFlow<List<Source>> =
        database.sourceDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Transactions keep their data and lose this source. */
    fun delete(source: Source) {
        viewModelScope.launch { database.sourceDao().delete(source) }
    }
}

@Composable
fun SourcesScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel = appViewModel { SourcesViewModel(it.database) }
    val sources by viewModel.sources.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(R.string.sources), onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_source)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(sources, key = { it.id }) { source ->
                SwipeToDelete(onDelete = { viewModel.delete(source) }) {
                    ListRow(
                        title = source.name,
                        leading = { CategoryIcon(iconFor(source.systemImage), source.color.toColor()) },
                        onClick = { onEdit(source.id) },
                    )
                }
            }
        }
    }
}

class SourceEditorViewModel(private val database: LadgerDatabase, private val sourceId: Long?, private val decimalSeparator: Char) : ViewModel() {
    private val _draft = MutableStateFlow(Source(name = ""))
    val draft: StateFlow<Source> = _draft.asStateFlow()

    /** The starting value as typed; empty means zero. */
    private val _initValueText = MutableStateFlow("")
    val initValueText: StateFlow<String> = _initValueText.asStateFlow()
    val isEditing = sourceId != null

    init {
        if (sourceId != null) {
            viewModelScope.launch {
                database.sourceDao().getAll().firstOrNull { it.id == sourceId }?.let {
                    _draft.value = it
                    // Zero shows as the "0" placeholder, so typing a value doesn't append to it.
                    _initValueText.value = if (it.initValue.signum() == 0) "" else AmountInput.format(it.initValue, decimalSeparator)
                }
            }
        }
    }

    fun update(transform: (Source) -> Source) = _draft.update(transform)

    fun setInitValueText(text: String) {
        _initValueText.value = text
    }

    /** Flips the starting value's sign, e.g. for a credit card that starts in debt, as on iOS. */
    fun toggleSign() {
        val text = _initValueText.value.trim()
        _initValueText.value = if (text.startsWith("-")) text.removePrefix("-") else "-$text"
    }

    private fun parsedInitValue(): BigDecimal? =
        if (_initValueText.value.isBlank()) BigDecimal.ZERO else AmountInput.parse(_initValueText.value, allowNegative = true)

    fun canSave(name: String, initValueText: String): Boolean =
        name.isNotBlank() && (initValueText.isBlank() || AmountInput.parse(initValueText, allowNegative = true) != null)

    /** Saves unless another source already has this exact name, as on iOS. */
    fun save(onSaved: () -> Unit, onDuplicate: () -> Unit) {
        val initValue = parsedInitValue() ?: return
        val draft = _draft.value.copy(name = _draft.value.name.trim(), initValue = initValue)
        if (draft.name.isEmpty()) return
        viewModelScope.launch {
            val dao = database.sourceDao()
            if (dao.getAll().any { it.id != sourceId && it.name == draft.name }) {
                onDuplicate()
                return@launch
            }
            if (sourceId != null) dao.update(draft) else dao.insert(draft.copy(id = 0))
            onSaved()
        }
    }
}

@Composable
fun SourceEditorScreen(sourceId: Long?, onDone: () -> Unit) {
    val viewModel = appViewModel { SourceEditorViewModel(it.database, sourceId, localDecimalSeparator()) }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val initValueText by viewModel.initValueText.collectAsStateWithLifecycle()
    var iconQuery by rememberSaveable { mutableStateOf("") }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }
    val isNegative = initValueText.trim().startsWith("-")

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(if (viewModel.isEditing) R.string.edit_source else R.string.add_source), onDone) {
                IconButton(
                    onClick = { viewModel.save(onDone) { showDuplicate = true } },
                    enabled = viewModel.canSave(draft.name, initValueText),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceForm(
                nameLabel = stringResource(R.string.source_name),
                name = draft.name,
                onNameChange = { name -> viewModel.update { it.copy(name = name) } },
                systemImage = draft.systemImage,
                onIconChange = { icon -> viewModel.update { it.copy(systemImage = icon) } },
                color = draft.color,
                onColorChange = { color -> viewModel.update { it.copy(color = color) } },
                iconQuery = iconQuery,
                onIconQueryChange = { iconQuery = it },
            ) {
                OutlinedTextField(
                    value = initValueText,
                    onValueChange = viewModel::setInitValueText,
                    label = { Text(stringResource(R.string.initial_value)) },
                    placeholder = { Text("0") },
                    leadingIcon = {
                        IconButton(onClick = viewModel::toggleSign) {
                            Icon(
                                Icons.Outlined.Exposure,
                                contentDescription = stringResource(R.string.change_sign),
                                tint = if (isNegative) ExpenseColor else MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (showDuplicate) {
        DuplicateNameDialog(stringResource(R.string.source_exists)) { showDuplicate = false }
    }
}
