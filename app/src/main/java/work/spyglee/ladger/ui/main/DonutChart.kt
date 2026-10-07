package work.spyglee.ladger.ui.main

import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.domain.CategorySlice
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

private const val INNER_RADIUS_RATIO = 0.618f
private const val UNSELECTED_OUTER_RATIO = 0.92f
private const val GAP_DEGREES = 1.5f
private const val UNCATEGORIZED_KEY = -1L

private val CategorySlice.key: Long get() = category?.id ?: UNCATEGORIZED_KEY

/**
 * This month's expenses by category as a ring. Tapping a slice selects it and shows its category
 * and amount in the middle; tapping it again, or outside the ring, shows the month balance.
 */
@Composable
fun DonutChart(slices: List<CategorySlice>, monthBalance: BigDecimal, modifier: Modifier = Modifier) {
    var storedKey by rememberSaveable { mutableStateOf<Long?>(null) }
    // A selected category can disappear (e.g. its last expense was deleted); then nothing is selected.
    val selected = slices.firstOrNull { it.key == storedKey }
    val selectedKey = selected?.key
    val currentSlices by rememberUpdatedState(slices)
    val currentSelectedKey by rememberUpdatedState(selectedKey)
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    val colors = slices.map { it.category?.color ?: CategoryColor.GRAY }.map { it.toColor() }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.fold(0.0) { sum, slice -> sum + slice.amount.toDouble() }

    BoxWithConstraints(modifier.aspectRatio(1f).padding(16.dp), contentAlignment = Alignment.Center) {
        val diameterPx = with(LocalDensity.current) { min(maxWidth.toPx(), maxHeight.toPx()) }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val radius = min(size.width, size.height) / 2f
                        val dx = tap.x - size.width / 2f
                        val dy = tap.y - size.height / 2f
                        val distance = hypot(dx, dy)
                        val hit = if (distance in radius * INNER_RADIUS_RATIO..radius) {
                            sliceAt(currentSlices, clockwiseAngleFromTop(dx, dy))
                        } else {
                            null
                        }
                        val newKey = hit?.key?.takeIf { it != currentSelectedKey }
                        if (newKey != currentSelectedKey) {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            storedKey = newKey
                        }
                    }
                },
        ) {
            val radius = min(size.width, size.height) / 2f
            val innerRadius = radius * INNER_RADIUS_RATIO
            if (total <= 0.0) {
                drawRing(trackColor, -90f, 360f, innerRadius, radius * UNSELECTED_OUTER_RATIO)
                return@Canvas
            }
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = (slice.amount.toDouble() / total * 360.0).toFloat()
                val isSelected = selectedKey == slice.key
                val outer = radius * if (isSelected) 1f else UNSELECTED_OUTER_RATIO
                val gap = if (slices.size > 1) min(GAP_DEGREES, sweep / 2f) else 0f
                val alpha = if (selectedKey == null || isSelected) 1f else 0.35f
                drawRing(colors[index].copy(alpha = alpha), start + gap / 2f, sweep - gap, innerRadius, outer)
                start += sweep
            }
        }

        val innerDiameter = with(LocalDensity.current) { (diameterPx * INNER_RADIUS_RATIO).toDp() }
        val centerScale by animateFloatAsState(if (selected != null) 1f else 0.95f, label = "center")
        Box(Modifier.size(innerDiameter * centerScale), contentAlignment = Alignment.Center) {
            if (selected != null) {
                SelectedSliceCenter(selected)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.this_month_balance).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(formatMoney(monthBalance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SelectedSliceCenter(slice: CategorySlice) {
    val color = (slice.category?.color ?: CategoryColor.GRAY).toColor()
    val icon = slice.category?.let { iconFor(it.systemImage) } ?: Icons.Outlined.QuestionMark
    Box(Modifier.fillMaxSize().clip(CircleShape).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.2f), modifier = Modifier.fillMaxSize().padding(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                slice.category?.name ?: stringResource(R.string.uncategorized),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
            )
            Text(formatMoney(slice.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    innerRadius: Float,
    outerRadius: Float,
) {
    val width = outerRadius - innerRadius
    val mid = innerRadius + width / 2f
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - mid, center.y - mid),
        size = Size(mid * 2f, mid * 2f),
        style = Stroke(width = width),
    )
}

/** Angle of a point relative to the center, in radians clockwise from 12 o'clock, as the ring is drawn. */
private fun clockwiseAngleFromTop(dx: Float, dy: Float): Double {
    val angle = atan2(dx.toDouble(), -dy.toDouble())
    return if (angle < 0) angle + 2 * Math.PI else angle
}

private fun sliceAt(slices: List<CategorySlice>, angle: Double): CategorySlice? {
    val total = slices.sumOf { it.amount.toDouble() }
    if (total <= 0.0) return null
    val value = angle / (2 * Math.PI) * total
    var cumulative = 0.0
    for (slice in slices) {
        cumulative += slice.amount.toDouble()
        if (value <= cumulative) return slice
    }
    return null
}
