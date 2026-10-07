package work.spyglee.ladger.domain

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{Mn}+")

/** Any line break, the same set as Swift's `CharacterSet.newlines`; `\r\n` counts as one break. */
val lineBreak = Regex("""\R""")

/**
 * Case- and diacritic-insensitive form of a string, like Foundation's
 * `folding(options: [.caseInsensitive, .diacriticInsensitive])`. `đ` has no Unicode decomposition,
 * so it is mapped explicitly to keep Croatian names searchable.
 */
fun String.folded(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace('đ', 'd')

/** Equivalent of `localizedStandardContains`: ignores case and diacritics. */
fun String.containsFolded(query: String): Boolean = folded().contains(query.folded())
