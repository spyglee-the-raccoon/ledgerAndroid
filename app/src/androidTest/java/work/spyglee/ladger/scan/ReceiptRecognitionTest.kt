package work.spyglee.ladger.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** Runs the real on-device OCR on a drawn receipt: labels on the left, amounts right-aligned. */
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
    fun readsAPdf() = runTest {
        // A narrow page like a receipt PDF, in points; it is rendered at 3x before OCR.
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(300, 254, 1).create())
        page.canvas.scale(1 / 3f, 1 / 3f)
        drawReceipt(page.canvas, 900)
        document.finishPage(page)
        val file = File(context.cacheDir, "receipt.pdf")
        file.outputStream().use(document::writeTo)
        document.close()

        assertReadsReceipt(recognizer.recognize(Uri.fromFile(file)))
    }
}
