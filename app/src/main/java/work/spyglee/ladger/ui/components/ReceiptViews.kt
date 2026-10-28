package work.spyglee.ladger.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.spyglee.ladger.R
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.formatNumber

/** A receipt page: saved with a transaction, or just scanned and not saved yet. */
sealed interface ReceiptPage {
    data class Saved(val file: File) : ReceiptPage

    class Scanned(val jpeg: ByteArray) : ReceiptPage

    /** Decodes the page at most about [maxSide] px on its longest side, to keep thumbnails small. */
    fun decode(maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) sampleSize *= 2
        return decode(BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private fun decode(options: BitmapFactory.Options): Bitmap? = when (this) {
        is Saved -> BitmapFactory.decodeFile(file.path, options)
        is Scanned -> BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
    }
}

@Composable
private fun rememberPageImage(page: ReceiptPage, maxSide: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, page, maxSide) {
        value = withContext(Dispatchers.IO) { page.decode(maxSide)?.asImageBitmap() }
    }
    return image
}

/** A horizontal strip of receipt page thumbnails; tapping one opens the full-screen viewer, as on iOS. */
@Composable
fun ReceiptPhotos(pages: List<ReceiptPage>, modifier: Modifier = Modifier) {
    var openPage by remember { mutableStateOf<Int?>(null) }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pages.forEachIndexed { index, page ->
            val label = stringResource(R.string.receipt_page, index + 1)
            Box(
                Modifier
                    .size(width = 80.dp, height = 110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClickLabel = label) { openPage = index },
            ) {
                rememberPageImage(page, THUMBNAIL_SIDE)?.let {
                    Image(it, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
    openPage?.let { ReceiptPhotoViewer(pages, initialPage = it, onDismiss = { openPage = null }) }
}

/** Full-screen, pageable receipt viewer with pinch and double-tap zoom. */
@Composable
private fun ReceiptPhotoViewer(pages: List<ReceiptPage>, initialPage: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pagerState = rememberPagerState(initialPage) { pages.size }
            HorizontalPager(pagerState, Modifier.fillMaxSize()) { index ->
                ZoomableImage(pages[index], stringResource(R.string.receipt_page, index + 1))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.safeDrawingPadding().align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(page: ReceiptPage, description: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val image = rememberPageImage(page, FULL_SIDE) ?: return
    Image(
        image,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Pinch zooms and a zoomed image pans; otherwise the drag is left to the pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        if (zoom != 1f || scale > 1f) {
                            scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                    offset = Offset.Zero
                })
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** One recognized product line: name, quantity × unit price when more than one, and the line total. */
@Composable
fun ReceiptItemRow(name: String, quantity: BigDecimal, unitPrice: BigDecimal, totalPrice: BigDecimal, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name)
            if (quantity.compareTo(BigDecimal.ONE) != 0) {
                Text(
                    "${formatNumber(quantity)} × ${formatMoney(unitPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(formatMoney(totalPrice), fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
    }
}

private const val THUMBNAIL_SIDE = 330
private const val FULL_SIDE = 2_048
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
