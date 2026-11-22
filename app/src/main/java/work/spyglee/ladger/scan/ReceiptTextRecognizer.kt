package work.spyglee.ladger.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
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

    /**
     * A shared image or PDF: the text of its pages, and each page made by [toPage] into the photo saved
     * with the transaction, as iOS does since `1748a53`. A page and its text are kept or lost
     * separately, so a receipt whose text can't be read still keeps its pages, as on iOS.
     */
    suspend fun read(uri: Uri, toPage: suspend (Bitmap) -> ByteArray): ScannedReceipt {
        val texts = mutableListOf<String>()
        val pages = mutableListOf<ByteArray>()
        forEachPage(uri) { bitmap ->
            attempt("Keeping a shared page") { toPage(bitmap) }?.let(pages::add)
            attempt("Reading a shared page") { recognize(bitmap) }?.takeIf { it.isNotBlank() }?.let(texts::add)
        }
        return ScannedReceipt(texts.joinToString("\n"), pages)
    }

    /** Runs [block] on each page of the image or PDF, then recycles the page. */
    private suspend fun forEachPage(uri: Uri, block: suspend (Bitmap) -> Unit) {
        if (!isPdf(uri)) {
            val bitmap = withContext(Dispatchers.IO) { decodeImage(uri) } ?: return
            try {
                block(bitmap)
            } finally {
                bitmap.recycle()
            }
            return
        }
        val descriptor = withContext(Dispatchers.IO) { context.contentResolver.openFileDescriptor(uri, "r") } ?: return
        PdfRenderer(descriptor).use { renderer ->
            for (index in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                val bitmap = withContext(Dispatchers.IO) { renderer.openPage(index).use(::render) }
                try {
                    block(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun isPdf(uri: Uri): Boolean =
        context.contentResolver.getType(uri) == PDF_MIME_TYPE || uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)

    /** Renders a PDF page at [PDF_RENDER_SCALE]× so small receipt text stays legible, as on iOS. */
    private fun render(page: PdfRenderer.Page): Bitmap {
        var scale = PDF_RENDER_SCALE
        val pixels = page.width * scale * page.height * scale
        if (pixels > MAX_PDF_PAGE_PIXELS) scale *= sqrt(MAX_PDF_PAGE_PIXELS / pixels)
        return Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888).also {
            // PDF pages are transparent where nothing is drawn; OCR needs dark text on light paper.
            it.eraseColor(Color.WHITE)
            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    /**
     * A shared image, turned upright as its EXIF orientation says (as ML Kit's `fromFilePath` did), and
     * halved until its longest side is at most [MAX_IMAGE_SIDE] px. Null when it can't be decoded.
     */
    private fun decodeImage(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val exif = try {
            resolver.openInputStream(uri)?.use(::ExifInterface)
        } catch (e: IOException) {
            null
        }
        val rotation = exif?.rotationDegrees ?: 0
        val flipped = exif?.isFlipped ?: false
        if (rotation == 0 && !flipped) return bitmap
        // ExifInterface's rotation is meant to be applied after the horizontal flip.
        val matrix = Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    /** Runs [block]; a failure is logged and gives null. */
    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed", e)
        null
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
        const val TAG = "ReceiptTextRecognizer"
        const val PDF_MIME_TYPE = "application/pdf"
        const val MAX_PDF_PAGES = 5
        const val PDF_RENDER_SCALE = 3f

        /** About 50 MB per rendered page; larger pages are rendered at a lower scale. */
        const val MAX_PDF_PAGE_PIXELS = 12_000_000f

        /** Plenty for OCR; a 50 MP photo is read at half size instead of taking 200 MB. */
        const val MAX_IMAGE_SIDE = 4_096
    }
}

/** A read receipt: its text, and its pages (JPEG) to save with the transaction. */
class ScannedReceipt(val text: String, val pages: List<ByteArray>)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
