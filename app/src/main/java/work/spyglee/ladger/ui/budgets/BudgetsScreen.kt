package work.spyglee.ladger.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.text.Collator
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Budget
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.domain.BudgetLevel
import work.spyglee.ladger.domain.BudgetUsage
import work.spyglee.ladger.domain.Budgets
import work.spyglee.ladger.domain.MonthRange
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.SectionHeader
import work.spyglee.ladger.ui.components.ToggleOrDeleteSwipe
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.formatPercent
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

data class BudgetsState(
    val general: BudgetUsage? = null,
    /** Category budgets by category name. */
    val categoryBudgets: List<BudgetUsage> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
)

/** This month's spending against each budget, as iOS's `BudgetingView`. */
class BudgetsViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.budgets
    private val month = MonthRange.containing(Instant.now())

    val state: StateFlow<BudgetsState> = combine(
        repository.observeAll(),
        container.database.transactionDao().observeBetween(month.start, month.end),
        container.database.categoryDao().observeAll(),
    ) { budgets, transactions, categories ->
        val byId = categories.associateBy { it.id }
        val usages = Budgets.usages(budgets, transactions, month)
        val collator = Collator.getInstance()
        BudgetsState(
            general = usages.firstOrNull { it.budget.isGeneral },
            categoryBudgets = usages.filterNot { it.budget.isGeneral }
                .sortedWith(compareBy(collator) { usage -> usage.budget.categoryId?.let(byId::get)?.name.orEmpty() }),
            categories = byId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState())

    init {
        viewModelScope.launch { repository.ensureGeneral() }
    }

    fun setActive(budget: Budget, isActive: Boolean) {
        viewModelScope.launch { repository.setActive(budget.id, isActive) }
    }

    fun delete(budget: Budget) {
        viewModelScope.launch { repository.delete(budget.id) }
    }

    /** Leaving the screen checks the budgets, which picks up switches and deletions made here, as on iOS. */
    override fun onCleared() {
        container.applicationScope.launch { repository.check() }
    }
}

/**
 * The general monthly limit, then a limit per category. Tap to edit; swipe to the end to activate or
 * deactivate, to the start to delete a category limit (the general one is only switched off).
 */
@Composable
fun BudgetsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel = appViewModel { BudgetsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activate = stringResource(R.string.activate)
    val deactivate = stringResource(R.string.deactivate)

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(R.string.budgeting), onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_budget)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            state.general?.let { general ->
                item(key = "general") {
                    ToggleOrDeleteSwipe(
                        isOn = general.budget.isActive,
                        turnOnLabel = activate,
                        turnOffLabel = deactivate,
                        onToggle = { viewModel.setActive(general.budget, !general.budget.isActive) },
                        onDelete = null,
                    ) {
                        BudgetCard(general, category = null, onClick = { onEdit(general.budget.id) }, onActiveChange = { viewModel.setActive(general.budget, it) })
                    }
                    HorizontalDivider()
                }
            }
            item(key = "categories") { SectionHeader(stringResource(R.string.categories), Modifier.padding(top = 8.dp)) }
            items(state.categoryBudgets, key = { it.budget.id }) { usage ->
                ToggleOrDeleteSwipe(
                    isOn = usage.budget.isActive,
                    turnOnLabel = activate,
                    turnOffLabel = deactivate,
                    onToggle = { viewModel.setActive(usage.budget, !usage.budget.isActive) },
                    onDelete = { viewModel.delete(usage.budget) },
                ) {
                    BudgetCard(
                        usage,
                        category = usage.budget.categoryId?.let(state.categories::get),
                        onClick = { onEdit(usage.budget.id) },
                        onActiveChange = { viewModel.setActive(usage.budget, it) },
                    )
                }
            }
            if (state.categoryBudgets.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_category_budgets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Name, what is left or over, a switch, spent of the limit with the percentage, and a progress bar. Faded while off. */
@Composable
private fun BudgetCard(usage: BudgetUsage, category: Category?, onClick: () -> Unit, onActiveChange: (Boolean) -> Unit) {
    val budget = usage.budget
    val barColor = barColor(Budgets.level(usage))
    val title = category?.name ?: stringResource(R.string.general_budget)
    val activeLabel = stringResource(R.string.active)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (budget.isActive) 1f else 0.5f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            category?.let { CategoryIcon(iconFor(it.systemImage), it.color.toColor()) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (budget.monthlyLimit.signum() > 0) {
                    val remaining = usage.remaining
                    Text(
                        if (remaining.signum() >= 0) {
                            stringResource(R.string.budget_left, formatMoney(remaining))
                        } else {
                            stringResource(R.string.budget_over, formatMoney(remaining.negate()))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (remaining.signum() >= 0) MaterialTheme.colorScheme.onSurfaceVariant else CategoryColor.RED.toColor(),
                    )
                }
            }
            Switch(
                checked = budget.isActive,
                onCheckedChange = onActiveChange,
                modifier = Modifier.semantics { contentDescription = "$title, $activeLabel" },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatMoney(usage.spent), fontWeight = FontWeight.Bold)
            Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoney(budget.monthlyLimit), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatPercent(usage.ratio), fontWeight = FontWeight.Bold, color = barColor)
        }
        LinearProgressIndicator(
            progress = { usage.ratio.coerceIn(0.0, 1.0).toFloat() },
            color = barColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun barColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.INACTIVE -> CategoryColor.GRAY.toColor()
    BudgetLevel.FINE -> CategoryColor.GREEN.toColor()
    BudgetLevel.CLOSE -> CategoryColor.ORANGE.toColor()
    BudgetLevel.OVER -> CategoryColor.RED.toColor()
}
