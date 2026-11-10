package work.spyglee.ladger.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import kotlin.math.min
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.domain.WidgetSlice
import work.spyglee.ladger.ui.theme.DarkColors
import work.spyglee.ladger.ui.theme.LightColors
import work.spyglee.ladger.ui.theme.toColor

/**
 * The parts of the widgets that Glance can't draw itself: the month ring and the category icons.
 * They are drawn for the theme in use when the widget updates; the app updates its widgets when the
 * theme changes, and the colors are close in both themes anyway.
 */
object WidgetBitmaps {
    // As the donut on the main screen.
    private const val INNER_RADIUS_RATIO = 0.618f
    private const val OUTER_RADIUS_RATIO = 0.92f
    private const val GAP_DEGREES = 1.5f

    fun isDark(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** A category color as ARGB, for the theme [context] is in. */
    fun argb(context: Context, color: CategoryColor): Int {
        val dark = isDark(context)
        return color.toColor(dark, primary = (if (dark) DarkColors else LightColors).onSurface).toArgb()
    }

    /** The month's spending as a ring, [sizePx] wide; without spending one gray ring keeps the shape, as on iOS. */
    fun ring(context: Context, slices: List<WidgetSlice>, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f
        val inner = radius * INNER_RADIUS_RATIO
        val outer = radius * OUTER_RADIUS_RATIO
        val middle = (inner + outer) / 2f
        val oval = RectF(radius - middle, radius - middle, radius + middle, radius + middle)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = outer - inner
        }
        val total = slices.sumOf { it.amount.toDouble() }
        if (total <= 0.0) {
            paint.color = argb(context, CategoryColor.GRAY)
            paint.alpha = (255 * 0.3f).toInt()
            canvas.drawArc(oval, -90f, 360f, false, paint)
            return bitmap
        }
        var start = -90f
        for (slice in slices) {
            val sweep = (slice.amount.toDouble() / total * 360.0).toFloat()
            val gap = if (slices.size > 1) min(GAP_DEGREES, sweep / 2f) else 0f
            paint.color = argb(context, slice.color)
            canvas.drawArc(oval, start + gap / 2f, sweep - gap, false, paint)
            start += sweep
        }
        return bitmap
    }

    /**
     * A category icon on a light circle of its color, [sizePx] wide, as the app's category badges. With
     * [circle] false only the icon is drawn, e.g. for the empty-state icons.
     */
    fun badge(icon: ImageVector, argb: Int, sizePx: Int, circle: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb }
        val iconSize = if (circle) {
            paint.alpha = (255 * 0.2f).toInt()
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
            paint.alpha = 255
            sizePx * 0.55f
        } else {
            sizePx.toFloat()
        }
        val offset = (sizePx - iconSize) / 2f
        canvas.save()
        canvas.translate(offset, offset)
        canvas.scale(iconSize / icon.viewportWidth, iconSize / icon.viewportHeight)
        drawGroup(canvas, icon.root, paint)
        canvas.restore()
        return bitmap
    }

    /** Draws a vector icon's paths filled with [paint]; Material icons are filled paths with plain transforms. */
    private fun drawGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorPath -> {
                    val path: Path = node.pathData.toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    canvas.drawPath(path, paint)
                }
                is VectorGroup -> drawGroup(canvas, node, paint)
            }
        }
        canvas.restore()
    }
}
