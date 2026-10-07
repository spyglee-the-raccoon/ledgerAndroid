package work.spyglee.ladger.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import work.spyglee.ladger.ui.lock.LockScreen
import work.spyglee.ladger.ui.theme.LadgerTheme

/**
 * The lock screen sits on top of the app's screens. Its gradient is dimmed in dark mode, as on iOS,
 * so it needs an opaque base: rendered over two different backgrounds it must look exactly the same.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun assertHidesWhatIsUnderneath(dark: Boolean) {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        // Stand-in for the balances and transactions under the lock.
        val underneath = mutableStateOf(Color.Red)
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                LadgerTheme {
                    Box(Modifier.fillMaxSize().testTag("root")) {
                        Box(Modifier.fillMaxSize().background(underneath.value))
                        LockScreen(onUnlock = {})
                    }
                }
            }
        }
        val overRed = capture()
        underneath.value = Color.Blue
        compose.waitForIdle()
        val overBlue = capture()
        assertEquals("pixels that differ", 0, differingPixels(overRed, overBlue))
    }

    private fun capture(): Bitmap = compose.onNodeWithTag("root").captureToImage().asAndroidBitmap()

    private fun differingPixels(a: Bitmap, b: Bitmap): Int {
        var count = 0
        for (y in 0 until a.height step 3) {
            for (x in 0 until a.width step 3) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) count++
            }
        }
        return count
    }

    @Test
    fun hidesTheAppInLightMode() = assertHidesWhatIsUnderneath(dark = false)

    @Test
    fun hidesTheAppInDarkMode() = assertHidesWhatIsUnderneath(dark = true)
}
