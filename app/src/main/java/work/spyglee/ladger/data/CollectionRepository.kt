package work.spyglee.ladger.data

import androidx.room.withTransaction

class CollectionRepository(private val database: LadgerDatabase) {
    private val dao = database.collectionDao()

    /** Inserts or updates [collection] together with its transactions; returns the collection id. */
    suspend fun save(collection: TransactionCollection, transactionIds: Collection<Long>): Long =
        database.withTransaction {
            val id = if (collection.id == 0L) dao.insert(collection) else collection.id.also { dao.update(collection) }
            dao.replaceTransactions(id, transactionIds)
            id
        }

    suspend fun addTransaction(collectionId: Long, transactionId: Long) =
        dao.addTransaction(CollectionTransaction(collectionId, transactionId))

    suspend fun removeTransactions(collectionId: Long, transactionIds: List<Long>) =
        dao.removeTransactions(collectionId, transactionIds)

    suspend fun delete(collection: TransactionCollection) = dao.delete(collection)
}
