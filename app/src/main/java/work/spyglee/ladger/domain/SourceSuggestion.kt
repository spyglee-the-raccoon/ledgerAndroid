package work.spyglee.ladger.domain

import work.spyglee.ladger.data.Transaction
import work.spyglee.ladger.data.TransactionKind

/** Picks the source the user most likely wants for a new transaction, based on recent history. */
object SourceSuggestion {
    enum class Role { SOURCE, DESTINATION }

    /** How many recent transactions to load before filtering by kind. */
    const val FETCH_LIMIT = 200
    private const val HISTORY_SIZE = 30

    /**
     * The most frequently used source for [role] among the last 30 transactions of [kind].
     * Ties go to the source used most recently.
     *
     * @param recent transactions sorted newest first, e.g. the last [FETCH_LIMIT].
     */
    fun mostLikely(
        role: Role,
        kind: TransactionKind,
        recent: List<Transaction>,
        excludingId: Long? = null,
    ): Long? {
        val used = recent
            .asSequence()
            .filter { it.kind == kind }
            .mapNotNull { if (role == Role.SOURCE) it.sourceId else it.destinationId }
            .filter { it != excludingId }
            .take(HISTORY_SIZE)
            .toList()

        val counts = used.groupingBy { it }.eachCount()
        return counts.keys.maxWithOrNull(
            compareBy<Long> { counts.getValue(it) }.thenByDescending { used.indexOf(it) }
        )
    }
}
