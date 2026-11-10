package work.spyglee.ladger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert suspend fun insert(category: Category): Long
    @Insert suspend fun insertAll(categories: List<Category>)
    @Update suspend fun update(category: Category)
    @Delete suspend fun delete(category: Category)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY id")
    fun observeAll(): Flow<List<Source>>

    @Query("SELECT * FROM sources ORDER BY id")
    suspend fun getAll(): List<Source>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Insert suspend fun insert(source: Source): Long
    @Insert suspend fun insertAll(sources: List<Source>)
    @Update suspend fun update(source: Source)
    @Delete suspend fun delete(source: Source)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE kind = :kind ORDER BY date DESC, id DESC")
    fun observeByKind(kind: TransactionKind): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :start AND date < :end ORDER BY date DESC, id DESC")
    fun observeBetween(start: Instant, end: Instant): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :start AND date < :end ORDER BY date DESC, id DESC")
    suspend fun getBetween(start: Instant, end: Instant): List<Transaction>

    /** Money going out of or into [sourceId]. */
    @Query("SELECT * FROM transactions WHERE sourceId = :sourceId OR destinationId = :sourceId ORDER BY date DESC, id DESC")
    fun observeForSource(sourceId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY date ASC, id ASC")
    suspend fun getAllChronological(): List<Transaction>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Insert suspend fun insert(transaction: Transaction): Long
    @Update suspend fun update(transaction: Transaction)
    @Delete suspend fun delete(transaction: Transaction)
}

@Dao
interface PlannedTransactionDao {
    @Query("SELECT * FROM planned_transactions ORDER BY startDate, id")
    fun observeAll(): Flow<List<PlannedTransaction>>

    @Query("SELECT * FROM planned_transactions ORDER BY startDate, id")
    suspend fun getAll(): List<PlannedTransaction>

    @Query("SELECT * FROM planned_transactions WHERE NOT isPaused ORDER BY startDate, id")
    suspend fun getActive(): List<PlannedTransaction>

    @Query("SELECT * FROM planned_transactions WHERE id = :id")
    suspend fun getById(id: Long): PlannedTransaction?

    @Insert suspend fun insert(planned: PlannedTransaction): Long
    @Update suspend fun update(planned: PlannedTransaction)
    @Delete suspend fun delete(planned: PlannedTransaction)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY id")
    suspend fun getAll(): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): Budget?

    @Insert suspend fun insert(budget: Budget): Long
    @Update suspend fun update(budget: Budget)
    @Delete suspend fun delete(budget: Budget)
}

/** A receipt line's name, for searching transactions by what was bought. */
data class ReceiptItemName(val transactionId: Long, val name: String)

/** A receipt line with its transaction's date and note (the store), for price comparison. */
data class DatedReceiptItem(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val unitPrice: BigDecimal,
    val date: Instant,
    val store: String,
)

@Dao
interface ReceiptItemDao {
    @Query("SELECT * FROM receipt_items WHERE transactionId = :transactionId ORDER BY position, id")
    fun observeForTransaction(transactionId: Long): Flow<List<ReceiptItem>>

    @Query("SELECT transactionId, name FROM receipt_items")
    fun observeNames(): Flow<List<ReceiptItemName>>

    @Query(
        """SELECT receipt_items.id, name, normalizedName, unitPrice, transactions.date AS date, transactions.note AS store
        FROM receipt_items JOIN transactions ON transactions.id = receipt_items.transactionId"""
    )
    fun observeDated(): Flow<List<DatedReceiptItem>>

    @Insert suspend fun insertAll(items: List<ReceiptItem>)
    @Delete suspend fun delete(item: ReceiptItem)
}

@Dao
interface ReceiptPhotoDao {
    @Query("SELECT * FROM receipt_photos WHERE transactionId = :transactionId ORDER BY pageIndex, id")
    fun observeForTransaction(transactionId: Long): Flow<List<ReceiptPhoto>>

    @Query("SELECT fileName FROM receipt_photos")
    suspend fun getAllFileNames(): List<String>

    @Insert suspend fun insertAll(photos: List<ReceiptPhoto>)
}

@Dao
interface MerchantCategoryDao {
    @Query("SELECT * FROM merchant_categories WHERE merchantKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MerchantCategory?

    @Query("SELECT * FROM merchant_categories ORDER BY updatedAt DESC")
    suspend fun getAllRecentFirst(): List<MerchantCategory>

    @Insert suspend fun insert(mapping: MerchantCategory): Long
    @Update suspend fun update(mapping: MerchantCategory)
}
