package work.spyglee.ladger.domain.receipt

import kotlin.math.abs

/** One line of recognized text and where it is on the image, in pixels. */
data class TextLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Int get() = bottom - top
}

/**
 * Puts OCR output back into receipt rows. ML Kit groups text into layout blocks, so a total's label
 * ("UKUPNO") and its right-aligned amount often land in different blocks, far apart in the text.
 * The rules parser expects them on one line, as Apple's document recognizer returns them on iOS.
 */
object ReceiptTextLayout {
    /**
     * Lines whose vertical centers are within half a line height of a row's center join that row,
     * left to right; rows are returned top to bottom, one per line of text.
     */
    fun toText(lines: List<TextLine>): String {
        val rows = mutableListOf<MutableList<TextLine>>()
        for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null && abs(line.centerY - row.centerY()) <= minOf(line.height, row.averageHeight()) / 2f) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    private fun List<TextLine>.centerY(): Float = map { it.centerY }.average().toFloat()

    private fun List<TextLine>.averageHeight(): Int = map { it.height }.average().toInt()
}
