package work.spyglee.ladger.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.spyglee.ladger.domain.PlannedTransactionDraft
import work.spyglee.ladger.domain.Recurrence

/** Wakes the app when the next planned transaction falls due. */
interface PlannedTransactionAlarm {
    /** Replaces any pending alarm; a time in the past goes off right away. */
    fun schedule(at: Instant)
    fun cancel()
}

/**
 * Saves planned transactions and records their occurrences as transactions once due, as iOS's
 * `RecurrenceEngine`. After every change the alarm is set for the earliest occurrence still to record.
 * [onRecorded] runs after occurrences were recorded, to check the budgets as iOS does.
 */
class PlannedTransactionRepository(
    private val database: LadgerDatabase,
    private val alarm: PlannedTransactionAlarm,
    private val onRecorded: suspend () -> Unit = {},
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val dao = database.plannedTransactionDao()

    /** Opening the app and the alarm can both record at once; this keeps them from recording an occurrence twice. */
    private val mutex = Mutex()

    fun observeAll(): Flow<List<PlannedTransaction>> = dao.observeAll()

    suspend fun get(id: Long): PlannedTransaction? = dao.getById(id)

    /** Records every occurrence due by [now]; returns the plans that recorded any. */
    suspend fun recordDue(now: Instant = Instant.now()): List<PlannedTransaction> =
        mutex.withLock { recordDueLocked(now) }.also { if (it.isNotEmpty()) onRecorded() }

    /**
     * Inserts or updates the plan from [draft] (only call when it is valid), then records what is
     * due, so a plan starting today is recorded straight away, as on iOS.
     */
    suspend fun save(id: Long?, draft: PlannedTransactionDraft) {
        val recorded = mutex.withLock {
            if (id == null) {
                dao.insert(draft.toPlanned(existing = null))
            } else {
                // Read again rather than trusting the editor's copy: the alarm may have recorded one meanwhile.
                val existing = dao.getById(id) ?: return
                dao.update(draft.toPlanned(existing))
            }
            recordDueLocked(Instant.now())
        }
        if (recorded.isNotEmpty()) onRecorded()
    }

    /** Transactions the plan recorded stay, unlinked. */
    suspend fun delete(id: Long) {
        mutex.withLock {
            dao.getById(id)?.let { dao.delete(it) }
            rescheduleLocked()
        }
    }

    /** Resuming records the occurrences missed while paused, as on iOS. */
    suspend fun setPaused(id: Long, paused: Boolean) {
        val recorded = mutex.withLock {
            val planned = dao.getById(id) ?: return
            dao.update(planned.copy(isPaused = paused))
            if (paused) {
                rescheduleLocked()
                emptyList()
            } else {
                recordDueLocked(Instant.now())
            }
        }
        if (recorded.isNotEmpty()) onRecorded()
    }

    /** Sets the alarm again, e.g. after a reboot or force stop cleared it. */
    suspend fun reschedule() {
        mutex.withLock { rescheduleLocked() }
    }

    private suspend fun recordDueLocked(now: Instant): List<PlannedTransaction> {
        val zone = zone()
        val recorded = database.withTransaction {
            dao.getActive().mapNotNull { planned ->
                val dates = Recurrence.dueDates(planned, now, zone)
                if (dates.isEmpty()) return@mapNotNull null
                dates.forEach { database.transactionDao().insert(Recurrence.transaction(planned, it)) }
                planned.copy(occurrencesGenerated = planned.occurrencesGenerated + dates.size).also { dao.update(it) }
            }
        }
        rescheduleLocked()
        return recorded
    }

    private suspend fun rescheduleLocked() {
        val zone = zone()
        val next = dao.getActive().mapNotNull { Recurrence.nextDate(it, zone) }.minOrNull()
        if (next == null) alarm.cancel() else alarm.schedule(next)
    }
}
