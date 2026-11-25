package work.spyglee.ladger.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.domain.receipt.ReceiptRulesParser

/**
 * Runs the real on-device OCR on a drawn receipt (labels on the left, amounts right-aligned), as a
 * photo and as a shared image or PDF, whose pages are kept too.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptRecognitionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val recognizer = ReceiptTextRecognizer(context)

    private val rows = listOf(
        "KONZUM d.d." to "",
        "Zagreb, Ilica 1" to "",
        "Mlijeko 2,8%" to "1,49",
        "Kruh bijeli" to "2,30",
        "UKUPNO EUR" to "12,49",
        "PDV 25%" to "2,50",
        "07.10.2026. 18:42" to "",
    )

    private fun drawReceipt(canvas: Canvas, width: Int) {
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        rows.forEachIndexed { index, (label, amount) ->
            val y = 100f + index * 90f
            canvas.drawText(label, 40f, y, paint)
            if (amount.isNotEmpty()) canvas.drawText(amount, width - 40f - paint.measureText(amount), y, paint)
        }
    }

    private fun assertReadsReceipt(text: String) {
        val receipt = ReceiptRulesParser.parse(text)
        assertEquals("OCR text was:\n$text", BigDecimal("12.49"), receipt.total?.setScale(2))
        assertTrue("OCR text was:\n$text", receipt.merchant.orEmpty().startsWith("KONZUM"))
        assertEquals("OCR text was:\n$text", LocalDate.of(2026, 10, 7), receipt.date)
        assertEquals("OCR text was:\n$text", LocalTime.of(18, 42), receipt.time)
    }

    @Test
    fun readsAPhoto() = runTest {
        val bitmap = Bitmap.createBitmap(900, 760, Bitmap.Config.ARGB_8888)
        drawReceipt(Canvas(bitmap), bitmap.width)
        assertReadsReceipt(recognizer.recognize(bitmap))
    }

    @Test
    fun readsASharedPdfAndKeepsEachPage() = runTest {
        // A narrow page like a receipt PDF, in points; it is rendered at 3x before OCR. The second page is
        // empty: as on iOS, it's still kept with the transaction.
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(300, 254, 1).create())
        page.canvas.scale(1 / 3f, 1 / 3f)
        drawReceipt(page.canvas, 900)
        document.finishPage(page)
        document.finishPage(document.startPage(PdfDocument.PageInfo.Builder(300, 254, 2).create()))
        val file = File(context.cacheDir, "receipt.pdf")
        file.outputStream().use(document::writeTo)
        document.close()

        val receipt = ReceiptScanner(context).read(Uri.fromFile(file))
        assertReadsReceipt(receipt.text)
        // Rendered at 900x762, which the stored page keeps (under 1,600 px).
        assertEquals(listOf(900 to 762, 900 to 762), receipt.pages.map { dimensions(it) })
    }

    @Test
    fun readsASharedPhotoUprightAsItsExifSays() = runTest {
        // Stored sideways, with EXIF saying "turn 90° clockwise", as phone cameras save photos.
        val upright = Bitmap.createBitmap(900, 760, Bitmap.Config.ARGB_8888)
        drawReceipt(Canvas(upright), upright.width)
        val sideways = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(-90f) }, true)
        val file = File(context.cacheDir, "receipt.jpg")
        file.outputStream().use { sideways.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val receipt = ReceiptScanner(context).read(Uri.fromFile(file))
        assertReadsReceipt(receipt.text)
        assertEquals(listOf(900 to 760), receipt.pages.map { dimensions(it) })
    }

    @Test
    fun keepsASharedImageWithoutText() = runTest {
        val blank = Bitmap.createBitmap(2_400, 1_200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val file = File(context.cacheDir, "blank.png")
        file.outputStream().use { blank.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val receipt = ReceiptScanner(context).read(Uri.fromFile(file))
        assertEquals("", receipt.text)
        // Stored at most 1,600 px on its longest side, as iOS stores pages.
        assertEquals(listOf(1_600 to 800), receipt.pages.map { dimensions(it) })
    }

    /** A long receipt photographed in two pages (D9): the merchant on the first, the total and date on the second. */
    @Test
    fun readsCameraPagesInOrderAsOneReceipt() = runTest {
        val whole = Bitmap.createBitmap(900, 760, Bitmap.Config.ARGB_8888)
        drawReceipt(Canvas(whole), whole.width)
        // Cut between "Kruh bijeli" and "UKUPNO EUR".
        val pages = listOf(
            Bitmap.createBitmap(whole, 0, 0, 900, 400),
            Bitmap.createBitmap(whole, 0, 400, 900, 360),
        ).map { page -> ByteArrayOutputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 95, it); it.toByteArray() } }

        val receipt = ReceiptScanner(context).read(pages)

        assertReadsReceipt(receipt.text)
        assertTrue("OCR text was:\n${receipt.text}", receipt.text.indexOf("KONZUM") < receipt.text.indexOf("PDV"))
        assertEquals(listOf(900 to 400, 900 to 360), receipt.pages.map { dimensions(it) })
    }

    @Test
    fun readsAtMostFivePages() = runTest {
        val blank = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val jpeg = ByteArrayOutputStream().use { blank.compress(Bitmap.CompressFormat.JPEG, 95, it); it.toByteArray() }

        assertEquals(ReceiptTextRecognizer.MAX_PAGES, ReceiptScanner(context).read(List(6) { jpeg }).pages.size)
    }

    private fun dimensions(jpeg: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        return bounds.outWidth to bounds.outHeight
    }
}
