package work.spyglee.ladger.data

import androidx.room.withTransaction
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import work.spyglee.ladger.domain.receipt.ParsedReceiptItem
import work.spyglee.ladger.domain.receipt.ReceiptItems

/**
 * Scanned receipt pages and the products read from them, kept with their transaction. Pages are
 * JPEG files in [directory] (iOS keeps them in external storage of the database); the rows go when
 * the transaction is deleted, and [sweepOrphans] then removes the files.
 */
class ReceiptRepository(private val database: LadgerDatabase, private val directory: File) {
    private val itemDao = database.receiptItemDao()
    private val photoDao = database.receiptPhotoDao()

    fun observeItems(transactionId: Long): Flow<List<ReceiptItem>> = itemDao.observeForTransaction(transactionId)

    /** The pages' files in page order. */
    fun observePhotos(transactionId: Long): Flow<List<File>> =
        photoDao.observeForTransaction(transactionId).map { photos -> photos.map { File(directory, it.fileName) } }

    fun observeDatedItems(): Flow<List<DatedReceiptItem>> = itemDao.observeDated()

    /** Saves [photos] (JPEG data, in page order) and [items] with transaction [transactionId]. */
    suspend fun attach(transactionId: Long, photos: List<ByteArray>, items: List<ParsedReceiptItem>) {
        if (photos.isEmpty() && items.isEmpty()) return
        val fileNames = withContext(Dispatchers.IO) {
            directory.mkdirs()
            photos.map { bytes -> "${UUID.randomUUID()}.jpg".also { File(directory, it).writeBytes(bytes) } }
        }
        database.withTransaction {
            photoDao.insertAll(fileNames.mapIndexed { index, name -> ReceiptPhoto(transactionId = transactionId, fileName = name, pageIndex = index) })
            itemDao.insertAll(
                items.mapIndexed { index, item ->
                    ReceiptItem(
                        transactionId = transactionId,
                        name = item.name,
                        normalizedName = ReceiptItems.normalize(item.name),
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        totalPrice = item.totalPrice,
                        position = index,
                    )
                }
            )
        }
    }

    suspend fun deleteItem(item: ReceiptItem) = itemDao.delete(item)

    /**
     * Deletes page files no transaction refers to any more. Files written in the last hour are kept,
     * since [attach] writes them just before inserting their rows.
     */
    suspend fun sweepOrphans(now: Instant = Instant.now()) {
        val referenced = photoDao.getAllFileNames().toSet()
        withContext(Dispatchers.IO) {
            val cutoff = now.minus(ORPHAN_AGE).toEpochMilli()
            directory.listFiles()
                ?.filter { it.name !in referenced && it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        val ORPHAN_AGE: Duration = Duration.ofHours(1)
    }
}
