package com.example.ui.workspace

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.mse.MseCardView
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseStyleSpec
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.ui.CardRenderState
import com.example.ui.editor.ArtCropDialog
import com.example.ui.editor.CardFieldEditTarget
import com.example.ui.editor.FieldEditorBottomSheet
import com.example.ui.editor.PhysicalScaleMath
import kotlin.math.roundToInt

/**
 * Redesigned MSE `Cards` Tab:
 * - Zero overlapping badges, banners, or raw debug placeholders stamped over the card.
 * - Bridge ONLINE: Exact MSE PNG from `POST /sets/render` scaled by hardware xdpi/ydpi × calibration + invisible aligned hitboxes.
 * - Bridge OFFLINE: Clean, modern, non-overlapping structured card layout inside 63:88 (or 59:86) container with strictly empty art (`image == null`).
 * - Slim 36.dp Scale & Zoom bar cleanly BELOW the card.
 * - Compact non-clipping bottom MSE Card Bar (`<` | `Card 1 / N` | `>` | `+ New` | Art).
 */
@Composable
fun CardsTabContent(
    settings: StudioSettings,
    archive: MseSetArchive,
    activeCardIndex: Int,
    styleSpec: MseStyleSpec,
    renderState: CardRenderState,
    showCalibrationSheet: Boolean,
    onDismissCalibrationSheet: () -> Unit,
    onOpenCalibrationSheet: () -> Unit,
    onSelectCard: (Int) -> Unit,
    onAddBlankCard: () -> Unit,
    onDuplicateCard: (Int) -> Unit,
    onDeleteCard: (Int) -> Unit,
    onUpdateCardFields: (Map<String, String?>) -> Unit,
    onEmbedCroppedArt: (ByteArray) -> Unit,
    onClearCardArt: () -> Unit,
    onUpdateCalibrationScale: (Float) -> Unit,
    onToggleCalibrationLock: (Boolean) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics

    val cards = archive.document.cards
    val activeCard = cards.getOrNull(activeCardIndex)
    val embeddedArtBytes = archive.getArtBytes(activeCardIndex)

    val physicalDim = remember(
        displayMetrics.xdpi,
        displayMetrics.ydpi,
        styleSpec,
        settings.physicalProfile,
        settings.calibrationScale,
    ) {
        PhysicalScaleMath.computeIrlCardPixels(
            displayMetrics = displayMetrics,
            styleSpec = styleSpec,
            profile = settings.physicalProfile,
            calibrationScale = settings.calibrationScale,
        )
    }

    val cardWidthDp = with(density) { physicalDim.widthPx.toDp() }
    val cardHeightDp = with(density) { physicalDim.heightPx.toDp() }
    val cardAspectRatio = (physicalDim.widthMm / physicalDim.heightMm).coerceIn(0.55f, 0.85f)

    var viewportScale by remember { mutableFloatStateOf(1.0f) }
    var viewportOffset by remember { mutableStateOf(Offset.Zero) }

    var editTarget by remember { mutableStateOf<CardFieldEditTarget?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showCardTableSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) pendingCropUri = uri
    }

    val launchArtPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // ---- Center Card Stage (Clean Dark-Neutral Backdrop, ZERO Overlapping HUD Pills) ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        viewportScale = (viewportScale * zoom).coerceIn(0.6f, 2.8f)
                        viewportOffset = Offset(
                            x = (viewportOffset.x + pan.x).coerceIn(-900f, 900f),
                            y = (viewportOffset.y + pan.y).coerceIn(-900f, 900f),
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("irl_card_stage"),
            contentAlignment = Alignment.Center,
        ) {
            when (renderState) {
                is CardRenderState.Rendered -> {
                    // ONLINE: Exact MSE Bridge PNG sized at real hardware 1:1 IRL × calibration × zoom
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = viewportScale
                                scaleY = viewportScale
                                translationX = viewportOffset.x
                                translationY = viewportOffset.y
                            }
                            .width(cardWidthDp)
                            .height(cardHeightDp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .testTag("online_mse_card_surface"),
                    ) {
                        val imageBitmap = remember(renderState.bitmap) {
                            renderState.bitmap.asImageBitmap()
                        }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Rendered card image",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                        InvisibleAlignedHitboxes(
                            styleSpec = styleSpec,
                            card = activeCard,
                            onTapField = { target -> editTarget = target },
                            onTapArt = launchArtPicker,
                        )
                    }
                }

                is CardRenderState.Rendering -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp)
                        Text(
                            text = "Rendering card…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is CardRenderState.BridgeOffline,
                is CardRenderState.Error,
                is CardRenderState.Idle -> {
                    // OFFLINE: Clean, modern, non-overlapping structured card layout inside crisp 63:88 container.
                    // Strictly obeys PROJECT_SPEC.md: NEVER draws fake MSE frames or default card art.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Fit cleanly within stage at 100% zoom, while honoring pinch-zoom & calibration ratio
                        val stageFitModifier = if (maxWidth / maxHeight > cardAspectRatio) {
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(cardAspectRatio)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(cardAspectRatio)
                        }

                        StructuredOfflineCardLayout(
                            card = activeCard,
                            setCode = archive.document.setInfo.setCode,
                            embeddedArtBytes = embeddedArtBytes,
                            onEditName = {
                                editTarget = CardFieldEditTarget.SingleField("name", activeCard?.name.orEmpty())
                            },
                            onEditCost = {
                                editTarget = CardFieldEditTarget.SingleField("casting cost", activeCard?.castingCost.orEmpty())
                            },
                            onTapArt = launchArtPicker,
                            onClearArt = onClearCardArt,
                            onEditTypeLine = {
                                editTarget = CardFieldEditTarget.TypeLine(
                                    superType = activeCard?.superType.orEmpty(),
                                    subType = activeCard?.subType.orEmpty(),
                                )
                            },
                            onEditRules = {
                                editTarget = CardFieldEditTarget.SingleField("rule text", activeCard?.ruleText.orEmpty())
                            },
                            onEditFlavor = {
                                editTarget = CardFieldEditTarget.SingleField("flavor text", activeCard?.flavorText.orEmpty())
                            },
                            onEditIllustrator = {
                                editTarget = CardFieldEditTarget.SingleField("illustrator", activeCard?.illustrator.orEmpty())
                            },
                            onEditPowerToughness = {
                                editTarget = CardFieldEditTarget.PowerToughness(
                                    power = activeCard?.power.orEmpty(),
                                    toughness = activeCard?.toughness.orEmpty(),
                                )
                            },
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = viewportScale * settings.calibrationScale
                                    scaleY = viewportScale * settings.calibrationScale
                                    translationX = viewportOffset.x
                                    translationY = viewportOffset.y
                                }
                                .then(stageFitModifier)
                                .testTag("structured_card_container"),
                        )
                    }
                }
            }
        }

        // ---- Slim Card Scale & Zoom Bar (height = 36.dp, Cleanly BELOW Card) ----
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${"%.1f".format(physicalDim.widthMm)} × ${"%.1f".format(physicalDim.heightMm)} mm • ${(viewportScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TextButton(
                            onClick = {
                                viewportScale = 1.0f
                                viewportOffset = Offset.Zero
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("snap_irl_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.snap_irl_short),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        IconButton(
                            onClick = onOpenCalibrationSheet,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("scale_bar_calibrate_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Calibrate 1:1 IRL size",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // ---- Clean MSE Bottom Card Bar (Fits 360dp with ZERO horizontal clipping) ----
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left/Center: < | Card 1 / N (tap for Card List Table) | >
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        IconButton(
                            onClick = { onSelectCard(activeCardIndex - 1) },
                            enabled = activeCardIndex > 0,
                            modifier = Modifier.testTag("bottom_prev_card"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous card",
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCardTableSheet = true }
                                .testTag("bottom_card_table_trigger"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableRows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Card ${if (cards.isEmpty()) 0 else activeCardIndex + 1} / ${cards.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        IconButton(
                            onClick = { onSelectCard(activeCardIndex + 1) },
                            enabled = activeCardIndex < cards.lastIndex,
                            modifier = Modifier.testTag("bottom_next_card"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next card",
                            )
                        }
                    }

                    // Right: Compact + New button and Art icon button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onAddBlankCard,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("bottom_new_card_button"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.new_card_short),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }

                        IconButton(
                            onClick = launchArtPicker,
                            modifier = Modifier.testTag("bottom_art_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add or change card artwork",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Field Editor Bottom Sheet ----
    editTarget?.let { target ->
        FieldEditorBottomSheet(
            target = target,
            onDismiss = { editTarget = null },
            onSaveFields = { updates ->
                onUpdateCardFields(updates)
                editTarget = null
            },
        )
    }

    // ---- Aspect-Locked Artwork Cropper ----
    pendingCropUri?.let { uri ->
        val artBox = styleSpec.artHitbox
        ArtCropDialog(
            imageUri = uri,
            targetAspectRatio = styleSpec.artAspectRatio,
            targetWidthPx = (artBox?.width?.roundToInt() ?: 316).coerceAtLeast(316) * 2,
            targetHeightPx = (artBox?.height?.roundToInt() ?: 231).coerceAtLeast(231) * 2,
            onDismiss = { pendingCropUri = null },
            onCroppedBytesReady = { bytes ->
                onEmbedCroppedArt(bytes)
                pendingCropUri = null
            },
        )
    }

    // ---- Expandable MSE Card List Table Bottom Sheet ----
    if (showCardTableSheet) {
        CardTableBottomSheet(
            cards = cards,
            activeCardIndex = activeCardIndex,
            onSelectCard = onSelectCard,
            onAddBlankCard = onAddBlankCard,
            onDuplicateCard = onDuplicateCard,
            onDeleteCard = onDeleteCard,
            onDismiss = { showCardTableSheet = false },
        )
    }

    // ---- Physical Card Calibration Bottom Sheet ----
    if (showCalibrationSheet) {
        CalibrationBottomSheet(
            settings = settings,
            physicalDim = physicalDim,
            onUpdateCalibrationScale = onUpdateCalibrationScale,
            onToggleCalibrationLock = onToggleCalibrationLock,
            onUpdatePhysicalProfile = onUpdatePhysicalProfile,
            onResetCalibration = { onUpdateCalibrationScale(1.0f) },
            onDismiss = onDismissCalibrationSheet,
        )
    }
}

/**
 * Clean, modern, strictly non-overlapping structured card layout used when the local MSE Bridge is offline.
 * Never fakes MSE frame graphics and never populates default card artwork (`image` remains strictly null).
 */
@Composable
private fun StructuredOfflineCardLayout(
    card: MseCardView?,
    setCode: String,
    embeddedArtBytes: ByteArray?,
    onEditName: () -> Unit,
    onEditCost: () -> Unit,
    onTapArt: () -> Unit,
    onClearArt: () -> Unit,
    onEditTypeLine: () -> Unit,
    onEditRules: () -> Unit,
    onEditFlavor: () -> Unit,
    onEditIllustrator: () -> Unit,
    onEditPowerToughness: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Header Row (Non-overlapping: Card Name + Mana/Cost)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditName)
                        .testTag("card_field_name"),
                ) {
                    val nameText = card?.name.orEmpty()
                    Text(
                        text = nameText.ifBlank { "Card Name" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (nameText.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(min = 64.dp, max = 110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditCost)
                        .testTag("card_field_cost"),
                ) {
                    val costText = card?.castingCost.orEmpty()
                    Text(
                        text = costText.ifBlank { "Cost" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (costText.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            // 2. Artwork Box (Clean dashed dropzone when image == null; strictly NO default art)
            val userArtBitmap = remember(embeddedArtBytes) {
                embeddedArtBytes?.let { bytes ->
                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                }
            }
            val dashColor = MaterialTheme.colorScheme.outline

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable(onClick = onTapArt)
                    .testTag("card_field_artwork"),
                contentAlignment = Alignment.Center,
            ) {
                if (userArtBitmap != null) {
                    Image(
                        bitmap = userArtBitmap.asImageBitmap(),
                        contentDescription = "Card artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        ) {
                            IconButton(
                                onClick = onTapArt,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Replace artwork",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        ) {
                            IconButton(
                                onClick = onClearArt,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("card_clear_art_button"),
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Clear artwork",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = dashColor,
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                            style = Stroke(
                                width = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                            ),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = stringResource(R.string.tap_to_add_artwork),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 3. Type Line Row (Single clean tappable row, zero clipping)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditTypeLine)
                    .testTag("card_field_type_line"),
            ) {
                val typeText = card?.fullTypeLine.orEmpty()
                Text(
                    text = typeText.ifBlank { "Card Type — Subtype" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (typeText.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }

            // 4. Rules & Flavor Box (Separated by thin divider; NEVER overlapped by P/T)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val rulesText = card?.ruleText.orEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.35f)
                            .clickable(onClick = onEditRules)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("card_field_rules"),
                    ) {
                        Text(
                            text = rulesText.ifBlank { "Tap to add rules text" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rulesText.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )

                    val flavorText = card?.flavorText.orEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.65f)
                            .clickable(onClick = onEditFlavor)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("card_field_flavor"),
                    ) {
                        Text(
                            text = flavorText.ifBlank { "Flavor text" },
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 5. Footer Row (Separate dedicated row cleanly below Rules/Flavor: Illustrator + P/T pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val illustrator = card?.illustrator.orEmpty()
                val footerLabel = buildString {
                    if (setCode.isNotBlank()) {
                        append(setCode)
                        if (illustrator.isNotBlank()) append(" • ")
                    }
                    if (illustrator.isNotBlank()) {
                        append(illustrator)
                    } else if (setCode.isBlank()) {
                        append("Illustrator")
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onEditIllustrator)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("card_field_illustrator"),
                ) {
                    Text(
                        text = footerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (illustrator.isBlank() && setCode.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val ptText = card?.ptSummary.orEmpty()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditPowerToughness)
                        .testTag("card_field_pt"),
                ) {
                    Text(
                        text = ptText.ifBlank { "P / T" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (ptText.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/**
 * Transparent tap hitboxes aligned proportionally over the MSE Bridge-rendered PNG.
 */
@Composable
private fun InvisibleAlignedHitboxes(
    styleSpec: MseStyleSpec,
    card: MseCardView?,
    onTapField: (CardFieldEditTarget) -> Unit,
    onTapArt: () -> Unit,
) {
    val cardW = styleSpec.cardWidthPx.coerceAtLeast(1).toFloat()
    val cardH = styleSpec.cardHeightPx.coerceAtLeast(1).toFloat()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        styleSpec.hitboxes.forEach { hitbox ->
            val leftPx = (hitbox.left / cardW) * containerW
            val topPx = (hitbox.top / cardH) * containerH
            val widthDp = with(density) { ((hitbox.width / cardW) * containerW).toDp() }
            val heightDp = with(density) { ((hitbox.height / cardH) * containerH).toDp() }
            val isArtBox = hitbox.field == "image" || hitbox.field == "art"

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .width(widthDp.coerceAtLeast(24.dp))
                    .height(heightDp.coerceAtLeast(18.dp))
                    .clickable {
                        when {
                            isArtBox -> onTapArt()
                            hitbox.field == "super type" || hitbox.field == "sub type" -> {
                                onTapField(
                                    CardFieldEditTarget.TypeLine(
                                        superType = card?.superType.orEmpty(),
                                        subType = card?.subType.orEmpty(),
                                    )
                                )
                            }

                            hitbox.field == "power" || hitbox.field == "toughness" -> {
                                onTapField(
                                    CardFieldEditTarget.PowerToughness(
                                        power = card?.power.orEmpty(),
                                        toughness = card?.toughness.orEmpty(),
                                    )
                                )
                            }

                            else -> {
                                onTapField(
                                    CardFieldEditTarget.SingleField(
                                        fieldKey = hitbox.field,
                                        initialValue = card?.field(hitbox.field).orEmpty(),
                                    )
                                )
                            }
                        }
                    }
                    .testTag("hitbox_${hitbox.field.replace(' ', '_')}"),
            )
        }
    }
}
