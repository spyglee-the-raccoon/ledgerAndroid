package work.spyglee.ladger.scan

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test
    fun `presets include the ultra-wide lens and the telephoto steps the camera supports`() {
        assertEquals(listOf(0.6f, 1f, 2f, 5f), CameraZoom.presets(0.6f, 10f))
        assertEquals(listOf(1f, 2f), CameraZoom.presets(1f, 4f))
        assertEquals(listOf(1f), CameraZoom.presets(1f, 1f))
    }

    @Test
    fun `active preset is the largest one at or below the current zoom`() {
        val presets = listOf(0.6f, 1f, 2f, 5f)
        assertEquals(0, CameraZoom.activeIndex(presets, 0.6f))
        assertEquals(1, CameraZoom.activeIndex(presets, 1f))
        assertEquals(1, CameraZoom.activeIndex(presets, 1.7f))
        assertEquals(2, CameraZoom.activeIndex(presets, 1.97f))
        assertEquals(3, CameraZoom.activeIndex(presets, 8f))
    }

    @Test
    fun `labels drop the decimal for whole steps and use the locale otherwise`() {
        assertEquals("1×", CameraZoom.label(1f, Locale.US))
        assertEquals("2×", CameraZoom.label(1.98f, Locale.US))
        assertEquals("0.6×", CameraZoom.label(0.6f, Locale.US))
        assertEquals("1,4×", CameraZoom.label(1.43f, Locale.forLanguageTag("hr")))
    }
}
