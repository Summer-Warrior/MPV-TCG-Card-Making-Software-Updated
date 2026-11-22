package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Aspect-ratio-locked image cropper matching the active `.mse-style` art hitbox ratio.
 * Decodes the user-selected photo via Android Photo Picker URI and produces exact cropped PNG bytes
 * to embed inside the `.mse-set` ZIP archive.
 */
@Composable
fun ArtCropDialog(
    imageUri: Uri,
    targetAspectRatio: Float,
    targetWidthPx: Int,
    targetHeightPx: Int,
    onDismiss: () -> Unit,
    onCroppedBytesReady: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, optsBounds)
                }
                val maxDim = max(optsBounds.outWidth, optsBounds.outHeight)
                val sampleSize = if (maxDim > 2048) (maxDim / 2048).coerceAtLeast(1) else 1
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: throw IllegalStateException("Could not decode selected image.")
            }.onSuccess { bmp ->
                sourceBitmap = bmp
            }.onFailure { err ->
                decodeError = err.message ?: "Unable to read image."
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.crop_art_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Locked aspect ${"%.2f".format(targetAspectRatio)}:1 (${targetWidthPx}×${targetHeightPx} px box)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("crop_close_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close cropper")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(targetAspectRatio.coerceIn(0.4f, 2.5f))
                        .clipToBounds()
                        .background(Color.Black)
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(sourceBitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                zoom = newZoom
                                offset = Offset(offset.x + pan.x, offset.y + pan.y)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        decodeError != null -> {
                            Text(
                                text = decodeError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }

                        sourceBitmap == null -> {
                            CircularProgressIndicator()
                        }

                        else -> {
                            val bmp = sourceBitmap!!
                            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val baseScale = max(
                                    size.width / bmp.width.toFloat(),
                                    size.height / bmp.height.toFloat(),
                                )
                                val drawW = bmp.width * baseScale * zoom
                                val drawH = bmp.height * baseScale * zoom
                                val maxOffsetX = ((drawW - size.width) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = ((drawH - size.height) / 2f).coerceAtLeast(0f)
                                val clampedX = offset.x.coerceIn(-maxOffsetX, maxOffsetX)
                                val clampedY = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                val left = (size.width - drawW) / 2f + clampedX
                                val top = (size.height - drawH) / 2f + clampedY

                                drawImage(
                                    image = imageBitmap,
                                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                    dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                                )

                                // Rule-of-thirds drafting guides
                                val guideColor = Color.White.copy(alpha = 0.25f)
                                drawLine(guideColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1f)
                                drawLine(guideColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1f)
                                drawLine(guideColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1f)
                                drawLine(guideColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1f)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Zoom ${"%.1f".format(zoom)}×",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(68.dp),
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1f..5f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("crop_zoom_slider"),
                    )
                    IconButton(
                        onClick = {
                            zoom = 1f
                            offset = Offset.Zero
                        },
                        modifier = Modifier.testTag("crop_reset_button"),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset crop")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("crop_cancel_button"),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        enabled = sourceBitmap != null && !isProcessing && viewportSize.width > 0,
                        onClick = {
                            val bmp = sourceBitmap ?: return@Button
                            isProcessing = true
                            scope.launch {
                                val bytes = withContext(Dispatchers.Default) {
                                    cropToAspectBytes(
                                        bitmap = bmp,
                                        viewportW = viewportSize.width.toFloat(),
                                        viewportH = viewportSize.height.toFloat(),
                                        zoom = zoom,
                                        offset = offset,
                                        outWidth = targetWidthPx.coerceIn(256, 1200),
                                        outHeight = targetHeightPx.coerceIn(256, 1200),
                                    )
                                }
                                isProcessing = false
                                onCroppedBytesReady(bytes)
                            }
                        },
                        modifier = Modifier.testTag("crop_apply_button"),
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.apply_crop))
                        }
                    }
                }
            }
        }
    }
}

private fun cropToAspectBytes(
    bitmap: Bitmap,
    viewportW: Float,
    viewportH: Float,
    zoom: Float,
    offset: Offset,
    outWidth: Int,
    outHeight: Int,
): ByteArray {
    val baseScale = max(viewportW / bitmap.width.toFloat(), viewportH / bitmap.height.toFloat())
    val totalScale = baseScale * zoom
    val drawW = bitmap.width * totalScale
    val drawH = bitmap.height * totalScale
    val maxOffsetX = ((drawW - viewportW) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((drawH - viewportH) / 2f).coerceAtLeast(0f)
    val clampedX = offset.x.coerceIn(-maxOffsetX, maxOffsetX)
    val clampedY = offset.y.coerceIn(-maxOffsetY, maxOffsetY)

    val left = (viewportW - drawW) / 2f + clampedX
    val top = (viewportH - drawH) / 2f + clampedY

    val srcLeft = ((-left) / totalScale).roundToInt().coerceIn(0, bitmap.width - 1)
    val srcTop = ((-top) / totalScale).roundToInt().coerceIn(0, bitmap.height - 1)
    val srcW = (viewportW / totalScale).roundToInt().coerceIn(1, bitmap.width - srcLeft)
    val srcH = (viewportH / totalScale).roundToInt().coerceIn(1, bitmap.height - srcTop)

    val cropped = Bitmap.createBitmap(bitmap, srcLeft, srcTop, srcW, srcH)
    val scaled = Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true)
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
    if (cropped !== bitmap) cropped.recycle()
    if (scaled !== cropped) scaled.recycle()
    return baos.toByteArray()
}
