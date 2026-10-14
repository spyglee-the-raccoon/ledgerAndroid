package work.spyglee.ladger.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.Balances
import work.spyglee.ladger.domain.CategorySlice
import work.spyglee.ladger.domain.MonthBrowser
import work.spyglee.ladger.domain.MonthRange
import work.spyglee.ladger.domain.Statistics

data class MainUiState(
    val today: LocalDate = LocalDate.now(),
    /** The month shown in the donut and the expense and income buttons; this month unless the user swiped. */
    val month: YearMonth = YearMonth.from(today),
    val isCurrentMonth: Boolean = true,
    val canShowPreviousMonth: Boolean = false,
    val canShowNextMonth: Boolean = false,
    /** The shown month's expenses by category, for the donut chart. */
    val slices: List<CategorySlice> = emptyList(),
    /** The shown month's incomes minus expenses, shown in the middle of the donut. */
    val monthBalance: BigDecimal = BigDecimal.ZERO,
    val monthExpenses: BigDecimal = BigDecimal.ZERO,
    val monthIncomes: BigDecimal = BigDecimal.ZERO,
    val totalBalance: BigDecimal = BigDecimal.ZERO,
)

class MainViewModel(database: LadgerDatabase) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val today = MutableStateFlow(LocalDate.now(zone))
    private val browser = MutableStateFlow(MonthBrowser.at(YearMonth.from(today.value)))
    private val transactionDao = database.transactionDao()

    /** The oldest transaction's date, from the latest state. Swipes check it against the browser itself, so quick swipes can't overshoot. */
    @Volatile
    private var earliest: Instant? = null

    /** The browser together with its month's transactions, so the two always match. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthTransactions = browser.flatMapLatest { browser ->
        val month = MonthRange(browser.shown, zone)
        transactionDao.observeBetween(month.start, month.end).map { browser to it }
    }

    val state: StateFlow<MainUiState> = combine(
        today,
        monthTransactions,
        transactionDao.observeAll(),
        database.sourceDao().observeAll(),
        database.categoryDao().observeAll(),
    ) { day, (browser, month), all, sources, categories ->
        earliest = all.minOfOrNull { it.date }
        MainUiState(
            today = day,
            month = browser.shown,
            isCurrentMonth = browser.isCurrent,
            canShowPreviousMonth = browser.canShowPrevious(earliest, zone),
            canShowNextMonth = browser.canShowNext,
            slices = Statistics.expensesByCategory(month, categories),
            monthBalance = Balances.net(month),
            monthExpenses = Balances.sum(month, TransactionKind.EXPENSE),
            monthIncomes = Balances.sum(month, TransactionKind.INCOME),
            totalBalance = Balances.total(sources, all),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun showPreviousMonth() = browser.update { if (it.canShowPrevious(earliest, zone)) it.previous() else it }

    fun showNextMonth() = browser.update { it.next() }

    /** Called on resume and when the date changes, so the month rolls over at midnight. */
    fun refreshDate() {
        today.value = LocalDate.now(zone)
        browser.update { it.withCurrent(YearMonth.from(today.value)) }
    }
}
