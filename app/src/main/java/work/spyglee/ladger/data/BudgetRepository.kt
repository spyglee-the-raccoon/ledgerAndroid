package work.spyglee.ladger.data

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.spyglee.ladger.domain.BudgetUsage
import work.spyglee.ladger.domain.Budgets
import work.spyglee.ladger.domain.MonthRange

/** Tells the user a budget reached its limit. */
fun interface BudgetAlerts {
    fun showReached(usage: BudgetUsage, category: Category?)
}

/**
 * Saves budgets and notifies once a month when one reaches its limit, as iOS's `BudgetNotifier`.
 * [check] runs after anything that changes this month's spending.
 */
class BudgetRepository(
    private val database: LadgerDatabase,
    private val alerts: BudgetAlerts,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val dao = database.budgetDao()

    /** Saving an expense and the planned transactions alarm can both check at once; this keeps an alert from showing twice. */
    private val mutex = Mutex()

    fun observeAll(): Flow<List<Budget>> = dao.observeAll()

    suspend fun get(id: Long): Budget? = dao.getById(id)

    /** The general limit always exists, so it can be switched on and off; it starts off, as on iOS. */
    suspend fun ensureGeneral() {
        mutex.withLock {
            if (dao.getAll().none { it.isGeneral }) dao.insert(Budget(isActive = false))
        }
    }

    /** Inserts a budget or updates budget [id]; then checks, so a limit that is already reached alerts. */
    suspend fun save(id: Long?, limit: BigDecimal, isActive: Boolean, categoryId: Long?) {
        mutex.withLock {
            if (id == null) {
                dao.insert(Budget(monthlyLimit = limit, isActive = isActive, categoryId = categoryId))
            } else {
                val existing = dao.getById(id) ?: return
                dao.update(existing.copy(monthlyLimit = limit, isActive = isActive, categoryId = if (existing.isGeneral) null else categoryId))
            }
            checkLocked(Instant.now())
        }
    }

    suspend fun setActive(id: Long, isActive: Boolean) {
        mutex.withLock { dao.getById(id)?.let { dao.update(it.copy(isActive = isActive)) } }
    }

    /** Only category budgets can be deleted; the general limit is switched off instead. */
    suspend fun delete(id: Long) {
        mutex.withLock { dao.getById(id)?.takeUnless { it.isGeneral }?.let { dao.delete(it) } }
    }

    /** Notifies about budgets that reached their limit this month and weren't notified about yet. */
    suspend fun check(now: Instant = Instant.now()) {
        mutex.withLock { checkLocked(now) }
    }

    /** This month's spending against the general limit, if it is on; for the daily reminder. */
    suspend fun generalUsage(now: Instant = Instant.now()): BudgetUsage? = Budgets.generalUsage(usages(now).first)

    private suspend fun checkLocked(now: Instant) {
        val (usages, month) = usages(now)
        val result = Budgets.check(usages, month, now)
        result.updated.forEach { dao.update(it) }
        if (result.alerts.isEmpty()) return
        val categories = database.categoryDao().getAll().associateBy { it.id }
        result.alerts.forEach { alerts.showReached(it, it.budget.categoryId?.let(categories::get)) }
    }

    private suspend fun usages(now: Instant): Pair<List<BudgetUsage>, MonthRange> {
        val month = MonthRange.containing(now, zone())
        val transactions = database.transactionDao().getBetween(month.start, month.end)
        return Budgets.usages(dao.getAll(), transactions, month) to month
    }
}
