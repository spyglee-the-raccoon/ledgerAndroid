package work.spyglee.ladger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.domain.IncomeExpenseMonth
import work.spyglee.ladger.domain.SavingsMonth
import work.spyglee.ladger.domain.Statistics

class IncomeExpenseViewModel(database: LadgerDatabase) : ViewModel() {
    /** Null while loading. */
    val months: StateFlow<List<IncomeExpenseMonth>?> = database.transactionDao().observeAll()
        .map { Statistics.incomeExpenseByMonth(it, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class SavingsUiState(
    val months: List<SavingsMonth>,
    /** All transactions, for the biggest expenses and incomes of the selected month. */
    val transactions: List<Transaction>,
    val categories: Map<Long, Category>,
)

class SavingsViewModel(database: LadgerDatabase) : ViewModel() {
    /** Null while loading. */
    val state: StateFlow<SavingsUiState?> = combine(
        database.transactionDao().observeAll(),
        database.sourceDao().observeAll(),
        database.categoryDao().observeAll(),
    ) { transactions, sources, categories ->
        SavingsUiState(
            months = Statistics.savingsByMonth(transactions, sources.sumOf { it.initValue }, ZoneId.systemDefault()),
            transactions = transactions,
            categories = categories.associateBy { it.id },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
