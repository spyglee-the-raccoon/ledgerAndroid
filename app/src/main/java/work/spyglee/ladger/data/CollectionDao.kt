package work.spyglee.ladger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction as RoomTransaction

data class CollectionWithTransactions(
    @Embedded val collection: TransactionCollection,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(CollectionTransaction::class, parentColumn = "collectionId", entityColumn = "transactionId"),
    )
    val transactions: List<Transaction>,
)

@Dao
interface CollectionDao {
    @RoomTransaction
    @Query("SELECT * FROM collections ORDER BY id")
    fun observeAllWithTransactions(): Flow<List<CollectionWithTransactions>>

    @RoomTransaction
    @Query("SELECT * FROM collections WHERE id = :id")
    fun observeWithTransactions(id: Long): Flow<CollectionWithTransactions?>

    @Query("SELECT * FROM collections ORDER BY id")
    fun observeAll(): Flow<List<TransactionCollection>>

    @Query("SELECT * FROM collections ORDER BY id")
    suspend fun getAll(): List<TransactionCollection>

    @Query("SELECT transactionId FROM collection_transactions WHERE collectionId = :collectionId")
    suspend fun getTransactionIds(collectionId: Long): List<Long>

    @Insert suspend fun insert(collection: TransactionCollection): Long
    @Update suspend fun update(collection: TransactionCollection)
    @Delete suspend fun delete(collection: TransactionCollection)

    /** Adding a transaction that is already in the collection does nothing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTransaction(membership: CollectionTransaction)

    @Query("DELETE FROM collection_transactions WHERE collectionId = :collectionId AND transactionId IN (:transactionIds)")
    suspend fun removeTransactions(collectionId: Long, transactionIds: List<Long>)

    @Query("DELETE FROM collection_transactions WHERE collectionId = :collectionId")
    suspend fun clearTransactions(collectionId: Long)

    /** Makes [transactionIds] the collection's exact contents, as saving the collection editor does. */
    @RoomTransaction
    suspend fun replaceTransactions(collectionId: Long, transactionIds: Collection<Long>) {
        clearTransactions(collectionId)
        transactionIds.forEach { addTransaction(CollectionTransaction(collectionId, it)) }
    }
}
