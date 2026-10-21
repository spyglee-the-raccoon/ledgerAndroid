package work.spyglee.ladger.data

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant

class Converters {
    // Amounts are stored as text so they round-trip exactly, like Swift's Decimal.
    @TypeConverter fun fromBigDecimal(value: BigDecimal): String = value.toPlainString()
    @TypeConverter fun toBigDecimal(value: String): BigDecimal = BigDecimal(value)

    @TypeConverter fun fromInstant(value: Instant): Long = value.toEpochMilli()
    @TypeConverter fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter fun fromTransactionKind(value: TransactionKind): String = value.raw
    @TypeConverter fun toTransactionKind(value: String): TransactionKind = TransactionKind.fromRaw(value)

    @TypeConverter fun fromCategoryKind(value: CategoryKind): String = value.raw
    @TypeConverter fun toCategoryKind(value: String): CategoryKind = CategoryKind.fromRaw(value)

    @TypeConverter fun fromCategoryColor(value: CategoryColor): String = value.raw
    @TypeConverter fun toCategoryColor(value: String): CategoryColor = CategoryColor.fromRaw(value)

    @TypeConverter fun fromRecurrenceFrequency(value: RecurrenceFrequency): String = value.raw
    @TypeConverter fun toRecurrenceFrequency(value: String): RecurrenceFrequency = RecurrenceFrequency.fromRaw(value)
}
