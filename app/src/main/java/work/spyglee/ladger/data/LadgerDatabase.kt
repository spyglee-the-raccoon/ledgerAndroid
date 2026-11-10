package work.spyglee.ladger.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Version 2 adds planned transactions (iOS `f909b82`), version 3 budgets and receipt items and photos (iOS `46702e5`). */
@Database(
    entities = [
        Category::class,
        Source::class,
        Transaction::class,
        MerchantCategory::class,
        TransactionCollection::class,
        CollectionTransaction::class,
        PlannedTransaction::class,
        Budget::class,
        ReceiptItem::class,
        ReceiptPhoto::class,
    ],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class LadgerDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun sourceDao(): SourceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao
    abstract fun collectionDao(): CollectionDao
    abstract fun plannedTransactionDao(): PlannedTransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun receiptItemDao(): ReceiptItemDao
    abstract fun receiptPhotoDao(): ReceiptPhotoDao

    companion object {
        fun build(context: Context): LadgerDatabase =
            Room.databaseBuilder(context, LadgerDatabase::class.java, "ladger.db").build()
    }
}
