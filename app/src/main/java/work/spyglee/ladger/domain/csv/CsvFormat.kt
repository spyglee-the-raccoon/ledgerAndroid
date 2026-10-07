package work.spyglee.ladger.domain.csv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Shared CSV layout, identical to the iOS app's: semicolon-separated, `dd.MM.yyyy` dates,
 * one file per transaction kind. Files exported on one platform import on the other unchanged.
 */
internal object CsvFormat {
    const val SEPARATOR = ";"

    val exportDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    val importDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT)
}

data class CsvFile(val fileName: String, val text: String)

object CsvText {
    private val fallbackCharsets = listOf(Charsets.UTF_8, Charset.forName("windows-1250"))

    /**
     * Decodes file bytes as UTF-8, falling back to Windows-1250 (common for Croatian bank exports)
     * and finally Latin-1, which accepts any byte sequence.
     */
    fun decode(bytes: ByteArray): String {
        for (charset in fallbackCharsets) {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                return decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                continue
            }
        }
        return String(bytes, Charsets.ISO_8859_1)
    }
}
