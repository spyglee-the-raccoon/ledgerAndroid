package work.spyglee.ladger.ui.budgets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.domain.AmountInput
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.SwitchRow
import work.spyglee.ladger.ui.localDecimalSeparator
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor
import work.spyglee.ladger.ui.transactions.PickerField
import work.spyglee.ladger.ui.transactions.PickerOption

/** The budget being edited; [isGeneral] for the general limit, which has no category. */
data class BudgetDraft(
    val limit: BigDecimal? = null,
    val isActive: Boolean = true,
    val categoryId: Long? = null,
    val isGeneral: Boolean = false,
) {
    val isValid: Boolean get() = (limit?.signum() ?: 0) > 0 && (isGeneral || categoryId != null)
}

class BudgetEditorViewModel(
    container: AppContainer,
    private val budgetId: Long?,
    private val decimalSeparator: Char,
) : ViewModel() {
    private val repository = container.budgets

    val isEditing = budgetId != null

    private val _draft = MutableStateFlow(BudgetDraft())
    val draft: StateFlow<BudgetDraft> = _draft.asStateFlow()

    /** The limit as typed; the draft holds the parsed value. */
    private val _limitText = MutableStateFlow("")
    val limitText: StateFlow<String> = _limitText.asStateFlow()

    /** Expense categories that don't have a limit yet, plus the one being edited, as on iOS. */
    val categories: StateFlow<List<Category>> = combine(
        container.database.categoryDao().observeAll(),
        repository.observeAll(),
    ) { categories, budgets ->
        val taken = budgets.filter { it.id != budgetId }.mapNotNull { it.categoryId }.toSet()
        categories.filter { it.kind == CategoryKind.EXPENSE && it.id !in taken }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (budgetId != null) {
            viewModelScope.launch {
                repository.get(budgetId)?.let { budget ->
                    // The general limit starts at zero; an empty field is easier to type into than "0".
                    val limit = budget.monthlyLimit.takeIf { it.signum() > 0 }
                    _draft.value = BudgetDraft(limit, budget.isActive, budget.categoryId, budget.isGeneral)
                    _limitText.value = limit?.let { AmountInput.format(it, decimalSeparator) }.orEmpty()
                }
            }
        }
    }

    fun setLimitText(text: String) {
        _limitText.value = text
        _draft.update { it.copy(limit = AmountInput.parse(text)) }
    }

    fun setCategory(id: Long?) = _draft.update { it.copy(categoryId = id) }
    fun setActive(isActive: Boolean) = _draft.update { it.copy(isActive = isActive) }

    /** Saves, which also checks the budgets, then calls [onSaved]; does nothing while the draft is incomplete. */
    fun save(onSaved: () -> Unit) {
        val draft = _draft.value
        val limit = draft.limit
        if (!draft.isValid || limit == null) return
        viewModelScope.launch {
            repository.save(budgetId, limit, draft.isActive, draft.categoryId)
            onSaved()
        }
    }
}

/** Adds or edits a monthly limit, as iOS's `AddBudgetView`. */
@Composable
fun BudgetEditorScreen(budgetId: Long?, onDone: () -> Unit) {
    val decimalSeparator = localDecimalSeparator()
    val viewModel = appViewModel { BudgetEditorViewModel(it, budgetId, decimalSeparator) }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val limitText by viewModel.limitText.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    // As on iOS, saving asks to allow notifications (for the "limit reached" alert), and saves whatever the answer.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.save(onDone)
    }
    val save = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.save(onDone)
    }

    val title = when {
        draft.isGeneral -> R.string.general_budget
        viewModel.isEditing -> R.string.edit_budget
        else -> R.string.add_budget
    }
    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(title), onBack = onDone) {
                IconButton(onClick = save, enabled = draft.isValid) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!draft.isGeneral) {
                PickerField(
                    label = stringResource(R.string.category),
                    options = categories.map { PickerOption(it.id, it.name, iconFor(it.systemImage), it.color.toColor()) },
                    selectedId = draft.categoryId,
                    onSelect = viewModel::setCategory,
                    onClear = { viewModel.setCategory(null) },
                )
            }
            OutlinedTextField(
                value = limitText,
                onValueChange = viewModel::setLimitText,
                label = { Text(stringResource(R.string.monthly_limit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(stringResource(R.string.active), draft.isActive, viewModel::setActive)
        }
    }
}
