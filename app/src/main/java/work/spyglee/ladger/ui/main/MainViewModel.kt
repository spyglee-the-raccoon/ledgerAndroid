package work.spyglee.ladger.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.Balances
import work.spyglee.ladger.domain.CategorySlice
import work.spyglee.ladger.domain.MonthRange
import work.spyglee.ladger.domain.Statistics

data class MainUiState(
    val today: LocalDate = LocalDate.now(),
    /** This month's expenses by category, for the donut chart. */
    val slices: List<CategorySlice> = emptyList(),
    /** This month's incomes minus expenses, shown in the middle of the donut. */
    val monthBalance: BigDecimal = BigDecimal.ZERO,
    val monthExpenses: BigDecimal = BigDecimal.ZERO,
    val monthIncomes: BigDecimal = BigDecimal.ZERO,
    val totalBalance: BigDecimal = BigDecimal.ZERO,
)

class MainViewModel(database: LadgerDatabase) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val today = MutableStateFlow(LocalDate.now(zone))
    private val transactionDao = database.transactionDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthTransactions = today.flatMapLatest { day ->
        val month = MonthRange.containing(day.atStartOfDay(zone).toInstant(), zone)
        transactionDao.observeBetween(month.start, month.end)
    }

    val state: StateFlow<MainUiState> = combine(
        today,
        monthTransactions,
        transactionDao.observeAll(),
        database.sourceDao().observeAll(),
        database.categoryDao().observeAll(),
    ) { day, month, all, sources, categories ->
        MainUiState(
            today = day,
            slices = Statistics.expensesByCategory(month, categories),
            monthBalance = Balances.net(month),
            monthExpenses = Balances.sum(month, TransactionKind.EXPENSE),
            monthIncomes = Balances.sum(month, TransactionKind.INCOME),
            totalBalance = Balances.total(sources, all),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    /** Called on resume and when the date changes, so the month rolls over at midnight. */
    fun refreshDate() {
        today.value = LocalDate.now(zone)
    }
}
