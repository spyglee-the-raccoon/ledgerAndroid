package work.spyglee.ladger.ui.lock

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.ui.theme.toColor

/**
 * iOS's BackgroundView: a radial gradient from the top-left corner, indigo to blue to mint, dimmed
 * in dark mode.
 */
@Composable
private fun lockBackground(): Brush {
    val alpha = if (isSystemInDarkTheme()) 0.7f else 1f
    val colors = listOf(CategoryColor.INDIGO, CategoryColor.BLUE, CategoryColor.MINT).map { it.toColor().copy(alpha = alpha) }
    val radius = with(LocalDensity.current) { 950.dp.toPx() }
    return Brush.radialGradient(colors, center = Offset.Zero, radius = radius)
}

/** Covers the whole app while it's locked. Back leaves the app instead of reaching the screens underneath. */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity
    BackHandler { activity?.moveTaskToBack(true) }
    // The opaque surface under the dimmed gradient keeps the locked screens from showing through.
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(lockBackground()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(100.dp))
            Text(stringResource(R.string.app_locked), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = onUnlock) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.unlock))
            }
        }
    }
}
