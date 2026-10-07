package work.spyglee.ladger.scan

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import work.spyglee.ladger.domain.receipt.GeneratedReceipt
import work.spyglee.ladger.domain.receipt.ReceiptPrompt

/**
 * Reads receipts with Gemini Nano, which runs on the device in the AICore system service. It is only
 * used when the model is already downloaded: the app never starts a download, and on devices
 * without Gemini Nano the rules parser is used alone, like iOS without Apple Intelligence.
 */
class NanoReceiptParser {
    private val model: GenerativeModel? by lazy {
        try {
            Generation.getClient()
        } catch (e: Exception) {
            Log.i(TAG, "Gemini Nano client unavailable", e)
            null
        }
    }

    private var warmedUp = false

    private suspend fun availableModel(): GenerativeModel? {
        val model = model ?: return null
        return try {
            model.takeIf { it.checkStatus() == FeatureStatus.AVAILABLE }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Loads the model while the user is still taking the photo, as iOS prewarms when Scan is tapped. */
    suspend fun prewarm() {
        if (warmedUp) return
        val model = availableModel() ?: return
        try {
            model.warmup()
            warmedUp = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "Gemini Nano warmup failed", e)
        }
    }

    /** The model's reading of [receiptText], or null when the model is unavailable, fails or takes too long. */
    suspend fun generate(receiptText: String, categoryNames: List<String>): GeneratedReceipt? {
        val model = availableModel() ?: return null
        return try {
            val response = withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.generateContent(
                    generateContentRequest(SystemInstruction(ReceiptPrompt.instructions(categoryNames)), TextPart(ReceiptPrompt.prompt(receiptText))) {
                        temperature = 0f
                        topK = 1
                        maxOutputTokens = MAX_OUTPUT_TOKENS
                    }
                )
            }
            ReceiptPrompt.parseResponse(response?.candidates?.firstOrNull()?.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "Gemini Nano failed; using the rules parser", e)
            null
        }
    }

    private companion object {
        const val TAG = "NanoReceiptParser"
        const val TIMEOUT_MILLIS = 20_000L
        const val MAX_OUTPUT_TOKENS = 200
    }
}
