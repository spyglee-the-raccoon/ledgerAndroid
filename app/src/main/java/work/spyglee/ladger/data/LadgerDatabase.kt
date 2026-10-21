package work.spyglee.ladger.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Version 2 adds planned transactions (iOS `f909b82`). */
@Database(
    entities = [
        Category::class,
        Source::class,
        Transaction::class,
        MerchantCategory::class,
        TransactionCollection::class,
        CollectionTransaction::class,
        PlannedTransaction::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class LadgerDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun sourceDao(): SourceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao
    abstract fun collectionDao(): CollectionDao
    abstract fun plannedTransactionDao(): PlannedTransactionDao

    companion object {
        fun build(context: Context): LadgerDatabase =
            Room.databaseBuilder(context, LadgerDatabase::class.java, "ladger.db").build()
    }
}
