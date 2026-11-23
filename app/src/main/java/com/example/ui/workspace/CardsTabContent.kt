package com.example.ui.workspace

import android.graphics.BitmapFactory
import android.graphics.Typeface
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.R
import com.example.data.mse.DiscoveredStyle
import com.example.data.mse.FrameVariantOption
import com.example.data.mse.MseCardView
import com.example.data.mse.MseHitbox
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.ResolvedOfflineFrame
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.ui.CardRenderState
import com.example.ui.editor.ArtCropDialog
import com.example.ui.editor.CardFieldEditTarget
import com.example.ui.editor.FieldEditorBottomSheet
import com.example.ui.editor.PhysicalScaleMath
import java.io.File
import kotlin.math.roundToInt

/**
 * Authentic MSE `Cards` Tab:
 * - Soft radial drafting-studio tabletop vignette behind the card + soft 14.dp drop shadow.
 * - Bridge ONLINE: 100% pixel-exact PNG from `POST /sets/render` at calibrated 1:1 IRL dimensions
 *   with invisible aligned `.mse-style` tap hitboxes.
 * - Bridge OFFLINE: Real Upstream Base-Frame Compositor displaying the actual blank upstream
 *   `.mse-style` frame PNG/JPG from `MagicSetEditorPacks` with proportional `.mse-style`
 *   coordinate-mapped artwork (`image` strictly `null`/empty until imported) & tappable live text previews.
 *   Safely falls back to non-overlapping structured card layout ONLY if upstream frame or coordinates are unresolvable.
 * - Compact Frame Color / Variant Selector + subtle status caption cleanly BELOW the card.
 * - Slim scale/zoom bar with active style mini thumbnail (tap to jump to Style tab).
 * - Comfortable 14.dp horizontal padding on bottom card bar (`< Card 1 / N > | + New | Art`).
 */
@Composable
fun CardsTabContent(
    settings: StudioSettings,
    archive: MseSetArchive,
    activeCardIndex: Int,
    styleSpec: MseStyleSpec,
    activeDiscoveredStyle: DiscoveredStyle?,
    renderState: CardRenderState,
    offlineFrame: ResolvedOfflineFrame?,
    localSelectedFontFile: File?,
    showCalibrationSheet: Boolean,
    onDismissCalibrationSheet: () -> Unit,
    onOpenCalibrationSheet: () -> Unit,
    onJumpToStyleTab: () -> Unit,
    onSelectOfflineFrameVariant: (String) -> Unit,
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

    val customCardFontFamily = remember(localSelectedFontFile?.absolutePath, localSelectedFontFile?.lastModified()) {
        localSelectedFontFile?.let { file ->
            runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull()
        }
    }

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
    val cardAspectRatio = if (styleSpec.cardWidthPx > 0 && styleSpec.cardHeightPx > 0) {
        (styleSpec.cardWidthPx.toFloat() / styleSpec.cardHeightPx.toFloat()).coerceIn(0.55f, 0.85f)
    } else {
        (physicalDim.widthMm / physicalDim.heightMm).coerceIn(0.55f, 0.85f)
    }

    var viewportScale by remember { mutableFloatStateOf(1.0f) }
    var viewportOffset by remember { mutableStateOf(Offset.Zero) }

    var editTarget by remember { mutableStateOf<CardFieldEditTarget?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showCardTableSheet by remember { mutableStateOf(false) }

    val activeVariantUrl = offlineFrame?.activeVariant?.imageUrl
    var frameLoadFailedForUrl by remember(activeVariantUrl) { mutableStateOf(false) }

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

    val isOnlineRendered = renderState is CardRenderState.Rendered
    val canShowUpstreamBaseFrame = !isOnlineRendered &&
        !activeVariantUrl.isNullOrBlank() &&
        !frameLoadFailedForUrl &&
        styleSpec.hasValidCompositeGeometry()

    val stageVignette = Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.52f),
            MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    )

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // ---- Center Card Stage (Soft Tabletop Vignette, 14.dp Card Shadow, ZERO Floating Badges Over Card) ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(stageVignette)
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("irl_card_stage"),
            contentAlignment = Alignment.Center,
        ) {
            when (renderState) {
                is CardRenderState.Rendered -> {
                    // ONLINE: 100% pixel-exact MSE Bridge PNG at calibrated 1:1 IRL size + invisible hitboxes
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
                            .shadow(14.dp, RoundedCornerShape(12.dp), clip = false)
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
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val stageFitModifier = if (maxWidth / maxHeight > cardAspectRatio) {
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(cardAspectRatio)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(cardAspectRatio)
                        }

                        val cardTransformModifier = Modifier
                            .graphicsLayer {
                                scaleX = viewportScale * settings.calibrationScale
                                scaleY = viewportScale * settings.calibrationScale
                                translationX = viewportOffset.x
                                translationY = viewportOffset.y
                            }
                            .then(stageFitModifier)
                            .shadow(14.dp, RoundedCornerShape(12.dp), clip = false)

                        if (canShowUpstreamBaseFrame && activeVariantUrl != null) {
                            RealUpstreamBaseFrameCompositor(
                                frameImageUrl = activeVariantUrl,
                                styleSpec = styleSpec,
                                card = activeCard,
                                setCode = archive.document.setInfo.setCode,
                                embeddedArtBytes = embeddedArtBytes,
                                customFontFamily = customCardFontFamily,
                                onFrameImageError = { frameLoadFailedForUrl = true },
                                onTapField = { target -> editTarget = target },
                                onTapArt = launchArtPicker,
                                onClearArt = onClearCardArt,
                                modifier = cardTransformModifier.testTag("offline_upstream_frame_surface"),
                            )
                        } else {
                            // Safe non-overlapping fallback ONLY when upstream frame or coordinates cannot be resolved
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
                                modifier = cardTransformModifier.testTag("structured_card_container"),
                            )
                        }
                    }
                }
            }
        }

        // ---- Compact Frame Color / Variant Selector & Subtle Status Caption (BELOW Card, NEVER Over Card) ----
        if (!isOnlineRendered) {
            val variants = offlineFrame?.variants.orEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 4.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (variants.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        variants.forEach { variant ->
                            val selected = variant.key == offlineFrame?.activeVariant?.key
                            FrameVariantPill(
                                variant = variant,
                                selected = selected,
                                onClick = {
                                    frameLoadFailedForUrl = false
                                    onSelectOfflineFrameVariant(variant.key)
                                },
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.offline_frame_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }

        // ---- Slim Card Scale & Zoom Bar with Active Style Mini Thumbnail (Cleanly BELOW Card) ----
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
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
                    // Mini active style icon + name (tap to jump to Style tab)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onJumpToStyleTab)
                            .padding(vertical = 3.dp)
                            .testTag("scale_bar_style_shortcut"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!activeDiscoveredStyle?.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = activeDiscoveredStyle?.iconUrl,
                                    contentDescription = styleSpec.shortName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Style,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }

                        Text(
                            text = "${styleSpec.shortName} • ${"%.0f".format(physicalDim.widthMm)}×${"%.0f".format(physicalDim.heightMm)}mm • ${(viewportScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

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

        // ---- Clean MSE Bottom Card Bar (14.dp Horizontal Padding — Zero Edge-Touch on 360dp) ----
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        IconButton(
                            onClick = { onSelectCard(activeCardIndex - 1) },
                            enabled = activeCardIndex > 0,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("bottom_prev_card"),
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("bottom_next_card"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next card",
                            )
                        }
                    }

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
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("bottom_art_button"),
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
 * Real Upstream Base-Frame Compositor (Offline Preview):
 * - Displays the ACTUAL blank upstream `.mse-style` frame PNG/JPG from `MagicSetEditorPacks`
 *   (ZERO fake Compose Canvas or SVG card frames).
 * - Maps parsed `.mse-style` bounding boxes proportionally onto the card surface.
 * - Places user artwork strictly inside the parsed `image` box (respecting `z index`, and keeping
 *   the art window strictly empty with a subtle tap hint when `card.image == null`).
 */
@Composable
private fun RealUpstreamBaseFrameCompositor(
    frameImageUrl: String,
    styleSpec: MseStyleSpec,
    card: MseCardView?,
    setCode: String,
    embeddedArtBytes: ByteArray?,
    customFontFamily: FontFamily?,
    onFrameImageError: () -> Unit,
    onTapField: (CardFieldEditTarget) -> Unit,
    onTapArt: () -> Unit,
    onClearArt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardW = styleSpec.cardWidthPx.coerceAtLeast(1).toFloat()
    val cardH = styleSpec.cardHeightPx.coerceAtLeast(1).toFloat()

    val userArtBitmap = remember(embeddedArtBytes) {
        embeddedArtBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    val artBox = styleSpec.artHitbox
    val artBehindFrame = artBox != null && artBox.zIndex <= 0 && frameImageUrl.endsWith(".png", ignoreCase = true)

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14161B)),
    ) {
        val density = LocalDensity.current
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val scaleFactor = containerW / cardW

        // Layer 1 (if PNG cutout places art underneath frame): User Art
        if (artBehindFrame && artBox != null && userArtBitmap != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ((artBox.left / cardW) * containerW).roundToInt(),
                            ((artBox.top / cardH) * containerH).roundToInt(),
                        )
                    }
                    .width(with(density) { ((artBox.width / cardW) * containerW).toDp() })
                    .height(with(density) { ((artBox.height / cardH) * containerH).toDp() }),
            ) {
                Image(
                    bitmap = userArtBitmap.asImageBitmap(),
                    contentDescription = "Card artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Layer 2: Real Upstream `.mse-style` Base-Frame Image (from MagicSetEditorPacks raw CDN via Coil)
        AsyncImage(
            model = frameImageUrl,
            contentDescription = "${styleSpec.shortName} upstream card frame",
            contentScale = ContentScale.FillBounds,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    onFrameImageError()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 3: Proportional `.mse-style` Art Window (interactive + strictly empty when image == null)
        artBox?.let { box ->
            val leftPx = (box.left / cardW) * containerW
            val topPx = (box.top / cardH) * containerH
            val widthDp = with(density) { ((box.width / cardW) * containerW).toDp() }
            val heightDp = with(density) { ((box.height / cardH) * containerH).toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .width(widthDp)
                    .height(heightDp)
                    .clickable(onClick = onTapArt)
                    .testTag("card_field_artwork"),
                contentAlignment = Alignment.Center,
            ) {
                if (userArtBitmap != null && !artBehindFrame) {
                    Image(
                        bitmap = userArtBitmap.asImageBitmap(),
                        contentDescription = "Card artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (userArtBitmap != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                        ) {
                            IconButton(
                                onClick = onTapArt,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Replace artwork",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                        ) {
                            IconButton(
                                onClick = onClearArt,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("card_clear_art_button"),
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Clear artwork",
                                    tint = Color(0xFFFF8A8A),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                } else {
                    // Strictly NO default artwork: subtle transparent tap hint only
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.24f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.tap_to_add_artwork),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }

        // Layer 4: Coordinate-Mapped Tappable Live Text Previews
        val textHitboxes = remember(styleSpec.hitboxes) {
            prepareOverlayTextHitboxes(styleSpec.hitboxes)
        }

        textHitboxes.forEach { hitbox ->
            val leftPx = (hitbox.left / cardW) * containerW
            val topPx = (hitbox.top / cardH) * containerH
            val widthDp = with(density) { ((hitbox.width / cardW) * containerW).toDp() }
            val heightDp = with(density) { ((hitbox.height / cardH) * containerH).toDp() }

            val fieldKey = hitbox.field.lowercase()
            val resolvedValue = resolveOverlayTextValue(fieldKey, card, setCode)
            val placeholder = resolveCleanPlaceholder(fieldKey)
            val isEmpty = resolvedValue.isBlank()
            val displayText = if (isEmpty) placeholder else resolvedValue

            val fontSizeSp = ((hitbox.fontSize ?: defaultFontSizeForField(fieldKey)) * scaleFactor)
                .coerceIn(7.5f, 18f).sp
            val parsedColor = parseHitboxColor(hitbox.fontColorHex)
            val textColor = when {
                isEmpty -> parsedColor.copy(alpha = 0.48f)
                else -> parsedColor
            }
            val textAlign = when {
                hitbox.alignment?.contains("right", ignoreCase = true) == true ||
                    fieldKey == "casting cost" -> TextAlign.End

                hitbox.alignment?.contains("center", ignoreCase = true) == true ||
                    fieldKey == "pt" || fieldKey == "power" || fieldKey == "toughness" -> TextAlign.Center

                else -> TextAlign.Start
            }
            val contentAlignment = when (textAlign) {
                TextAlign.End -> Alignment.CenterEnd
                TextAlign.Center -> Alignment.Center
                else -> if (fieldKey.contains("rule") || fieldKey.contains("text") || fieldKey.contains("flavor")) {
                    Alignment.TopStart
                } else {
                    Alignment.CenterStart
                }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .width(widthDp.coerceAtLeast(24.dp))
                    .height(heightDp.coerceAtLeast(16.dp))
                    .clickable {
                        onTapField(buildEditTargetForHitbox(fieldKey, card))
                    }
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .testTag("hitbox_${fieldKey.replace(' ', '_')}"),
                contentAlignment = contentAlignment,
            ) {
                Text(
                    text = displayText,
                    fontFamily = customFontFamily ?: MaterialTheme.typography.bodyMedium.fontFamily,
                    fontSize = fontSizeSp,
                    lineHeight = (fontSizeSp.value * 1.18f).sp,
                    fontWeight = if (fieldKey == "name" || fieldKey == "pt" || fieldKey == "power" || fieldKey == "toughness") {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    fontStyle = if (fieldKey.contains("flavor")) FontStyle.Italic else FontStyle.Normal,
                    color = textColor,
                    textAlign = textAlign,
                    maxLines = if (fieldKey.contains("rule") || fieldKey.contains("text") || fieldKey.contains("flavor")) {
                        6
                    } else {
                        1
                    },
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FrameVariantPill(
    variant: FrameVariantOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .testTag("frame_variant_${variant.key}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            variant.swatchColorHex?.let { hex ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(hex)),
                )
            }
            Text(
                text = variant.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * Clean, modern, strictly non-overlapping structured card layout used ONLY as a safe fallback
 * when the Bridge is offline AND the active style has no resolvable upstream frame image or coordinates.
 * Strictly obeys PROJECT_SPEC.md: NEVER draws fake MSE frames and NEVER populates default artwork.
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
        shape = RoundedCornerShape(12.dp),
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
            // 1. Header Row (Card Name + neutral Cost placeholder — never pre-filled gold "W")
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
                        .widthIn(min = 60.dp, max = 108.dp)
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

            // 2. Artwork Box (Strictly empty until user imports/crops an image)
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
                            IconButton(onClick = onTapArt, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Replace artwork", modifier = Modifier.size(16.dp))
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

            // 3. Type Line Row
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

            // 4. Rules & Flavor Box
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

            // 5. Dedicated Footer Row (Illustrator + P/T)
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
 * Transparent tap hitboxes aligned proportionally over the ONLINE MSE Bridge-rendered PNG.
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
            val fieldKey = hitbox.field.lowercase()
            val isArtBox = fieldKey == "image" || fieldKey == "art" || fieldKey == "image 1" || fieldKey == "card image"

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .width(widthDp.coerceAtLeast(24.dp))
                    .height(heightDp.coerceAtLeast(18.dp))
                    .clickable {
                        if (isArtBox) onTapArt() else onTapField(buildEditTargetForHitbox(fieldKey, card))
                    }
                    .testTag("hitbox_${fieldKey.replace(' ', '_')}"),
            )
        }
    }
}

// ---- Overlay Helpers ----

private val IGNORED_OVERLAY_FIELDS = setOf(
    "image", "art", "image 1", "card image",
    "border", "card color", "background", "frame", "card frame", "card background",
    "rarity", "watermark", "set symbol", "symbol", "stamp", "foil", "indicator",
    "casting cost 2", "copyright",
)

private fun prepareOverlayTextHitboxes(raw: List<MseHitbox>): List<MseHitbox> {
    val filtered = raw.filter { it.field.lowercase() !in IGNORED_OVERLAY_FIELDS }
    val result = mutableListOf<MseHitbox>()
    val superBox = filtered.firstOrNull { it.field.equals("super type", ignoreCase = true) }
    val subBox = filtered.firstOrNull { it.field.equals("sub type", ignoreCase = true) }
    val powerBox = filtered.firstOrNull { it.field.equals("power", ignoreCase = true) }
    val toughBox = filtered.firstOrNull { it.field.equals("toughness", ignoreCase = true) }

    val skip = mutableSetOf<String>()
    if (superBox != null && subBox != null) {
        val left = minOf(superBox.left, subBox.left)
        val top = minOf(superBox.top, subBox.top)
        val right = maxOf(superBox.left + superBox.width, subBox.left + subBox.width)
        val bottom = maxOf(superBox.top + superBox.height, subBox.top + subBox.height)
        result += superBox.copy(
            field = "type",
            left = left,
            top = top,
            width = (right - left).coerceAtLeast(superBox.width),
            height = (bottom - top).coerceAtLeast(superBox.height),
        )
        skip += "super type"
        skip += "sub type"
    }
    if (powerBox != null && toughBox != null) {
        val left = minOf(powerBox.left, toughBox.left)
        val top = minOf(powerBox.top, toughBox.top)
        val right = maxOf(powerBox.left + powerBox.width, toughBox.left + toughBox.width)
        val bottom = maxOf(powerBox.top + powerBox.height, toughBox.top + toughBox.height)
        result += powerBox.copy(
            field = "pt",
            left = left,
            top = top,
            width = (right - left).coerceAtLeast(powerBox.width),
            height = (bottom - top).coerceAtLeast(powerBox.height),
        )
        skip += "power"
        skip += "toughness"
    }

    filtered.forEach { box ->
        if (box.field.lowercase() !in skip) {
            result += box
        }
    }
    return result.sortedBy { it.zIndex }
}

private fun resolveOverlayTextValue(fieldKey: String, card: MseCardView?, setCode: String): String {
    if (card == null) return ""
    return when (fieldKey) {
        "type" -> card.fullTypeLine
        "pt" -> card.ptSummary
        "text" -> listOf(card.ruleText, card.flavorText).filter { it.isNotBlank() }.joinToString("\n\n")
        "illustrator" -> card.illustrator.ifBlank { setCode }
        else -> card.field(fieldKey)
    }
}

private fun resolveCleanPlaceholder(fieldKey: String): String {
    return when (fieldKey) {
        "name" -> "Card Name"
        "casting cost" -> "Cost"
        "type", "super type" -> "Card Type"
        "sub type" -> "Subtype"
        "rule text", "text" -> "Rules text"
        "flavor text" -> "Flavor text"
        "pt" -> "P / T"
        "power" -> "P"
        "toughness" -> "T"
        "illustrator" -> "Illustrator"
        else -> fieldKey.split(' ', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
    }
}

private fun defaultFontSizeForField(fieldKey: String): Float = when (fieldKey) {
    "name" -> 16f
    "casting cost" -> 13f
    "type", "super type", "sub type" -> 13f
    "pt", "power", "toughness" -> 14f
    "illustrator" -> 9f
    "flavor text" -> 11f
    else -> 12f
}

private fun parseHitboxColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF14161A)
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(Color(0xFF14161A))
}

private fun buildEditTargetForHitbox(fieldKey: String, card: MseCardView?): CardFieldEditTarget {
    return when (fieldKey) {
        "type", "super type", "sub type" -> CardFieldEditTarget.TypeLine(
            superType = card?.superType.orEmpty(),
            subType = card?.subType.orEmpty(),
        )

        "pt", "power", "toughness" -> CardFieldEditTarget.PowerToughness(
            power = card?.power.orEmpty(),
            toughness = card?.toughness.orEmpty(),
        )

        "text" -> CardFieldEditTarget.SingleField(
            fieldKey = "rule text",
            initialValue = card?.ruleText.orEmpty(),
        )

        else -> CardFieldEditTarget.SingleField(
            fieldKey = fieldKey,
            initialValue = card?.field(fieldKey).orEmpty(),
        )
    }
}
