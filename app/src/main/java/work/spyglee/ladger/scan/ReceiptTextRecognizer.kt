package work.spyglee.ladger.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import work.spyglee.ladger.domain.receipt.ReceiptTextLayout
import work.spyglee.ladger.domain.receipt.TextLine

/**
 * Reads the text of receipt photos and PDFs with ML Kit's bundled text recognizer, which runs
 * entirely in the app's process. It reads Latin script only, so Ukrainian (Cyrillic) labels are
 * not recognized; amounts and dates still are.
 */
class ReceiptTextRecognizer(context: Context) {
    private val context = context.applicationContext
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): String = recognize(InputImage.fromBitmap(bitmap, 0))

    /** A shared image or PDF. Images are read with their EXIF rotation applied. */
    suspend fun recognize(uri: Uri): String =
        if (isPdf(uri)) recognizePdf(uri) else recognize(withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) })

    private fun isPdf(uri: Uri): Boolean =
        context.contentResolver.getType(uri) == PDF_MIME_TYPE || uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)

    /** Renders up to [MAX_PDF_PAGES] pages at [PDF_RENDER_SCALE]× so small receipt text stays legible, as on iOS. */
    private suspend fun recognizePdf(uri: Uri): String = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext ""
        PdfRenderer(descriptor).use { renderer ->
            (0 until minOf(renderer.pageCount, MAX_PDF_PAGES)).map { index ->
                val bitmap = renderer.openPage(index).use { page ->
                    var scale = PDF_RENDER_SCALE
                    val pixels = page.width * scale * page.height * scale
                    if (pixels > MAX_PDF_PAGE_PIXELS) scale *= sqrt(MAX_PDF_PAGE_PIXELS / pixels)
                    Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888).also {
                        // PDF pages are transparent where nothing is drawn; OCR needs dark text on light paper.
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
                try {
                    recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }.filter { it.isNotBlank() }.joinToString("\n")
        }
    }

    private suspend fun recognize(image: InputImage): String {
        val text = recognizer.process(image).await()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                line.boundingBox?.let { TextLine(line.text, it.left, it.top, it.right, it.bottom) }
            }
        }
        return ReceiptTextLayout.toText(lines)
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val MAX_PDF_PAGES = 5
        const val PDF_RENDER_SCALE = 3f

        /** About 50 MB per rendered page; larger pages are rendered at a lower scale. */
        const val MAX_PDF_PAGE_PIXELS = 12_000_000f
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
