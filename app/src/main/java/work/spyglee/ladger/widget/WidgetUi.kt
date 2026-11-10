package work.spyglee.ladger.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import work.spyglee.ladger.MainActivity
import work.spyglee.ladger.QuickAction
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.ui.theme.DarkColors
import work.spyglee.ladger.ui.theme.LightColors
import work.spyglee.ladger.ui.theme.toColor

/** The app's colors for widgets, following the system's light or dark theme. */
private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** A category color for widget text and dots, in its light and dark variants. */
fun CategoryColor.provider(): ColorProvider = DayNightColor(
    day = toColor(dark = false, primary = LightColors.onSurface),
    night = toColor(dark = true, primary = DarkColors.onSurface),
)

/**
 * The frame every widget shares: the app's colors, a rounded background like other widgets, and a
 * tap that opens the app at [link] (the main screen when null), as iOS's widget links.
 */
@Composable
fun WidgetFrame(link: QuickAction?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    GlanceTheme(colors = WidgetColors) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(openApp(context, link))),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** Opens the app in a fresh task, as the app icon shortcuts do, at [link] if set; the app lock applies. */
private fun openApp(context: Context, link: QuickAction?): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(link?.action ?: Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

@Composable
fun primaryText(size: TextUnit = 13.sp, bold: Boolean = false, color: ColorProvider = GlanceTheme.colors.onSurface) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@Composable
fun secondaryText(size: TextUnit = 12.sp) = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = size)

/** A category badge as in the app's lists, drawn as a picture ([WidgetBitmaps.badge]). */
@Composable
fun Badge(icon: ImageVector, color: CategoryColor, size: Dp) {
    val context = LocalContext.current
    val px = (size.value * context.resources.displayMetrics.density).toInt()
    Image(
        provider = ImageProvider(WidgetBitmaps.badge(icon, WidgetBitmaps.argb(context, color), px)),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}

/** An icon and a line of text in the middle of an empty widget, as iOS's empty states. */
@Composable
fun EmptyState(icon: ImageVector, text: String) {
    val context = LocalContext.current
    val px = (24 * context.resources.displayMetrics.density).toInt()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            provider = ImageProvider(WidgetBitmaps.badge(icon, android.graphics.Color.WHITE, px, circle = false)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(24.dp),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(text, style = secondaryText().copy(textAlign = TextAlign.Center))
    }
}
