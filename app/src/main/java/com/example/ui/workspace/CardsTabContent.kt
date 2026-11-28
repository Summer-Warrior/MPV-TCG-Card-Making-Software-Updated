package com.example.ui.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.example.data.mse.MseStyleParser
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.MseTemplateCatalogRepository
import com.example.data.mse.ResolvedOfflineFrame
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.domain.comparison.CardOrigin
import com.example.domain.comparison.SplitOffSetManifest
import com.example.domain.frameworks.UniversalCardFramework
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
    splitOffManifest: SplitOffSetManifest? = null,
    activeFramework: UniversalCardFramework? = null,
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
    onBranchAlternateLoreVariant: (Int) -> Unit = {},
    onUpdateCardFields: (Map<String, String?>) -> Unit,
    onEmbedCroppedArt: (ByteArray) -> Unit,
    onClearCardArt: () -> Unit,
    onUpdateCalibrationScale: (Float) -> Unit,
    onToggleCalibrationLock: (Boolean) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    isDeviceOnline: Boolean = false,
    onLoadLocalStyleUri: (Uri) -> Unit = {},
    onSyncFramesOnline: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics

    val cards = archive.document.cards
    val activeCard = cards.getOrNull(activeCardIndex)
    val embeddedArtBytes = archive.getArtBytes(activeCardIndex)
    val isProtectedCanonCard = splitOffManifest?.preserveAllOriginalOfficialCards == true &&
        activeCard?.isCanonOriginal == true

    val customCardFontFamily = remember(localSelectedFontFile?.absolutePath, localSelectedFontFile?.lastModified()) {
        localSelectedFontFile?.let { file ->
            runCatching { FontFamily(Typeface.createFromFile(file)) }
                .onFailure { Log.w("CardsTabContent", "Could not load local font ${file.name}: ${it.message}") }
                .getOrNull()
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
    var showInlineCardListOnCompact by remember { mutableStateOf(false) }

    val activeVariant = offlineFrame?.activeVariant
    val activeVariantUrl = activeVariant?.imageUrl
    val localFrameFileExists = remember(activeVariant?.localFilePath) {
        activeVariant?.localFilePath?.let { File(it).exists() } == true
    }
    var frameLoadFailedForUrl by remember(activeVariantUrl, activeVariant?.localFilePath) {
        mutableStateOf(false)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) pendingCropUri = uri
    }

    val localStylePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onLoadLocalStyleUri(uri)
    }

    val launchArtPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val isOnlineRendered = renderState is CardRenderState.Rendered
    // NEVER hide a valid real frame image just because a scripted `.mse-style` has non-static coordinates.
    val canShowUpstreamBaseFrame = !isOnlineRendered &&
        activeVariant != null &&
        (localFrameFileExists || !activeVariantUrl.isNullOrBlank()) &&
        !frameLoadFailedForUrl

    val activeFrameColorKey = remember(activeVariant?.key, activeCard?.field("card color"), activeCard?.castingCost, activeCard?.superType) {
        activeVariant?.key
            ?: activeCard?.field("card color")?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: inferFallbackCardColorKey(activeCard)
    }

    val stageVignette = Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.52f),
            MaterialTheme.colorScheme.surfaceContainerLowest,
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideWorkbench = maxWidth >= 720.dp
        val compactInlineListHeight = (maxHeight * 0.36f).coerceIn(156.dp, 260.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(if (isWideWorkbench) 0.56f else 1f)
                    .fillMaxHeight(),
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

                        if (canShowUpstreamBaseFrame && activeVariant != null) {
                            RealUpstreamBaseFrameCompositor(
                                variant = activeVariant,
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
                            // Authentic MSE-proportioned fallback sheet ONLY when no real PNG/JPG frame is cached yet
                            StructuredOfflineCardLayout(
                                card = activeCard,
                                setCode = archive.document.setInfo.setCode,
                                borderColorSpec = archive.document.setInfo.borderColor,
                                frameColorKey = activeFrameColorKey,
                                customFontFamily = customCardFontFamily,
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

        // ---- Compact Frame Color / Variant Selector & 1-Tap Local Style / Online Frame Sync (BELOW Card, NEVER Over Card) ----
        if (!isOnlineRendered) {
            val resolvedVariants = offlineFrame?.variants.orEmpty()
            val displayVariants = if (resolvedVariants.isNotEmpty()) {
                resolvedVariants
            } else {
                DEFAULT_MSE_FRAME_COLOR_OPTIONS
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 4.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    displayVariants.forEach { variant ->
                        val selected = if (resolvedVariants.isNotEmpty()) {
                            variant.key == offlineFrame?.activeVariant?.key
                        } else {
                            variant.key.equals(activeFrameColorKey, ignoreCase = true)
                        }
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

                if (!canShowUpstreamBaseFrame) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { localStylePickerLauncher.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("load_local_mse_style_inline_button"),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Load Local .mse-style", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        TextButton(
                            onClick = onSyncFramesOnline,
                            enabled = isDeviceOnline && offlineFrame?.isLoading != true,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("sync_frames_online_inline_button"),
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = when {
                                    offlineFrame?.isLoading == true -> "Syncing Frames…"
                                    isDeviceOnline -> "Sync Frames (Online)"
                                    else -> "Sync Frames (Offline)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                } else {
                    Text(
                        text = buildString {
                            if (activeFramework != null) {
                                append("${activeFramework.effectiveShortName} • ")
                            }
                            if (localFrameFileExists) {
                                append("Local Cached Frame • ")
                            }
                            append(stringResource(R.string.offline_frame_caption))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ---- Canon Lore Preservation Strip (Cleanly BELOW Card — NEVER Over Card) ----
        if (splitOffManifest != null && activeCard != null) {
            val originEnum = CardOrigin.fromWire(activeCard.cardOriginRaw)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_origin_status_strip"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isProtectedCanonCard) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Protected Canon Original",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = if (isProtectedCanonCard) {
                                "Canon Original • Protected"
                            } else {
                                originEnum.displayLabel
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isProtectedCanonCard) {
                        FilledTonalButton(
                            onClick = { onBranchAlternateLoreVariant(activeCardIndex) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("branch_alternate_lore_variant_button"),
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Branch AU Variant", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
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
                        .padding(horizontal = 16.dp),
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

        // ---- Collapsible Inline MSE Card List Panel on Compact Phones (~36% height) ----
        if (!isWideWorkbench && showInlineCardListOnCompact) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MseDockedCardListPanel(
                cards = cards,
                activeCardIndex = activeCardIndex,
                onSelectCard = onSelectCard,
                onAddBlankCard = onAddBlankCard,
                onDuplicateCard = onDuplicateCard,
                onDeleteCard = onDeleteCard,
                onExpandFullSheet = { showCardTableSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(compactInlineListHeight),
            )
        }

        // ---- Clean MSE Bottom Card Bar (Explicit 16.dp Horizontal Padding — Zero Edge-Touch on 360dp) ----
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                .clickable {
                                    if (isWideWorkbench) {
                                        showCardTableSheet = true
                                    } else {
                                        showInlineCardListOnCompact = !showInlineCardListOnCompact
                                    }
                                }
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
                                    text = if (isWideWorkbench) {
                                        "Card ${if (cards.isEmpty()) 0 else activeCardIndex + 1} / ${cards.size}"
                                    } else {
                                        "Cards (${cards.size})"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!isWideWorkbench) {
                                    Icon(
                                        imageVector = if (showInlineCardListOnCompact) {
                                            Icons.Default.KeyboardArrowDown
                                        } else {
                                            Icons.Default.KeyboardArrowUp
                                        },
                                        contentDescription = if (showInlineCardListOnCompact) "Collapse card table" else "Expand card table",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
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
            } // end left Card Preview Column

            // ---- Wide / Tablet / Landscape Docked Live MSE Card List Table (44% Split Pane) ----
            if (isWideWorkbench) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MseDockedCardListPanel(
                    cards = cards,
                    activeCardIndex = activeCardIndex,
                    onSelectCard = onSelectCard,
                    onAddBlankCard = onAddBlankCard,
                    onDuplicateCard = onDuplicateCard,
                    onDeleteCard = onDeleteCard,
                    onExpandFullSheet = { showCardTableSheet = true },
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                )
            }
        } // end Row
    } // end BoxWithConstraints

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
 * - Enforces runtime portrait bitmap dimension validation (`width >= 250 && height >= 350 &&
 *   height / width in 1.25..1.65`) and immediately falls back to [StructuredOfflineCardLayout]
 *   on invalid assets — NEVER stretching tiny overlay PNGs onto a pitch-black card.
 * - Maps ONLY core visible card fields (strictly excluding `alias`, `border color`, `card color *`,
 *   `loyalty*`, and hidden/conditional fields) with guaranteed luminance-aware text contrast.
 * - Keeps artwork strictly empty (`card.image == null`) with a subtle transparent tap hint.
 */
@Composable
private fun RealUpstreamBaseFrameCompositor(
    variant: FrameVariantOption,
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
    val frameImageUrl = variant.imageUrl
    val frameModel: Any = remember(variant.localFilePath, frameImageUrl) {
        variant.localFilePath?.let(::File)?.takeIf { it.exists() } ?: frameImageUrl
    }
    val cardW = styleSpec.cardWidthPx.coerceAtLeast(1).toFloat()
    val cardH = styleSpec.cardHeightPx.coerceAtLeast(1).toFloat()

    val userArtBitmap = remember(embeddedArtBytes) {
        embeddedArtBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .onFailure { Log.w("CardsTabContent", "Could not decode embedded card art: ${it.message}") }
                .getOrNull()
        }
    }

    var loadedFrameBitmap by remember(frameModel) { mutableStateOf<Bitmap?>(null) }

    val effectiveHitboxes = remember(styleSpec) {
        resolveEffectiveCompositeHitboxes(styleSpec)
    }
    val artBox = remember(effectiveHitboxes) {
        effectiveHitboxes.firstOrNull {
            val k = it.field.lowercase()
            k == "image" || k == "art" || k == "image 1" || k == "card image"
        }
    }
    val artBehindFrame = artBox != null && artBox.zIndex <= 0 &&
        (variant.localFilePath ?: frameImageUrl).endsWith(".png", ignoreCase = true)

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
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

        // Layer 2: Real Local Cached or Upstream `.mse-style` Base-Frame Image via Coil
        AsyncImage(
            model = frameModel,
            contentDescription = "${styleSpec.shortName} card frame",
            contentScale = ContentScale.FillBounds,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        val drawable = state.result.drawable
                        val bmp = (drawable as? BitmapDrawable)?.bitmap
                        val w = bmp?.width ?: drawable.intrinsicWidth
                        val h = bmp?.height ?: drawable.intrinsicHeight
                        if (!MseTemplateCatalogRepository.isValidPortraitCardDimensions(w, h)) {
                            loadedFrameBitmap = null
                            onFrameImageError()
                        } else {
                            loadedFrameBitmap = bmp
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        loadedFrameBitmap = null
                        onFrameImageError()
                    }

                    else -> Unit
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
                    // Strictly NO default artwork: clean subtle transparent tap area only
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color(0xFFF5F5F5).copy(alpha = 0.88f),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.tap_to_add_artwork),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF5F5F5).copy(alpha = 0.88f),
                        )
                    }
                }
            }
        }

        // Layer 4: Core Visible Card Fields Only — Guaranteed High-Contrast Text (NEVER black-on-black)
        val textHitboxes = remember(effectiveHitboxes, styleSpec.game) {
            prepareOverlayTextHitboxes(effectiveHitboxes, styleSpec.game)
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

            // Sample actual pixel luminance under this hitbox on the loaded base-frame bitmap
            val regionLuminance = remember(loadedFrameBitmap, hitbox, variant.avgLuminance, fieldKey) {
                val sampled = loadedFrameBitmap?.let { bmp ->
                    sampleRegionLuminance(
                        bitmap = bmp,
                        normLeft = (hitbox.left / cardW).coerceIn(0f, 0.98f),
                        normTop = (hitbox.top / cardH).coerceIn(0f, 0.98f),
                        normWidth = (hitbox.width / cardW).coerceIn(0.02f, 1f),
                        normHeight = (hitbox.height / cardH).coerceIn(0.02f, 1f),
                    )
                }
                when {
                    sampled != null -> sampled
                    fieldKey == "illustrator" || fieldKey == "copyright" -> 0.12f
                    else -> variant.avgLuminance
                }
            }
            val isDarkRegion = regionLuminance < 0.46f

            val fontSizeSp = ((hitbox.fontSize ?: defaultFontSizeForField(fieldKey)) * scaleFactor)
                .coerceIn(8f, 18f).sp
            val textColor = when {
                isDarkRegion && isEmpty -> Color(0xFFF5F5F5).copy(alpha = 0.78f)
                isDarkRegion -> Color(0xFFF5F5F5)
                isEmpty -> Color(0xFF111111).copy(alpha = 0.62f)
                else -> Color(0xFF111111)
            }
            val textShadow = if (isDarkRegion) {
                Shadow(
                    color = Color(0xCC000000),
                    offset = Offset(0f, 1.2f),
                    blurRadius = 3f,
                )
            } else {
                null
            }
            val placeholderBackdrop = when {
                !isEmpty -> Color.Transparent
                isDarkRegion -> Color(0x3D000000)
                else -> Color(0x47FFFFFF)
            }

            val textAlign = when {
                hitbox.alignment?.contains("right", ignoreCase = true) == true ||
                    fieldKey == "casting cost" || fieldKey == "cost" -> TextAlign.End

                hitbox.alignment?.contains("center", ignoreCase = true) == true ||
                    fieldKey == "pt" || fieldKey == "power" || fieldKey == "toughness" ||
                    fieldKey == "atk" || fieldKey == "def" -> TextAlign.Center

                else -> TextAlign.Start
            }
            val contentAlignment = when (textAlign) {
                TextAlign.End -> Alignment.CenterEnd
                TextAlign.Center -> Alignment.Center
                else -> if (
                    fieldKey.contains("rule") || fieldKey == "text" ||
                    fieldKey.contains("flavor") || fieldKey.contains("desc") ||
                    fieldKey == "effect"
                ) {
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholderBackdrop)
                    .clickable {
                        onTapField(buildEditTargetForHitbox(fieldKey, card))
                    }
                    .padding(horizontal = 3.dp, vertical = 1.dp)
                    .testTag("hitbox_${fieldKey.replace(' ', '_')}"),
                contentAlignment = contentAlignment,
            ) {
                Text(
                    text = displayText,
                    style = TextStyle(
                        fontFamily = customFontFamily ?: MaterialTheme.typography.bodyMedium.fontFamily,
                        fontSize = fontSizeSp,
                        lineHeight = (fontSizeSp.value * 1.18f).sp,
                        fontWeight = if (
                            fieldKey == "name" || fieldKey == "title" ||
                            fieldKey == "pt" || fieldKey == "power" || fieldKey == "toughness"
                        ) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        fontStyle = if (fieldKey.contains("flavor")) FontStyle.Italic else FontStyle.Normal,
                        color = textColor,
                        textAlign = textAlign,
                        shadow = textShadow,
                    ),
                    maxLines = if (
                        fieldKey.contains("rule") || fieldKey == "text" ||
                        fieldKey.contains("flavor") || fieldKey.contains("desc") ||
                        fieldKey == "effect"
                    ) {
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

private fun sampleRegionLuminance(
    bitmap: Bitmap,
    normLeft: Float,
    normTop: Float,
    normWidth: Float,
    normHeight: Float,
): Float? {
    val bw = bitmap.width
    val bh = bitmap.height
    if (bw <= 0 || bh <= 0) return null
    val x0 = (normLeft * bw).toInt().coerceIn(0, bw - 1)
    val y0 = (normTop * bh).toInt().coerceIn(0, bh - 1)
    val x1 = ((normLeft + normWidth) * bw).toInt().coerceIn(x0 + 1, bw)
    val y1 = ((normTop + normHeight) * bh).toInt().coerceIn(y0 + 1, bh)

    val stepX = ((x1 - x0) / 5).coerceAtLeast(1)
    val stepY = ((y1 - y0) / 4).coerceAtLeast(1)
    var sum = 0.0
    var count = 0
    var y = y0
    while (y < y1) {
        var x = x0
        while (x < x1) {
            val px = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(px) >= 128) {
                val r = AndroidColor.red(px) / 255.0
                val g = AndroidColor.green(px) / 255.0
                val b = AndroidColor.blue(px) / 255.0
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                count++
            }
            x += stepX
        }
        y += stepY
    }
    return if (count > 0) (sum / count).toFloat().coerceIn(0f, 1f) else null
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
 * Authentic Magic Set Editor card sheet fallback used ONLY when the Bridge is offline AND
 * no local/cached PNG/JPG frame exists yet on disk.
 * - Uses outer border parsed from `archive.document.setInfo.borderColor` (`rgb(r,g,b)`).
 * - Inner beveled plate subtly tinted by active card color (`Colorless/White/Blue/Black/Red/Green/Gold/Artifact/Land`).
 * - Strictly preserves `image == null` (ZERO default card art).
 */
@Composable
private fun StructuredOfflineCardLayout(
    card: MseCardView?,
    setCode: String,
    borderColorSpec: String,
    frameColorKey: String,
    customFontFamily: FontFamily?,
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
    val outerBorderColor = remember(borderColorSpec) { parseMseBorderColor(borderColorSpec) }
    val palette = remember(frameColorKey) { resolveMseSheetPalette(frameColorKey) }
    val footerIsDarkBorder = remember(outerBorderColor) {
        (0.2126f * outerBorderColor.red + 0.7152f * outerBorderColor.green + 0.0722f * outerBorderColor.blue) < 0.45f
    }
    val footerTextColor = if (footerIsDarkBorder) Color(0xFFE6E6EA) else Color(0xFF1A1A1E)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = outerBorderColor,
        border = BorderStroke(1.dp, Color(0xFF2B2D35)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Inner Beveled MSE Frame Plate
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.framePlate,
                border = BorderStroke(1.2.dp, palette.bevelStroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // 1. Classic MSE Header Bar (Card Name + Cost)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.barFill,
                        border = BorderStroke(1.dp, palette.barStroke),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val nameText = card?.name.orEmpty()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onEditName)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("card_field_name"),
                            ) {
                                Text(
                                    text = nameText.ifBlank { "Card Name" },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = customFontFamily ?: MaterialTheme.typography.titleSmall.fontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = if (nameText.isBlank()) palette.barHint else palette.barInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = palette.framePlate.copy(alpha = 0.55f),
                                border = BorderStroke(0.8.dp, palette.barStroke.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .widthIn(min = 44.dp, max = 96.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onEditCost)
                                    .testTag("card_field_cost"),
                            ) {
                                val costText = card?.castingCost.orEmpty()
                                Text(
                                    text = costText.ifBlank { "Cost" },
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = customFontFamily ?: MaterialTheme.typography.labelLarge.fontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (costText.isBlank()) palette.barHint else palette.barInk,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    // 2. Recessed Art Window (Strictly empty until user imports/crops an image)
                    val userArtBitmap = remember(embeddedArtBytes) {
                        embeddedArtBytes?.let { bytes ->
                            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                                .onFailure { Log.w("CardsTabContent", "Failed decoding card art: ${it.message}") }
                                .getOrNull()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.08f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF15171E))
                            .border(1.2.dp, palette.bevelStroke, RoundedCornerShape(4.dp))
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
                                    .padding(5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.68f),
                                ) {
                                    IconButton(onClick = onTapArt, modifier = Modifier.size(28.dp)) {
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
                                    color = Color.Black.copy(alpha = 0.68f),
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
                            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                drawRoundRect(
                                    color = Color(0xFF4F5566),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                    ),
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color(0xFFB7BCCB),
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = stringResource(R.string.tap_to_add_artwork),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB7BCCB),
                                )
                            }
                        }
                    }

                    // 3. Full-Width Classic MSE Type Line Bar
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.barFill,
                        border = BorderStroke(1.dp, palette.barStroke),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onEditTypeLine)
                            .testTag("card_field_type_line"),
                    ) {
                        val typeText = card?.fullTypeLine.orEmpty()
                        Text(
                            text = typeText.ifBlank { "Card Type — Subtype" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = customFontFamily ?: MaterialTheme.typography.bodySmall.fontFamily,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (typeText.isBlank()) palette.barHint else palette.barInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }

                    // 4. Parchment / High-Contrast Rules + Italic Flavor Box with Recessed P/T Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.95f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = palette.textBoxFill,
                            border = BorderStroke(1.dp, palette.barStroke),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                val rulesText = card?.ruleText.orEmpty()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1.35f)
                                        .clickable(onClick = onEditRules)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("card_field_rules"),
                                ) {
                                    Text(
                                        text = rulesText.ifBlank { "Tap to add rules text" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = customFontFamily ?: MaterialTheme.typography.bodySmall.fontFamily,
                                        ),
                                        color = if (rulesText.isBlank()) palette.barHint else palette.barInk,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                HorizontalDivider(
                                    color = palette.barStroke.copy(alpha = 0.45f),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )

                                val flavorText = card?.flavorText.orEmpty()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.65f)
                                        .clickable(onClick = onEditFlavor)
                                        .padding(start = 8.dp, end = 64.dp, top = 4.dp, bottom = 4.dp)
                                        .testTag("card_field_flavor"),
                                ) {
                                    Text(
                                        text = flavorText.ifBlank { "Flavor text" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = customFontFamily ?: MaterialTheme.typography.bodySmall.fontFamily,
                                            fontStyle = FontStyle.Italic,
                                        ),
                                        color = palette.barHint,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        // Recessed Bottom-Right MSE P/T Box
                        val ptText = card?.ptSummary.orEmpty()
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.barFill,
                            border = BorderStroke(1.2.dp, palette.bevelStroke),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onEditPowerToughness)
                                .testTag("card_field_pt"),
                        ) {
                            Text(
                                text = ptText.ifBlank { "P / T" },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = customFontFamily ?: MaterialTheme.typography.labelLarge.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (ptText.isBlank()) palette.barHint else palette.barInk,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // 5. Crisp Bottom Collector / Illustrator Bar on Outer Card Border
            val illustrator = card?.illustrator.orEmpty()
            val footerLabel = buildString {
                if (setCode.isNotBlank()) {
                    append(setCode)
                    append(" • ")
                }
                if (illustrator.isNotBlank()) {
                    append(illustrator)
                } else {
                    append("Illustrator")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onEditIllustrator)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag("card_field_illustrator"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = footerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = footerTextColor.copy(alpha = if (illustrator.isBlank()) 0.68f else 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class MseSheetPalette(
    val framePlate: Color,
    val bevelStroke: Color,
    val barFill: Color,
    val barStroke: Color,
    val textBoxFill: Color,
    val barInk: Color,
    val barHint: Color,
)

private val DEFAULT_MSE_FRAME_COLOR_OPTIONS = listOf(
    FrameVariantOption("colorless", "Colorless", "", swatchColorHex = 0xFFAAB0BC),
    FrameVariantOption("white", "White", "", swatchColorHex = 0xFFF3E9C9),
    FrameVariantOption("blue", "Blue", "", swatchColorHex = 0xFF3E88C7),
    FrameVariantOption("black", "Black", "", swatchColorHex = 0xFF453D4F),
    FrameVariantOption("red", "Red", "", swatchColorHex = 0xFFC9513E),
    FrameVariantOption("green", "Green", "", swatchColorHex = 0xFF3C8A57),
    FrameVariantOption("multicolor", "Gold", "", swatchColorHex = 0xFFD4A84B),
    FrameVariantOption("artifact", "Artifact", "", swatchColorHex = 0xFF8B99A8),
    FrameVariantOption("land", "Land", "", swatchColorHex = 0xFF9C8468),
)

private fun inferFallbackCardColorKey(card: MseCardView?): String {
    if (card == null) return "colorless"
    val superType = card.superType.lowercase()
    if (superType.contains("land")) return "land"
    if (superType.contains("artifact")) return "artifact"
    val cost = card.castingCost.uppercase()
    val colors = buildList {
        if (cost.contains('W')) add("white")
        if (cost.contains('U')) add("blue")
        if (cost.contains('B')) add("black")
        if (cost.contains('R')) add("red")
        if (cost.contains('G')) add("green")
    }
    return when {
        colors.size >= 2 -> "multicolor"
        colors.size == 1 -> colors.first()
        else -> "colorless"
    }
}

private fun parseMseBorderColor(spec: String): Color {
    val match = Regex("rgb\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)
        .find(spec)
    if (match != null) {
        val r = match.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: 14
        val g = match.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: 15
        val b = match.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: 18
        return Color(r / 255f, g / 255f, b / 255f)
    }
    return Color(0xFF101116)
}

private fun resolveMseSheetPalette(frameColorKey: String): MseSheetPalette {
    val ink = Color(0xFF14151A)
    val hint = Color(0xFF535866)
    return when (frameColorKey.lowercase()) {
        "white", "w" -> MseSheetPalette(
            framePlate = Color(0xFFE7DFCB),
            bevelStroke = Color(0xFFC4B89B),
            barFill = Color(0xFFF6F2E6),
            barStroke = Color(0xFFB3A688),
            textBoxFill = Color(0xFFFAF7EE),
            barInk = ink,
            barHint = hint,
        )
        "blue", "u" -> MseSheetPalette(
            framePlate = Color(0xFF2B6EA3),
            bevelStroke = Color(0xFF194B75),
            barFill = Color(0xFFD6E6F3),
            barStroke = Color(0xFF4F84AF),
            textBoxFill = Color(0xFFEAF2F8),
            barInk = ink,
            barHint = hint,
        )
        "black", "b" -> MseSheetPalette(
            framePlate = Color(0xFF393440),
            bevelStroke = Color(0xFF1F1B24),
            barFill = Color(0xFFC7C2CE),
            barStroke = Color(0xFF585161),
            textBoxFill = Color(0xFFE3E0E8),
            barInk = ink,
            barHint = hint,
        )
        "red", "r" -> MseSheetPalette(
            framePlate = Color(0xFFB54434),
            bevelStroke = Color(0xFF7D2A1F),
            barFill = Color(0xFFF3DAD4),
            barStroke = Color(0xFFA65246),
            textBoxFill = Color(0xFFF9ECE9),
            barInk = ink,
            barHint = hint,
        )
        "green", "g" -> MseSheetPalette(
            framePlate = Color(0xFF2F7248),
            bevelStroke = Color(0xFF1D4D2F),
            barFill = Color(0xFFD6E9DC),
            barStroke = Color(0xFF4E8A65),
            textBoxFill = Color(0xFFEBF4EE),
            barInk = ink,
            barHint = hint,
        )
        "multicolor", "gold", "m" -> MseSheetPalette(
            framePlate = Color(0xFFC8A04A),
            bevelStroke = Color(0xFF8C6B25),
            barFill = Color(0xFFF4E8C6),
            barStroke = Color(0xFFA6853C),
            textBoxFill = Color(0xFFFAF3DF),
            barInk = ink,
            barHint = hint,
        )
        "artifact", "a" -> MseSheetPalette(
            framePlate = Color(0xFF8A97A5),
            bevelStroke = Color(0xFF5D6A78),
            barFill = Color(0xFFDFE6EC),
            barStroke = Color(0xFF7B8998),
            textBoxFill = Color(0xFFEDF1F5),
            barInk = ink,
            barHint = hint,
        )
        "land", "l" -> MseSheetPalette(
            framePlate = Color(0xFF968065),
            bevelStroke = Color(0xFF66543F),
            barFill = Color(0xFFE9DFD2),
            barStroke = Color(0xFF8A755D),
            textBoxFill = Color(0xFFF3ECE3),
            barInk = ink,
            barHint = hint,
        )
        else -> MseSheetPalette(
            framePlate = Color(0xFFB5BAC4),
            bevelStroke = Color(0xFF7B818E),
            barFill = Color(0xFFE7E9EE),
            barStroke = Color(0xFF8C92A0),
            textBoxFill = Color(0xFFF3F4F7),
            barInk = ink,
            barHint = hint,
        )
    }
}

/**
 * Resolves effective composite hitboxes for any `.mse-style`.
 * When a scripted style (using `{ ... }` MSE expressions) yields empty or collapsed static
 * hitboxes (`!styleSpec.hasValidCompositeGeometry()`), proportionally scales
 * `MseStyleParser.defaultStandardGeometrySpec().hitboxes` from `(375, 523)` to
 * `(styleSpec.cardWidthPx, styleSpec.cardHeightPx)` so overlays sit in exact spots over the real frame!
 */
internal fun resolveEffectiveCompositeHitboxes(styleSpec: MseStyleSpec): List<MseHitbox> {
    if (styleSpec.hasValidCompositeGeometry()) {
        return styleSpec.hitboxes
    }
    val base = MseStyleParser.defaultStandardGeometrySpec()
    val targetW = styleSpec.cardWidthPx.coerceAtLeast(1).toFloat()
    val targetH = styleSpec.cardHeightPx.coerceAtLeast(1).toFloat()
    val sx = targetW / base.cardWidthPx.coerceAtLeast(1).toFloat()
    val sy = targetH / base.cardHeightPx.coerceAtLeast(1).toFloat()
    return base.hitboxes.map { h ->
        h.copy(
            left = h.left * sx,
            top = h.top * sy,
            width = h.width * sx,
            height = h.height * sy,
        )
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

    val effectiveHitboxes = remember(styleSpec) { resolveEffectiveCompositeHitboxes(styleSpec) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        effectiveHitboxes.forEach { hitbox ->
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

// ---- Strict Core Visible Card Fields Allowlist & Exclusion Rules ----

private val MAGIC_VISIBLE_TEXT_FIELDS = setOf(
    "name",
    "casting cost",
    "type",
    "super type",
    "sub type",
    "rule text",
    "flavor text",
    "pt",
    "power",
    "toughness",
    "illustrator",
)

private val NON_MAGIC_VISIBLE_TEXT_FIELDS = setOf(
    "name",
    "title",
    "cost",
    "type",
    "attribute",
    "level",
    "rank",
    "rule text",
    "text",
    "desc",
    "description",
    "effect",
    "flavor text",
    "atk",
    "def",
    "hp",
    "power",
    "toughness",
    "health",
    "attack",
    "illustrator",
    "copyright",
)

private val STRICTLY_EXCLUDED_FIELDS = setOf(
    "image", "art", "image 1", "card image",
    "alias", "border", "border color",
    "card color", "card color 2", "card color 3",
    "background", "frame", "card frame", "card background",
    "styling", "watermark", "indicator", "stamp", "rarity",
    "set code", "card number", "loyalty", "special text",
    "notes", "time created", "time modified", "copyright 2",
    "set symbol", "symbol", "foil", "casting cost 2",
)

internal fun isAllowedOfflineOverlayField(hitbox: MseHitbox, gameId: String): Boolean {
    if (hitbox.isHiddenOrConditional) return false
    val key = hitbox.field.trim().lowercase()
    if (key in STRICTLY_EXCLUDED_FIELDS) return false
    if (key.startsWith("loyalty") || key.startsWith("card color")) return false
    val allowlist = if (gameId.equals("magic", ignoreCase = true)) {
        MAGIC_VISIBLE_TEXT_FIELDS
    } else {
        NON_MAGIC_VISIBLE_TEXT_FIELDS
    }
    return key in allowlist
}

internal fun prepareOverlayTextHitboxes(raw: List<MseHitbox>, gameId: String): List<MseHitbox> {
    val filtered = raw.filter { isAllowedOfflineOverlayField(it, gameId) }
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
        "name", "title" -> "Card Name"
        "casting cost", "cost" -> "Cost"
        "type", "super type" -> "Card Type"
        "sub type" -> "Subtype"
        "rule text", "text", "desc", "description", "effect" -> "Tap to add rules text"
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
    "name", "title" -> 16f
    "casting cost", "cost" -> 13f
    "type", "super type", "sub type" -> 13f
    "pt", "power", "toughness", "atk", "def" -> 14f
    "illustrator", "copyright" -> 9f
    "flavor text" -> 11f
    else -> 12f
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
