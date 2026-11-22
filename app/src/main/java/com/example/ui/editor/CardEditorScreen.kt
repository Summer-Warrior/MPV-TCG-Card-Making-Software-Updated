package com.example.ui.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.fonts.FontEntry
import com.example.data.mse.MseCardView
import com.example.data.mse.MseHitbox
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseStyleSpec
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.ui.BridgeHealthState
import com.example.ui.CardRenderState
import com.example.ui.components.BridgeStatusBanner
import com.example.ui.fonts.FontDropdownBottomSheet
import kotlin.math.roundToInt

/**
 * 1:1 IRL Physical Card Editing Window (Section 4).
 *
 * Strictly adheres to the engineering contract:
 * - Zero fake or approximated MSE card frames. Rendered card PNGs come exclusively from the MSE Bridge.
 * - Zero default card art (`image` initializes strictly to `null`/empty).
 * - Exact hardware `xdpi`/`ydpi` physical sizing + persistent Calibration Slider + Snap 1:1 IRL.
 * - Direct tap-on-card hitboxes for text fields (M3 BottomSheet + MSE bar) and art box (Photo Picker + Cropper + Clear Art).
 */
@Composable
fun CardEditorScreen(
    settings: StudioSettings,
    archive: MseSetArchive,
    activeCardIndex: Int,
    styleSpec: MseStyleSpec,
    bridgeHealth: BridgeHealthState,
    renderState: CardRenderState,
    fonts: List<FontEntry>,
    isFontBusy: Boolean,
    rawSetTextProvider: () -> String,
    onSaveBridgeUrl: (String) -> Unit,
    onRetryBridgeHealth: () -> Unit,
    onOpenFullDiagnostics: () -> Unit,
    onRequestRender: () -> Unit,
    onSelectCard: (Int) -> Unit,
    onAddBlankCard: () -> Unit,
    onCreateNewBlankSet: (title: String, game: String, stylesheet: String) -> Unit,
    onOpenMseSetUri: (Uri) -> Unit,
    onExportMseSetUri: (Uri) -> Unit,
    onLoadMseStyleUri: (Uri) -> Unit,
    onUpdateCardField: (fieldKey: String, newValue: String?) -> Unit,
    onEmbedCroppedArt: (ByteArray) -> Unit,
    onClearCardArt: () -> Unit,
    onUpdateCalibrationScale: (Float) -> Unit,
    onToggleCalibrationLock: (Boolean) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    onApplyReadyFont: (FontEntry) -> Unit,
    onDownloadOpenFont: (FontEntry) -> Unit,
    onImportPurchasedFont: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics

    val cards = archive.document.cards
    val activeCard = cards.getOrNull(activeCardIndex)
    val embeddedArtBytes = archive.getArtBytes(activeCardIndex)

    val physicalDim = remember(displayMetrics.xdpi, displayMetrics.ydpi, styleSpec, settings.physicalProfile, settings.calibrationScale) {
        PhysicalScaleMath.computeIrlCardPixels(
            displayMetrics = displayMetrics,
            styleSpec = styleSpec,
            profile = settings.physicalProfile,
            calibrationScale = settings.calibrationScale,
        )
    }

    val cardWidthDp = with(density) { physicalDim.widthPx.toDp() }
    val cardHeightDp = with(density) { physicalDim.heightPx.toDp() }

    // Viewport transform state (pinch-to-zoom + pan + 1-tap Snap 1:1 IRL)
    var viewportScale by remember { mutableFloatStateOf(1.0f) }
    var viewportOffset by remember { mutableStateOf(Offset.Zero) }

    // UI modal states
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCalibrationDrawer by remember { mutableStateOf(false) }
    var showFontDropdownSheet by remember { mutableStateOf(false) }
    var showRawSetDialog by remember { mutableStateOf(false) }
    var showNewSetDialog by remember { mutableStateOf(false) }
    var editingHitbox by remember { mutableStateOf<MseHitbox?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    // Zero-permission Android Photo Picker (Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
        }
    }

    val openSetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onOpenMseSetUri(uri)
    }

    val exportSetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri != null) onExportMseSetUri(uri)
    }

    val loadStyleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onLoadMseStyleUri(uri)
    }

    val missingFontCount = remember(fonts) {
        fonts.count { it.detectedMissingInTemplate && !it.availability.isReady }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- Top Studio Toolbar ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = archive.document.setTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${archive.document.game} / ${archive.document.stylesheet} • Card ${if (cards.isEmpty()) 0 else activeCardIndex + 1}/${cards.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Font Dropdown Picker trigger in Editor toolbar
            AssistChip(
                onClick = { showFontDropdownSheet = true },
                label = {
                    val labelText = when {
                        missingFontCount > 0 -> "$missingFontCount Missing"
                        !settings.selectedFontFamily.isNullOrBlank() -> settings.selectedFontFamily.take(12)
                        else -> "Fonts"
                    }
                    Text(labelText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = "Open font dropdown",
                        tint = if (missingFontCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.testTag("editor_font_dropdown_button"),
            )

            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.testTag("editor_menu_button"),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Set and style file actions")
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_mse_set)) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            openSetLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_mse_set)) },
                        leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            exportSetLauncher.launch(archive.displayName.ifBlank { "set.mse-set" })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.load_mse_style)) },
                        leadingIcon = { Icon(Icons.Default.Style, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            loadStyleLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New Blank .mse-set") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            showNewSetDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Inspect Raw `set` Syntax") },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            showRawSetDialog = true
                        },
                    )
                }
            }
        }

        // ---- Live MSE Bridge Status & Diagnostics Banner ----
        BridgeStatusBanner(
            healthState = bridgeHealth,
            currentBaseUrl = settings.bridgeBaseUrl,
            onSaveUrl = onSaveBridgeUrl,
            onRetryHealth = onRetryBridgeHealth,
            onOpenFullDiagnostics = onOpenFullDiagnostics,
        )

        // ---- Card Switcher + Quick Actions Strip ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onSelectCard(activeCardIndex - 1) },
                        enabled = activeCardIndex > 0,
                        modifier = Modifier.testTag("editor_prev_card_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous card")
                    }
                    Text(
                        text = "${if (cards.isEmpty()) 0 else activeCardIndex + 1}/${cards.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(
                        onClick = { onSelectCard(activeCardIndex + 1) },
                        enabled = activeCardIndex < cards.lastIndex,
                        modifier = Modifier.testTag("editor_next_card_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next card")
                    }
                }
            }

            AssistChip(
                onClick = onAddBlankCard,
                label = { Text(stringResource(R.string.new_card_action)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_add_card_button"),
            )

            AssistChip(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                label = { Text(stringResource(R.string.import_art_action)) },
                leadingIcon = {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("editor_import_art_button"),
            )

            if (activeCard?.image != null) {
                AssistChip(
                    onClick = onClearCardArt,
                    label = { Text(stringResource(R.string.clear_art_action)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("editor_clear_art_button"),
                )
            }

            AssistChip(
                onClick = onRequestRender,
                label = { Text("Render") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_render_button"),
            )
        }

        // ---- 1:1 IRL Drafting Table Viewport ----
        val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        val crosshairColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
        val cardOutlineColor = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        viewportScale = (viewportScale * zoom).coerceIn(0.45f, 3.0f)
                        viewportOffset = Offset(
                            x = (viewportOffset.x + pan.x).coerceIn(-1200f, 1200f),
                            y = (viewportOffset.y + pan.y).coerceIn(-1200f, 1200f),
                        )
                    }
                }
                .testTag("irl_card_viewport"),
            contentAlignment = Alignment.Center,
        ) {
            // Precision architectural drafting grid & 1:1 millimeter calibration tick marks on the table
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pxPerMm = (physicalDim.xdpi / 25.4f) * settings.calibrationScale * viewportScale
                val step = (pxPerMm * 5f).coerceAtLeast(18f) // 5mm drafting grid
                var x = (size.width / 2f + viewportOffset.x) % step
                while (x < size.width) {
                    drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = (size.height / 2f + viewportOffset.y) % step
                while (y < size.height) {
                    drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }

                // Center calibration axes
                val cx = size.width / 2f + viewportOffset.x
                val cy = size.height / 2f + viewportOffset.y
                drawLine(
                    color = crosshairColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
                drawLine(
                    color = crosshairColor,
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }

            // Physical 1:1 Card Container scaled by hardware DPI * calibrationScale * viewportScale
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
                    .border(
                        width = 1.5.dp,
                        color = cardOutlineColor.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("irl_physical_card_container"),
            ) {
                when (renderState) {
                    is CardRenderState.Rendered -> {
                        val imageBitmap = remember(renderState.bitmap) {
                            renderState.bitmap.asImageBitmap()
                        }
                        // Exact MSE Bridge Rendered PNG
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "MSE rendered card PNG (${renderState.widthPx}x${renderState.heightPx})",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Transparent aligned tap-on-card hitboxes directly over the MSE PNG
                        HitboxTapOverlay(
                            styleSpec = styleSpec,
                            card = activeCard,
                            embeddedArtBytes = embeddedArtBytes,
                            isOfflineBlueprint = false,
                            onTapTextField = { hitbox -> editingHitbox = hitbox },
                            onTapArtBox = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                    }

                    is CardRenderState.Rendering -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Rendering via MSE Bridge…",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    is CardRenderState.BridgeOffline,
                    is CardRenderState.Error,
                    is CardRenderState.Idle -> {
                        // Clean Diagnostic Blueprint Overlay — NEVER a fake card drawing.
                        // Shows exact .mse-style hitbox coordinates so user can edit all `.mse-set` fields & art offline.
                        HitboxTapOverlay(
                            styleSpec = styleSpec,
                            card = activeCard,
                            embeddedArtBytes = embeddedArtBytes,
                            isOfflineBlueprint = true,
                            onTapTextField = { hitbox -> editingHitbox = hitbox },
                            onTapArtBox = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                    }
                }
            }

            // Top-left exact telemetry HUD pill inside viewport
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Text(
                    text = "${"%.1f".format(physicalDim.widthMm)}×${"%.1f".format(physicalDim.heightMm)} mm • " +
                        "Zoom ${"%.0f".format(viewportScale * 100)}% • Cal ${"%.2f".format(settings.calibrationScale)}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Top-right render mode pill
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (renderState is CardRenderState.Rendered) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            ) {
                Text(
                    text = when (renderState) {
                        is CardRenderState.Rendered -> "MSE PNG • ${renderState.latencyMs}ms"
                        is CardRenderState.Rendering -> "Rendering…"
                        else -> "Hitbox Blueprint (Bridge Offline)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (renderState is CardRenderState.Rendered) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // ---- Bottom Calibration & Snap 1:1 IRL Control Dock ----
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            viewportScale = 1.0f
                            viewportOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("snap_irl_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.snap_irl_button))
                    }

                    OutlinedButton(
                        onClick = { showCalibrationDrawer = !showCalibrationDrawer },
                        modifier = Modifier.testTag("calibration_toggle_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Calibrate ${"%.2f".format(settings.calibrationScale)}×")
                    }
                }

                AnimatedVisibility(visible = showCalibrationDrawer) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.calibration_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${"%.2f".format(settings.calibrationScale)}×",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(48.dp),
                            )
                            Slider(
                                value = settings.calibrationScale,
                                onValueChange = {
                                    if (!settings.calibrationLocked) {
                                        onUpdateCalibrationScale(it)
                                    }
                                },
                                enabled = !settings.calibrationLocked,
                                valueRange = StudioSettings.MIN_CALIBRATION..StudioSettings.MAX_CALIBRATION,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("calibration_slider"),
                            )
                            IconButton(
                                onClick = { onToggleCalibrationLock(!settings.calibrationLocked) },
                                modifier = Modifier.testTag("calibration_lock_button"),
                            ) {
                                Icon(
                                    imageVector = if (settings.calibrationLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (settings.calibrationLocked) "Unlock calibration" else "Lock calibration",
                                    tint = if (settings.calibrationLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PhysicalCardProfile.entries.forEach { profile ->
                                FilterChip(
                                    selected = settings.physicalProfile == profile,
                                    onClick = { onUpdatePhysicalProfile(profile) },
                                    label = { Text(profile.label) },
                                    modifier = Modifier.testTag("profile_chip_${profile.id}"),
                                )
                            }
                        }

                        Text(
                            text = "Display hardware: xdpi=${"%.1f".format(physicalDim.xdpi)}, ydpi=${"%.1f".format(physicalDim.ydpi)} • " +
                                "Template: ${styleSpec.cardWidthPx}×${styleSpec.cardHeightPx} px @ ${styleSpec.cardDpi} DPI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // ---- Tap-on-Card Text Field Editor Bottom Sheet ----
    editingHitbox?.let { hitbox ->
        val currentValue = activeCard?.field(hitbox.field).orEmpty()
        FieldEditorBottomSheet(
            fieldKey = hitbox.field,
            initialValue = currentValue,
            hitboxSummary = "Hitbox (${hitbox.left.toInt()}, ${hitbox.top.toInt()}) ${hitbox.width.toInt()}×${hitbox.height.toInt()} px • z=${hitbox.zIndex}",
            onDismiss = { editingHitbox = null },
            onSave = { updated ->
                onUpdateCardField(hitbox.field, updated)
                editingHitbox = null
            },
        )
    }

    // ---- Aspect-Ratio-Locked Art Cropper ----
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

    // ---- Searchable Font Dropdown Sheet ----
    if (showFontDropdownSheet) {
        FontDropdownBottomSheet(
            fonts = fonts,
            selectedFontFamily = settings.selectedFontFamily,
            isBusy = isFontBusy,
            onDismiss = { showFontDropdownSheet = false },
            onApplyReadyFont = onApplyReadyFont,
            onDownloadOpenFont = onDownloadOpenFont,
            onImportPurchasedFont = onImportPurchasedFont,
        )
    }

    // ---- Raw Lossless `set` Syntax Inspector ----
    if (showRawSetDialog) {
        val rawText = remember(archive) { rawSetTextProvider() }
        AlertDialog(
            onDismissRequest = { showRawSetDialog = false },
            title = { Text("Lossless `set` File Preview") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = rawText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRawSetDialog = false }) { Text("Close") }
            },
        )
    }

    // ---- New Blank .mse-set Dialog ----
    if (showNewSetDialog) {
        var title by remember { mutableStateOf("Untitled Set") }
        var game by remember { mutableStateOf("magic") }
        var stylesheet by remember { mutableStateOf("m15-altered") }
        AlertDialog(
            onDismissRequest = { showNewSetDialog = false },
            title = { Text("New Blank .mse-set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Set Title") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = game,
                        onValueChange = { game = it },
                        label = { Text("MSE Game ID") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = stylesheet,
                        onValueChange = { stylesheet = it },
                        label = { Text("MSE Stylesheet ID") },
                        singleLine = true,
                    )
                    Text(
                        text = "Card art initializes strictly to null/blank.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNewBlankSet(
                            title.ifBlank { "Untitled Set" },
                            game.ifBlank { "magic" },
                            stylesheet.ifBlank { "m15-altered" },
                        )
                        showNewSetDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewSetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Aligns `.mse-style` bounding hitboxes proportionally over the 1:1 physical card container.
 * - When MSE Bridge PNG is displayed (`isOfflineBlueprint == false`), hitboxes are transparent touch regions.
 * - When Bridge is offline (`isOfflineBlueprint == true`), displays clean diagnostic hitbox wireframes
 *   so local `.mse-set` editing and art import remain 100% functional without faking a card frame.
 */
@Composable
private fun HitboxTapOverlay(
    styleSpec: MseStyleSpec,
    card: MseCardView?,
    embeddedArtBytes: ByteArray?,
    isOfflineBlueprint: Boolean,
    onTapTextField: (MseHitbox) -> Unit,
    onTapArtBox: () -> Unit,
) {
    val cardW = styleSpec.cardWidthPx.coerceAtLeast(1).toFloat()
    val cardH = styleSpec.cardHeightPx.coerceAtLeast(1).toFloat()

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val density = LocalDensity.current
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        if (isOfflineBlueprint) {
            Text(
                text = "MSE BRIDGE OFFLINE • TAP ANY HITBOX TO EDIT .mse-set",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp, start = 6.dp, end = 6.dp),
            )
        }

        styleSpec.hitboxes.forEach { hitbox ->
            val leftPx = (hitbox.left / cardW) * containerW
            val topPx = (hitbox.top / cardH) * containerH
            val widthDp = with(density) { ((hitbox.width / cardW) * containerW).toDp() }
            val heightDp = with(density) { ((hitbox.height / cardH) * containerH).toDp() }
            val isArtBox = hitbox.field == "image" || hitbox.field == "art"
            val fieldValue = card?.field(hitbox.field).orEmpty()

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .width(widthDp.coerceAtLeast(24.dp))
                    .height(heightDp.coerceAtLeast(18.dp))
                    .then(
                        if (isOfflineBlueprint) {
                            Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (isArtBox) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                    },
                                    shape = MaterialTheme.shapes.extraSmall,
                                )
                                .background(
                                    color = if (isArtBox) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
                                    },
                                    shape = MaterialTheme.shapes.extraSmall,
                                )
                        } else {
                            Modifier
                        }
                    )
                    .clickable {
                        if (isArtBox) onTapArtBox() else onTapTextField(hitbox)
                    }
                    .testTag("hitbox_${hitbox.field.replace(' ', '_')}"),
                contentAlignment = if (isArtBox) Alignment.Center else Alignment.TopStart,
            ) {
                if (isOfflineBlueprint) {
                    if (isArtBox) {
                        val userArtBitmap = remember(embeddedArtBytes) {
                            embeddedArtBytes?.let { bytes ->
                                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                            }
                        }
                        if (userArtBitmap != null) {
                            // Strictly displays ONLY user-imported or embedded `.mse-set` artwork
                            Image(
                                bitmap = userArtBitmap.asImageBitmap(),
                                contentDescription = "User embedded artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Empty art hitbox",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.art_empty_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Tap to pick & crop",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = fieldValue.ifBlank { "[${hitbox.field}]" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (fieldValue.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = if (hitbox.height > 40f) 5 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
