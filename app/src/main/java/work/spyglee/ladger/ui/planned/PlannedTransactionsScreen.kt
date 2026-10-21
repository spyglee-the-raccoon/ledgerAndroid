package work.spyglee.ladger.ui.planned

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.PlannedTransactionRepository
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.Recurrence
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.formatDate
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.IncomeColor
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

data class PlannedTransactionsState(
    val planned: List<PlannedTransaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
)

class PlannedTransactionsViewModel(database: LadgerDatabase, private val repository: PlannedTransactionRepository) : ViewModel() {
    val state: StateFlow<PlannedTransactionsState> = combine(
        repository.observeAll(),
        database.categoryDao().observeAll(),
    ) { planned, categories ->
        PlannedTransactionsState(planned, categories.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannedTransactionsState())

    fun delete(planned: PlannedTransaction) {
        viewModelScope.launch { repository.delete(planned.id) }
    }

    fun togglePause(planned: PlannedTransaction) {
        viewModelScope.launch { repository.setPaused(planned.id, !planned.isPaused) }
    }
}

/** Planned transactions by start date. Swipe to the start to delete, to the end to pause or resume, as on iOS. */
@Composable
fun PlannedTransactionsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel = appViewModel { PlannedTransactionsViewModel(it.database, it.plannedTransactions) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LadgerTopBar(stringResource(R.string.planned_transactions), onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_planned_transaction)) }
            }
        },
    ) { padding ->
        if (state.planned.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_planned_transactions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.planned, key = { it.id }) { planned ->
                PauseOrDeleteSwipe(
                    isPaused = planned.isPaused,
                    onTogglePause = { viewModel.togglePause(planned) },
                    onDelete = { viewModel.delete(planned) },
                ) {
                    PlannedTransactionRow(planned, planned.categoryId?.let(state.categories::get), onClick = { onEdit(planned.id) })
                }
            }
        }
    }
}

@Composable
private fun PauseOrDeleteSwipe(isPaused: Boolean, onTogglePause: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val currentOnTogglePause by rememberUpdatedState(onTogglePause)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> currentOnDelete()
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnTogglePause()
                    scope.launch { state.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        when {
                            toDelete -> MaterialTheme.colorScheme.errorContainer
                            isPaused -> CategoryColor.GREEN.toColor()
                            else -> CategoryColor.ORANGE.toColor()
                        }
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toDelete) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when {
                    toDelete -> Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                    isPaused -> Icon(Icons.Outlined.PlayArrow, stringResource(R.string.resume), tint = MaterialTheme.colorScheme.surface)
                    else -> Icon(Icons.Outlined.Pause, stringResource(R.string.pause), tint = MaterialTheme.colorScheme.surface)
                }
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

/** Badge, title, schedule with the next date (or "Paused"/"Ended") and amount; faded while paused. */
@Composable
private fun PlannedTransactionRow(planned: PlannedTransaction, category: Category?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (planned.isPaused) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            planned.kind == TransactionKind.TRANSFER -> CategoryIcon(Icons.Outlined.SyncAlt, CategoryColor.GRAY.toColor())
            category != null -> CategoryIcon(iconFor(category.systemImage), category.color.toColor())
            else -> CategoryIcon(Icons.Outlined.QuestionMark, CategoryColor.GRAY.toColor())
        }
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    planned.note.isNotEmpty() -> planned.note
                    planned.kind == TransactionKind.TRANSFER -> stringResource(R.string.transfer)
                    category != null -> category.name
                    else -> stringResource(R.string.uncategorized)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val upcoming = Recurrence.nextDate(planned, ZoneId.systemDefault())
            val status = when {
                planned.isPaused -> stringResource(R.string.paused)
                upcoming != null -> formatDate(upcoming)
                else -> stringResource(R.string.ended)
            }
            Text(
                "${scheduleDescription(planned.frequency, planned.interval)} · $status",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatMoney(planned.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = when (planned.kind) {
                TransactionKind.EXPENSE -> ExpenseColor
                TransactionKind.INCOME -> IncomeColor
                TransactionKind.TRANSFER -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
fun frequencyLabel(frequency: RecurrenceFrequency): String = stringResource(
    when (frequency) {
        RecurrenceFrequency.DAILY -> R.string.daily
        RecurrenceFrequency.WEEKLY -> R.string.weekly
        RecurrenceFrequency.MONTHLY -> R.string.monthly
        RecurrenceFrequency.YEARLY -> R.string.yearly
    }
)

/** "Monthly", or "Every 3 months" for longer intervals, as iOS's `scheduleDescription`. */
@Composable
fun scheduleDescription(frequency: RecurrenceFrequency, interval: Int): String {
    if (interval <= 1) return frequencyLabel(frequency)
    val plural = when (frequency) {
        RecurrenceFrequency.DAILY -> R.plurals.every_n_days
        RecurrenceFrequency.WEEKLY -> R.plurals.every_n_weeks
        RecurrenceFrequency.MONTHLY -> R.plurals.every_n_months
        RecurrenceFrequency.YEARLY -> R.plurals.every_n_years
    }
    return pluralStringResource(plural, interval, interval)
}
