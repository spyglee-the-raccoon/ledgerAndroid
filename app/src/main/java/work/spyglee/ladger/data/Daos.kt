package work.spyglee.ladger.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
interface MerchantCategoryDao {
    @Query("SELECT * FROM merchant_categories WHERE merchantKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MerchantCategory?

    @Query("SELECT * FROM merchant_categories ORDER BY updatedAt DESC")
    suspend fun getAllRecentFirst(): List<MerchantCategory>

    @Insert suspend fun insert(mapping: MerchantCategory): Long
    @Update suspend fun update(mapping: MerchantCategory)
}
