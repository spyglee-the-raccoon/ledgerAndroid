package work.spyglee.ladger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

/** `systemImage` keeps the iOS SF Symbol name as a stable icon key; the UI maps it to a Material icon. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemImage: String = "tag",
    val color: CategoryColor = CategoryColor.PRIMARY,
    val kind: CategoryKind = CategoryKind.EXPENSE,
)

/** A wallet or account money moves in and out of. */
@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemImage: String = "creditcard",
    val initValue: BigDecimal = BigDecimal.ZERO,
    val color: CategoryColor = CategoryColor.BLUE,
    val createdAt: Instant = Instant.now(),
)

/**
 * Expenses use [sourceId] and [categoryId], incomes use [destinationId] and [categoryId],
 * transfers move money from [sourceId] to [destinationId] without a category.
 * Deleting a source or category keeps its transactions and clears the reference, as on iOS.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(Source::class, ["id"], ["sourceId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Source::class, ["id"], ["destinationId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("sourceId"), Index("destinationId"), Index("categoryId"), Index("date")],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: BigDecimal,
    val kind: TransactionKind,
    val sourceId: Long? = null,
    val destinationId: Long? = null,
    val note: String = "",
    val date: Instant = Instant.now(),
    val categoryId: Long? = null,
)

/** A user-named group of transactions, e.g. "Trip to Naples". */
@Entity(tableName = "collections")
data class TransactionCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemImage: String = "globe",
    val color: CategoryColor = CategoryColor.BLUE,
)

/**
 * Membership of a transaction in a collection. A transaction can belong to several collections.
 * Deleting either side removes only the membership: transactions outlive their collections.
 */
@Entity(
    tableName = "collection_transactions",
    primaryKeys = ["collectionId", "transactionId"],
    foreignKeys = [
        ForeignKey(TransactionCollection::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Transaction::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("transactionId")],
)
data class CollectionTransaction(
    val collectionId: Long,
    val transactionId: Long,
)

/** Category and source the user picked for a scanned merchant, used to prefill the next scan. */
@Entity(
    tableName = "merchant_categories",
    foreignKeys = [
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Source::class, ["id"], ["sourceId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("merchantKey", unique = true), Index("categoryId"), Index("sourceId")],
)
data class MerchantCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantKey: String,
    val categoryId: Long?,
    val sourceId: Long?,
    val updatedAt: Instant = Instant.now(),
)
