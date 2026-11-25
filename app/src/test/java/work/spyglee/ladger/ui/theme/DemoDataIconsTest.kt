package work.spyglee.ladger.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Icons travel in the CSV files as iOS SF Symbol names (`docs/csv-format.md`). Every name in iOS's
 * `DemoData` must have an Android icon, or it shows as the fallback tag.
 */
class DemoDataIconsTest {
    /** In `DemoData/generate.py` but not in iOS's `Icons.swift`, so neither app offers it. */
    private val knownMissing = setOf("desktopcomputer")

    private fun icons(file: String): List<String> {
        val text = checkNotNull(javaClass.getResourceAsStream("/ios-demo-data/$file")) { file }
            .use { it.readBytes().decodeToString() }
        val lines = text.lines().filter { it.isNotBlank() }
        val column = lines.first().split(";").indexOf("icon")
        check(column >= 0) { "$file has no icon column" }
        return lines.drop(1).map { it.split(";")[column].trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun `every icon in the iOS demo data has an Android icon`() {
        val used = listOf("accounts.csv", "categories.csv", "collections.csv").flatMap(::icons).toSet()

        assertEquals(emptySet<String>(), used - pickableIcons.keys - knownMissing)
        assertTrue(
            "iOS fixed the demo data; remove ${knownMissing - used} from knownMissing",
            used.containsAll(knownMissing),
        )
    }
}
