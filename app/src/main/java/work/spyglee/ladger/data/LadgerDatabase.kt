package work.spyglee.ladger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Category::class,
        Source::class,
        Transaction::class,
        MerchantCategory::class,
        TransactionCollection::class,
        CollectionTransaction::class,
    ],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class LadgerDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun sourceDao(): SourceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        fun build(context: Context): LadgerDatabase =
            Room.databaseBuilder(context, LadgerDatabase::class.java, "ladger.db").build()
    }
}
