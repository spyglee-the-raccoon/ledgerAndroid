package work.spyglee.ladger.widget

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.SettingsRepository
import work.spyglee.ladger.domain.MonthRange
import work.spyglee.ladger.domain.WidgetSnapshot

/**
 * What a widget shows: the [snapshot], and whether amounts and notes are hidden because the app lock
 * is on (decision D5).
 */
data class WidgetState(val snapshot: WidgetSnapshot, val hideAmounts: Boolean)

/** Builds what the widgets show from the database, and tells when it may have changed. */
class WidgetData(
    private val database: LadgerDatabase,
    private val settings: SettingsRepository,
    private val uncategorizedName: () -> String,
) {
    suspend fun load(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): WidgetState {
        val month = MonthRange.containing(now, zone)
        val snapshot = WidgetSnapshot.make(
            now = now,
            zone = zone,
            monthTransactions = database.transactionDao().getBetween(month.start, month.end),
            recentTransactions = database.transactionDao().getRecent(WidgetSnapshot.RECENT_COUNT),
            budgets = database.budgetDao().getAll(),
            categories = database.categoryDao().getAll(),
            uncategorizedName = uncategorizedName(),
        )
        return WidgetState(snapshot, hideAmounts = settings.isAppLockOn.first())
    }

    /** Emits when anything the widgets show may have changed, starting with the current state. */
    fun changes(): Flow<Unit> =
        combine(database.invalidationTracker.createFlow(*TABLES), settings.isAppLockOn) { _, _ -> }

    /** The current state, and a new one after every change. */
    fun observe(): Flow<WidgetState> = changes().map { load() }

    private companion object {
        /** The tables behind the widgets; planned transactions show up once recorded as transactions. */
        val TABLES = arrayOf("transactions", "budgets", "categories")
    }
}
