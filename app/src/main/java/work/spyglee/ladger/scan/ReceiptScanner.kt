package work.spyglee.ladger.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.spyglee.ladger.BuildConfig
import work.spyglee.ladger.domain.receipt.ParsedReceipt
import work.spyglee.ladger.domain.receipt.ParsedReceiptItem
import work.spyglee.ladger.domain.receipt.ReceiptRulesParser

/** Receipt photo or file → text → total, date, merchant and category, all on the device. */
class ReceiptScanner(context: Context) {
    private val cacheDir = context.cacheDir
    private val recognizer = ReceiptTextRecognizer(context)
    private val nano = NanoReceiptParser()

    suspend fun prewarm() = nano.prewarm()

    suspend fun recognize(bitmap: Bitmap): String {
        if (BuildConfig.DEBUG) saveForDebugging(bitmap)
        return recognizer.recognize(bitmap)
    }

    /**
     * Debug builds only: keeps the last photo in app-private storage, so a misread receipt can be
     * pulled with `adb exec-out run-as work.spyglee.ladger cat cache/last-receipt.jpg > last-receipt.jpg`.
     */
    private suspend fun saveForDebugging(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        File(cacheDir, "last-receipt.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    suspend fun recognize(uri: Uri): String = recognizer.recognize(uri)

    /**
     * The photo as stored with the transaction: at most [MAX_PHOTO_SIDE] px on its longest side and
     * JPEG quality 60, as iOS stores scanned pages; enough to read the receipt, small enough to keep many.
     */
    suspend fun photoData(bitmap: Bitmap): ByteArray = withContext(Dispatchers.Default) {
        val scale = minOf(1f, MAX_PHOTO_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val resized = if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
        } else {
            bitmap
        }
        ByteArrayOutputStream().use { stream ->
            resized.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            if (resized !== bitmap) resized.recycle()
            stream.toByteArray()
        }
    }

    /** The purchased products, read by Gemini Nano; empty without it, as on iOS without Apple Intelligence. */
    suspend fun parseItems(text: String): List<ParsedReceiptItem> = nano.generateItems(text)

    /** The rules parser's result, improved by Gemini Nano where available (same merge rules as iOS). */
    suspend fun parse(text: String, expenseCategoryNames: List<String>): ParsedReceipt =
        ReceiptRulesParser.merge(
            fallback = ReceiptRulesParser.parse(text),
            generated = nano.generate(text, expenseCategoryNames),
            categoryNames = expenseCategoryNames,
        )

    private companion object {
        const val MAX_PHOTO_SIDE = 1_600
    }
}
