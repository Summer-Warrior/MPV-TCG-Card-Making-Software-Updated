package com.example.ui.workspace

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.bridge.BridgeTemplateDto
import com.example.data.fonts.FontEntry
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseSetInfo
import com.example.data.mse.MseStyleOption
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.MseTemplateCatalog
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.ui.BridgeHealthState
import com.example.ui.CardRenderState
import com.example.ui.components.BridgeStatusDotButton
import com.example.ui.fonts.FontDropdownBottomSheet

private enum class MseWorkspaceTab(val labelRes: Int) {
    CARDS(R.string.tab_cards),
    SET_INFO(R.string.tab_set_info),
    STYLE(R.string.tab_style),
    KEYWORDS(R.string.tab_keywords),
}

/**
 * Main Set Window — Authentic Magic Set Editor Tabbed Workspace.
 *
 * Layout:
 * - Compact Single-Row Top App Bar: Home/Back (unsaved-changes check), Set Title + game • style,
 *   Bridge Status Dot, Font Picker Icon (A), and Overflow Menu (⋮).
 * - Classic MSE Workspace Tab Strip directly below Top App Bar: Cards | Set Info | Style | Keywords.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetWorkspaceScreen(
    settings: StudioSettings,
    archive: MseSetArchive,
    activeCardIndex: Int,
    hasUnsavedChanges: Boolean,
    styleSpec: MseStyleSpec,
    bridgeHealth: BridgeHealthState,
    renderState: CardRenderState,
    bridgeTemplates: List<BridgeTemplateDto>,
    fonts: List<FontEntry>,
    isFontBusy: Boolean,
    onRequestCloseSet: () -> Unit,
    onSaveSet: () -> Unit,
    onSaveSetAndClose: () -> Unit,
    onExportSetUri: (Uri) -> Unit,
    onLoadStyleUri: (Uri) -> Unit,
    onOpenBridgeSheet: () -> Unit,
    onOpenUpstreamSplits: () -> Unit,
    onOpenBridgeSettings: () -> Unit,
    onSelectCard: (Int) -> Unit,
    onAddBlankCard: () -> Unit,
    onDuplicateCard: (Int) -> Unit,
    onDeleteCard: (Int) -> Unit,
    onUpdateCardFields: (Map<String, String?>) -> Unit,
    onEmbedCroppedArt: (ByteArray) -> Unit,
    onClearCardArt: () -> Unit,
    onUpdateSetInfo: (MseSetInfo) -> Unit,
    onSwitchStylesheet: (MseStyleOption) -> Unit,
    onAddKeyword: (String, String, String, String) -> Unit,
    onUpdateKeyword: (Int, String, String, String, String) -> Unit,
    onDeleteKeyword: (Int) -> Unit,
    onUpdateCalibrationScale: (Float) -> Unit,
    onToggleCalibrationLock: (Boolean) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    onApplyReadyFont: (FontEntry) -> Unit,
    onDownloadOpenFont: (FontEntry) -> Unit,
    onImportPurchasedFont: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFontDropdownSheet by remember { mutableStateOf(false) }
    var showCalibrationSheet by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri != null) onExportSetUri(uri)
    }

    val loadStyleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onLoadStyleUri(uri)
    }

    fun handleBackToWelcome() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onRequestCloseSet()
        }
    }

    BackHandler(onBack = ::handleBackToWelcome)

    val missingFontCount = remember(fonts) {
        fonts.count { it.detectedMissingInTemplate && !it.availability.isReady }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Compact Single-Row Top App Bar ----
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
                    onClick = ::handleBackToWelcome,
                    modifier = Modifier.testTag("workspace_home_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return to MSE Welcome Window",
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = if (hasUnsavedChanges) "${archive.document.setTitle} •" else archive.document.setTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${MseTemplateCatalog.displayGameName(archive.document.game)} • ${archive.document.stylesheet}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Compact Bridge Status Dot (Green = Connected, Amber = Offline)
                BridgeStatusDotButton(
                    healthState = bridgeHealth,
                    onClick = onOpenBridgeSheet,
                )

                // Font Picker Icon (A — opens Searchable Font Dropdown directly)
                IconButton(
                    onClick = { showFontDropdownSheet = true },
                    modifier = Modifier.testTag("topbar_font_picker_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = "Open searchable font picker",
                        tint = if (missingFontCount > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }

                // Overflow Menu (⋮)
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("topbar_overflow_button"),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Set options")
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save .mse-set") },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onSaveSet()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Save As…") },
                            leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                saveAsLauncher.launch(archive.displayName.ifBlank { "set.mse-set" })
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Load .mse-style") },
                            leadingIcon = { Icon(Icons.Default.Style, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                loadStyleLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Calibrate 1:1 IRL") },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                selectedTabIndex = MseWorkspaceTab.CARDS.ordinal
                                showCalibrationSheet = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Upstream Splits") },
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
                    }
                }
            }
        }

        // ---- Classic MSE Workspace Tab Strip (Zero Truncation at 360dp) ----
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            MseWorkspaceTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.testTag("workspace_tab_${tab.name.lowercase()}"),
                )
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
                        renderState = renderState,
                        showCalibrationSheet = showCalibrationSheet,
                        onDismissCalibrationSheet = { showCalibrationSheet = false },
                        onOpenCalibrationSheet = { showCalibrationSheet = true },
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
                        bridgeTemplates = bridgeTemplates,
                        missingFontCount = missingFontCount,
                        onSwitchStylesheet = onSwitchStylesheet,
                        onLoadStyleUri = onLoadStyleUri,
                        onUpdatePhysicalProfile = onUpdatePhysicalProfile,
                        onOpenFontDropdown = { showFontDropdownSheet = true },
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
            }
        }
    }

    // ---- Searchable Font Dropdown Bottom Sheet ----
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

    // ---- Unsaved Changes Confirmation Dialog ----
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Save Changes?") },
            text = {
                Text("Save changes to \"${archive.document.setTitle}\" before returning to the Welcome Window?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        onSaveSetAndClose()
                    },
                    modifier = Modifier.testTag("unsaved_save_and_close"),
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
                        },
                        modifier = Modifier.testTag("unsaved_discard"),
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
