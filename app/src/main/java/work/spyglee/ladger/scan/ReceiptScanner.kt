package work.spyglee.ladger.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.spyglee.ladger.BuildConfig
import work.spyglee.ladger.domain.receipt.ParsedReceipt
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

    /** The rules parser's result, improved by Gemini Nano where available (same merge rules as iOS). */
    suspend fun parse(text: String, expenseCategoryNames: List<String>): ParsedReceipt =
        ReceiptRulesParser.merge(
            fallback = ReceiptRulesParser.parse(text),
            generated = nano.generate(text, expenseCategoryNames),
            categoryNames = expenseCategoryNames,
        )
}
