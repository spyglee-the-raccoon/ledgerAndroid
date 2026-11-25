package work.spyglee.ladger.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.ZoomState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.scale
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import work.spyglee.ladger.BuildConfig
import work.spyglee.ladger.R

/** Longest side of the photo handed to OCR; plenty for receipt text, and keeps the bitmap around 30 MB. */
private const val MAX_PHOTO_SIDE = 3_000

/** How long the focus ring stays where the user tapped. */
private const val FOCUS_RING_MILLIS = 1_500L

/** JPEG quality of a kept page until it's read: close to the photo, at a tenth of the memory. */
private const val PAGE_JPEG_QUALITY = 95

/** Longest side of a page's thumbnail in the strip. */
private const val THUMBNAIL_SIDE = 200

/**
 * Our own camera screen for receipts (no Play services document scanner, so photos never leave the
 * app). After taking a photo the user reviews it, then adds another page or finishes, and
 * [onPagesConfirmed] receives the pages in order (JPEG), like confirming a scan in iOS's document
 * camera (decision D9). Pages taken so far show as thumbnails and can be removed.
 */
@Composable
fun ReceiptCameraScreen(onPagesConfirmed: (List<ByteArray>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            // Pinch and tap are handled in Compose below, so the zoom buttons and focus ring follow them.
            isPinchToZoomEnabled = false
            isTapToFocusEnabled = false
        }
    }
    val previewView = remember { PreviewView(context).apply { this.controller = controller } }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    var zoom by remember { mutableStateOf<ZoomState?>(null) }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        val zoomObserver = Observer<ZoomState> { zoom = it }
        controller.zoomState.observe(lifecycleOwner, zoomObserver)
        onDispose {
            controller.zoomState.removeObserver(zoomObserver)
            controller.unbind()
            captureExecutor.shutdown()
        }
    }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var pages by remember { mutableStateOf(listOf<CapturedPage>()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(FOCUS_RING_MILLIS)
            focusPoint = null
        }
    }

    // Leaving with pages taken asks first, as iOS's document camera does.
    val close = { if (pages.isEmpty()) onClose() else confirmDiscard = true }
    BackHandler { if (photo != null) photo = null else close() }
    val finish = { onPagesConfirmed(pages.map { it.jpeg }) }
    // Keeps the photo under review as a page, then takes another or finishes.
    val keep: (Bitmap, Boolean) -> Unit = { current, done ->
        isCapturing = true
        scope.launch {
            pages = pages + current.toCapturedPage()
            isCapturing = false
            photo = null
            if (done) finish()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val current = photo
        if (current == null) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            zoom?.let { controller.setZoomRatio((it.zoomRatio * zoomChange).coerceIn(it.minZoomRatio, it.maxZoomRatio)) }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            focusPoint = tap
                            val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                            controller.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                            )
                        }
                    },
            ) {
                focusPoint?.let { FocusRing(it) }
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp).align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
                if (pages.isNotEmpty()) {
                    Button(onClick = finish, enabled = !isCapturing) { Text(stringResource(R.string.done)) }
                }
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (pages.isNotEmpty()) PageStrip(pages, onRemove = { index -> pages = pages.filterIndexed { i, _ -> i != index } })
                zoom?.let { ZoomButtons(it, onZoom = { ratio -> controller.setZoomRatio(ratio) }) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp))
                    ShutterButton(enabled = !isCapturing && pages.size < ReceiptTextRecognizer.MAX_PAGES) {
                        isCapturing = true
                        scope.launch {
                            photo = try {
                                controller.capturePhoto(captureExecutor)
                            } catch (_: ImageCaptureException) {
                                null
                            }
                            isCapturing = false
                        }
                    }
                    IconButton(onClick = {
                        torchOn = !torchOn
                        controller.enableTorch(torchOn)
                    }) {
                        Icon(
                            if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = stringResource(R.string.flash),
                            tint = Color.White,
                        )
                    }
                }
            }
            if (isCapturing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        } else {
            Image(current.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            // With pages taken already, says which page this is.
            if (pages.isNotEmpty()) {
                Text(
                    stringResource(R.string.receipt_page, pages.size + 1),
                    color = Color.White,
                    modifier = Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopCenter),
                )
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                OutlinedButton(onClick = { photo = null }, enabled = !isCapturing) {
                    Text(stringResource(R.string.retake_photo), color = Color.White)
                }
                // This page and one more must fit within the limit.
                if (pages.size + 1 < ReceiptTextRecognizer.MAX_PAGES) {
                    OutlinedButton(onClick = { keep(current, false) }, enabled = !isCapturing) {
                        Text(stringResource(R.string.add_page), color = Color.White)
                    }
                }
                Button(onClick = { keep(current, true) }, enabled = !isCapturing) { Text(stringResource(R.string.done)) }
            }
            if (isCapturing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.discard_pages)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A page the user kept: the photo as JPEG until it's read, and a small copy for the strip. */
private class CapturedPage(val jpeg: ByteArray, val thumbnail: ImageBitmap)

private suspend fun Bitmap.toCapturedPage(): CapturedPage = withContext(Dispatchers.Default) {
    val jpeg = ByteArrayOutputStream().use {
        compress(Bitmap.CompressFormat.JPEG, PAGE_JPEG_QUALITY, it)
        it.toByteArray()
    }
    val ratio = THUMBNAIL_SIDE.toFloat() / max(width, height)
    val thumbnail = scale((width * ratio).roundToInt().coerceAtLeast(1), (height * ratio).roundToInt().coerceAtLeast(1))
    CapturedPage(jpeg, thumbnail.asImageBitmap())
}

/** The pages taken so far, in order, each with a button to remove it. */
@Composable
private fun PageStrip(pages: List<CapturedPage>, onRemove: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pages.forEachIndexed { index, page ->
            Box {
                Image(
                    page.thumbnail,
                    contentDescription = stringResource(R.string.receipt_page, index + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 8.dp, end = 8.dp)
                        .size(width = 44.dp, height = 60.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(6.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { onRemove(index) }
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_page, index + 1),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** 0.6× / 1× / 2× / 5× buttons; the active one shows the exact zoom, e.g. "1,4×" while pinching. */
@Composable
private fun ZoomButtons(state: ZoomState, onZoom: (Float) -> Unit) {
    val presets = CameraZoom.presets(state.minZoomRatio, state.maxZoomRatio)
    val active = CameraZoom.activeIndex(presets, state.zoomRatio)
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        presets.forEachIndexed { index, preset ->
            val isActive = index == active
            val label = CameraZoom.label(if (isActive) state.zoomRatio else preset)
            val description = stringResource(R.string.zoom_level, CameraZoom.label(preset))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onZoom(preset) }
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        selected = isActive
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isActive) Color(0xFFFFD60A) else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** A ring where the user tapped to focus. */
@Composable
private fun FocusRing(center: Offset) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.White, radius = 36.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.take_photo)
    Box(
        Modifier
            .size(72.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.Gray)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    )
}

/**
 * Takes a photo of exactly what the preview showed, upright, scaled down to at most [MAX_PHOTO_SIDE]
 * pixels on its longest side.
 *
 * The preview fills the screen by cropping the camera's 4:3 frame, and CameraX marks that visible
 * part as the image's crop rect, but `toBitmap()` ignores it. Without applying it the photo would show
 * far more than the user framed, as if taken from further away.
 */
private suspend fun LifecycleCameraController.capturePhoto(executor: java.util.concurrent.Executor): Bitmap {
    val image = suspendCancellableCoroutine<ImageProxy> { continuation ->
        takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                continuation.resume(image)
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resumeWithException(exception)
            }
        })
    }
    return withContext(Dispatchers.Default) {
        image.use { proxy ->
            val bitmap = proxy.toBitmap()
            val crop = Rect(proxy.cropRect).apply { if (!intersect(0, 0, bitmap.width, bitmap.height)) set(0, 0, bitmap.width, bitmap.height) }
            if (BuildConfig.DEBUG) {
                Log.d("ReceiptCamera", "Captured ${bitmap.width}x${bitmap.height}, crop $crop, rotation ${proxy.imageInfo.rotationDegrees}")
            }
            val scale = minOf(1f, MAX_PHOTO_SIDE.toFloat() / max(crop.width(), crop.height()))
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height(), matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }
}
