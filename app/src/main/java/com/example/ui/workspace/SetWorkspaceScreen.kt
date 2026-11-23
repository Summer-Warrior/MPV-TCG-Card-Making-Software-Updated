package com.example.ui.workspace

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.fonts.FontEntry
import com.example.data.mse.CatalogSyncState
import com.example.data.mse.DiscoveredGame
import com.example.data.mse.DiscoveredStyle
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseSetInfo
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.MseTemplateCatalog
import com.example.data.mse.ResolvedOfflineFrame
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.ui.BridgeHealthState
import com.example.ui.CardRenderState
import com.example.ui.components.BridgeStatusDotButton
import com.example.ui.fonts.FontDropdownBottomSheet
import com.example.ui.theme.StatusOfflineAmber
import java.io.File

private enum class MseWorkspaceTab(val labelRes: Int) {
    CARDS(R.string.tab_cards),
    SET_INFO(R.string.tab_set_info),
    STYLE(R.string.tab_style),
    KEYWORDS(R.string.tab_keywords),
    STATS(R.string.tab_stats),
}

/**
 * Main Set Window — Authentic 5-Tab Magic Set Editor Workspace.
 *
 * Compact single-row Top App Bar:
 * - Back/Welcome (with unsaved-changes confirmation), Set Title + game • style subtitle
 * - Real Undo / Redo buttons, Save icon (with unsaved dot), Font Dropdown (TT), Bridge Status Dot, Overflow menu
 * - 5 authentic MSE tabs: Cards | Set Info | Style | Keywords | Stats
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetWorkspaceScreen(
    settings: StudioSettings,
    archive: MseSetArchive,
    activeCardIndex: Int,
    hasUnsavedChanges: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    styleSpec: MseStyleSpec,
    bridgeHealth: BridgeHealthState,
    renderState: CardRenderState,
    offlineFrame: ResolvedOfflineFrame?,
    discoveredGames: List<DiscoveredGame>,
    discoveredStyles: List<DiscoveredStyle>,
    catalogSyncState: CatalogSyncState,
    fonts: List<FontEntry>,
    isFontBusy: Boolean,
    resolveLocalFontFile: (String?) -> File?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onSelectOfflineFrameVariant: (String) -> Unit,
    onRequestCloseSet: () -> Unit,
    onSaveSet: () -> Unit,
    onSaveSetAndClose: () -> Unit,
    onExportSetUri: (Uri) -> Unit,
    onLoadStyleUri: (Uri) -> Unit,
    onOpenBridgeSheet: () -> Unit,
    onOpenUpstreamSplits: () -> Unit,
    onOpenBridgeSettings: () -> Unit,
    onOpenNexusHub: () -> Unit = {},
    onSwitchToCardCoder: () -> Unit = {},
    onToggleDarkTheme: (Boolean) -> Unit = {},
    onSelectCard: (Int) -> Unit,
    onAddBlankCard: () -> Unit,
    onDuplicateCard: (Int) -> Unit,
    onDeleteCard: (Int) -> Unit,
    onUpdateCardFields: (Map<String, String?>) -> Unit,
    onEmbedCroppedArt: (ByteArray) -> Unit,
    onClearCardArt: () -> Unit,
    onUpdateSetInfo: (MseSetInfo) -> Unit,
    onSwitchStylesheet: (DiscoveredStyle) -> Unit,
    onAddKeyword: (keyword: String, match: String, reminder: String, rules: String) -> Unit,
    onUpdateKeyword: (index: Int, keyword: String, match: String, reminder: String, rules: String) -> Unit,
    onDeleteKeyword: (index: Int) -> Unit,
    onUpdateCalibrationScale: (Float) -> Unit,
    onToggleCalibrationLock: (Boolean) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    onApplyReadyFont: (FontEntry) -> Unit,
    onDownloadOpenFont: (FontEntry) -> Unit,
    onImportPurchasedFont: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showFontSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCalibrationSheet by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val missingFontCount = remember(fonts) {
        fonts.count { it.detectedMissingInTemplate && !it.availability.isReady }
    }

    val activeDiscoveredStyle = remember(archive.document.game, archive.document.stylesheet, discoveredStyles) {
        discoveredStyles.firstOrNull {
            it.gameId.equals(archive.document.game, ignoreCase = true) &&
                it.stylesheetId.equals(archive.document.stylesheet, ignoreCase = true)
        }
    }

    val localSelectedFontFile = remember(settings.selectedFontFamily, fonts) {
        resolveLocalFontFile(settings.selectedFontFamily)
    }

    val exportSetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri != null) onExportSetUri(uri)
    }

    val loadStyleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onLoadStyleUri(uri)
    }

    val handleBackNavigation = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onRequestCloseSet()
        }
    }

    BackHandler(onBack = handleBackNavigation)

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Compact Single-Row MSE Top App Bar ----
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = handleBackNavigation,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("workspace_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Welcome Window",
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                ) {
                    Text(
                        text = archive.document.setTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${MseTemplateCatalog.displayGameName(archive.document.game, discoveredGames)} • ${styleSpec.shortName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Undo / Redo (Real Edit History Stack)
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("workspace_undo_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("workspace_redo_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Save button with amber unsaved indicator dot
                IconButton(
                    onClick = onSaveSet,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("workspace_save_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save set",
                            tint = if (hasUnsavedChanges) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        if (hasUnsavedChanges) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(StatusOfflineAmber),
                            )
                        }
                    }
                }

                // Font Picker Dropdown Trigger (TT)
                IconButton(
                    onClick = { showFontSheet = true },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("workspace_font_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = "Choose card font",
                        tint = if (missingFontCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(19.dp),
                    )
                }

                // Compact Engine Status Dot
                BridgeStatusDotButton(
                    healthState = bridgeHealth,
                    onClick = onOpenBridgeSheet,
                )

                // Overflow Menu
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("workspace_overflow_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More set options",
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export .mse-set…") },
                            leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                exportSetLauncher.launch(archive.displayName.ifBlank { "set.mse-set" })
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Load .mse-style…") },
                            leadingIcon = { Icon(Icons.Default.Style, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                loadStyleLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Calibrate 1:1 Scale") },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showCalibrationSheet = true
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Switch to Card Coder (DataEditorX)") },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onSwitchToCardCoder()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (settings.darkTheme) "Switch to Light Theme" else "Switch to Dark Theme") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (settings.darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                onToggleDarkTheme(!settings.darkTheme)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Upstream Splits (MSE & DataEditorX)") },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenUpstreamSplits()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Bridge Settings") },
                            leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenBridgeSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Nexus Hub & Engine Sync") },
                            leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenNexusHub()
                            },
                        )
                    }
                }
            }
        }

        // ---- 5 Classic MSE Workspace Tabs (Zero Truncation on 360dp Phones) ----
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val minRowWidth = maxWidth
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .widthIn(min = minRowWidth)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    MseWorkspaceTab.entries.forEachIndexed { index, tab ->
                        val selected = selectedTabIndex == index
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .height(44.dp)
                                .widthIn(min = 54.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .clickable { selectedTabIndex = index }
                                .padding(horizontal = 6.dp)
                                .testTag("workspace_tab_${tab.name.lowercase()}"),
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    ),
                            )
                        }
                    }
                }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // ---- Active MSE Tab Content ----
        Box(modifier = Modifier.weight(1f)) {
            when (MseWorkspaceTab.entries[selectedTabIndex]) {
                MseWorkspaceTab.CARDS -> {
                    CardsTabContent(
                        settings = settings,
                        archive = archive,
                        activeCardIndex = activeCardIndex,
                        styleSpec = styleSpec,
                        activeDiscoveredStyle = activeDiscoveredStyle,
                        renderState = renderState,
                        offlineFrame = offlineFrame,
                        localSelectedFontFile = localSelectedFontFile,
                        showCalibrationSheet = showCalibrationSheet,
                        onDismissCalibrationSheet = { showCalibrationSheet = false },
                        onOpenCalibrationSheet = { showCalibrationSheet = true },
                        onJumpToStyleTab = { selectedTabIndex = MseWorkspaceTab.STYLE.ordinal },
                        onSelectOfflineFrameVariant = onSelectOfflineFrameVariant,
                        onSelectCard = onSelectCard,
                        onAddBlankCard = onAddBlankCard,
                        onDuplicateCard = onDuplicateCard,
                        onDeleteCard = onDeleteCard,
                        onUpdateCardFields = onUpdateCardFields,
                        onEmbedCroppedArt = onEmbedCroppedArt,
                        onClearCardArt = onClearCardArt,
                        onUpdateCalibrationScale = onUpdateCalibrationScale,
                        onToggleCalibrationLock = onToggleCalibrationLock,
                        onUpdatePhysicalProfile = onUpdatePhysicalProfile,
                    )
                }

                MseWorkspaceTab.SET_INFO -> {
                    SetInfoTabContent(
                        setInfo = archive.document.setInfo,
                        onUpdateSetInfo = onUpdateSetInfo,
                    )
                }

                MseWorkspaceTab.STYLE -> {
                    StyleTabContent(
                        archive = archive,
                        styleSpec = styleSpec,
                        settings = settings,
                        discoveredGames = discoveredGames,
                        discoveredStyles = discoveredStyles,
                        catalogSyncState = catalogSyncState,
                        missingFontCount = missingFontCount,
                        onRefreshCatalog = onRefreshCatalog,
                        onSwitchStylesheet = onSwitchStylesheet,
                        onLoadStyleUri = onLoadStyleUri,
                        onUpdatePhysicalProfile = onUpdatePhysicalProfile,
                        onOpenFontDropdown = { showFontSheet = true },
                    )
                }

                MseWorkspaceTab.KEYWORDS -> {
                    KeywordsTabContent(
                        keywords = archive.document.keywords,
                        onAddKeyword = onAddKeyword,
                        onUpdateKeyword = onUpdateKeyword,
                        onDeleteKeyword = onDeleteKeyword,
                    )
                }

                MseWorkspaceTab.STATS -> {
                    StatsTabContent(archive = archive)
                }
            }
        }
    }

    if (showFontSheet) {
        FontDropdownBottomSheet(
            fonts = fonts,
            selectedFontFamily = settings.selectedFontFamily,
            isBusy = isFontBusy,
            onDismiss = { showFontSheet = false },
            onApplyReadyFont = onApplyReadyFont,
            onDownloadOpenFont = onDownloadOpenFont,
            onImportPurchasedFont = onImportPurchasedFont,
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Save changes to '${archive.document.setTitle}'?") },
            text = {
                Text(
                    text = "You have unsaved edits in this set. Save before returning to the Welcome Window?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        onSaveSetAndClose()
                    }
                ) {
                    Text("Save & Close")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            onRequestCloseSet()
                        }
                    ) {
                        Text("Discard")
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}
