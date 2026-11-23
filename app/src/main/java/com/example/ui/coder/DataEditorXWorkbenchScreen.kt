package com.example.ui.coder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.NexusClientDefinition
import com.example.domain.nexus.NexusCodedCard
import com.example.ui.nexus.NexusHubUiState
import com.example.ui.nexus.NexusHubViewModel
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber
import com.example.ui.theme.TcgStudioTheme
import com.example.ui.theme.WorkspaceVisualTheme
import com.example.ui.welcome.ConnectivityPill

private enum class DexWorkbenchTab(val labelRes: Int) {
    DATABASE(R.string.dex_tab_database),
    INSPECTOR(R.string.dex_tab_inspector),
    SCRIPT(R.string.dex_tab_script),
}

/**
 * Dedicated Offline-First DataEditorX-Style Card Coding UI.
 *
 * Visually and structurally separated from the Magic Set Editor Card Making UI:
 * - Own DataEditorX dark slate/cyan IDE color scheme (`WorkspaceVisualTheme.DATA_EDITOR_X_CODER`).
 * - Adaptive 2-pane split (Card DB left + Inspector/Lua right) on tablets,
 *   or crisp 3-tab IDE layout (`Card DB | Inspector | Lua / Export`) on compact phones.
 * - 100% offline local database, validation, and `.lua`/`.json` export; upstream
 *   DataEditorX/MSE comparisons and GitHub Hub pushes remain strictly online-only.
 */
@Composable
fun DataEditorXWorkbenchScreen(
    darkTheme: Boolean,
    isDeviceOnline: Boolean,
    uiState: NexusHubUiState,
    viewModel: NexusHubViewModel,
    activeMseCardName: String?,
    activeMseCardType: String?,
    activeMseCardRules: String?,
    onToggleDarkTheme: (Boolean) -> Unit,
    onBackToModeChooser: () -> Unit,
    onSwitchToDesigner: () -> Unit,
    onOpenUpstreamCompare: () -> Unit,
    onOpenNexusHubSync: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TcgStudioTheme(
        darkTheme = darkTheme,
        workspaceTheme = WorkspaceVisualTheme.DATA_EDITOR_X_CODER,
    ) {
        val context = LocalContext.current
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
        var tabletRightTabIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = Inspector, 1 = Lua/Export
        var showOverflow by remember { mutableStateOf(false) }

        val savePackJsonLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri: Uri? ->
            if (uri != null) {
                val json = viewModel.buildCurrentExportPackJson()
                if (json != null) {
                    viewModel.writeUtf8ToUri(context, uri, json, "card pack JSON")
                } else {
                    onNotify("Fix blocking validation errors before exporting pack JSON.")
                }
            }
        }

        val saveLuaLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri: Uri? ->
            if (uri != null) {
                val lua = uiState.generatedLuaPreview
                if (!lua.isNullOrBlank()) {
                    viewModel.writeUtf8ToUri(context, uri, lua, "Lua script")
                } else {
                    onNotify("Generate a valid card first to save a Lua script.")
                }
            }
        }

        fun copyText(label: String, text: String, confirmation: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            onNotify(confirmation)
        }

        val selectedClient = uiState.selectedClient
        val report = uiState.validationReport
        val errorCount = report?.blockingErrors?.size ?: 0

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ---- DataEditorX IDE Top Toolbar ----
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
                        onClick = onBackToModeChooser,
                        modifier = Modifier.testTag("dex_back_to_chooser"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Mode Chooser",
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dex_workbench_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = selectedClient?.name ?: "No writable client selected",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 1-tap switch to Card Making (Magic Set Editor UI)
                    OutlinedButton(
                        onClick = onSwitchToDesigner,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.testTag("dex_switch_to_mse"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "MSE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    IconButton(
                        onClick = { onToggleDarkTheme(!darkTheme) },
                        modifier = Modifier.testTag("dex_theme_toggle"),
                    ) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark Theme",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflow = true },
                            modifier = Modifier.testTag("dex_overflow_menu"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More DataEditorX actions",
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Pack JSON…") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    val slug = uiState.packName.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").ifBlank { "card-pack" }
                                    savePackJsonLauncher.launch("$slug.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export Lua Script (.lua)…") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    val code = uiState.register21Digit.ifBlank { uiState.cardId }.ifBlank { "card" }
                                    saveLuaLauncher.launch("c$code.lua")
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Compare DataEditorX & MSE Updates") },
                                leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    onOpenUpstreamCompare()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Nexus Hub & Engine Sync") },
                                leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    onOpenNexusHubSync()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Switch to Card Designer (MSE)") },
                                leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    onSwitchToDesigner()
                                },
                            )
                        }
                    }
                }
            }

            // ---- DataEditorX Compact IDE Status Strip ----
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ConnectivityPill(isOnline = isDeviceOnline)
                    DexStatusTag(
                        label = "DB: ${uiState.savedCodedCards.size} card(s)",
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    DexStatusTag(
                        label = if (report?.isValid == true) "VALID" else "$errorCount ERR",
                        accent = if (report?.isValid == true) StatusConnected else MaterialTheme.colorScheme.error,
                    )
                    DexStatusTag(
                        label = if (uiState.editorMode == CardEditorMode.MATCH_GAMEPLAY) "MATCH MODE" else "DOSSIER MODE",
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---- Adaptive DataEditorX Workspace Body ----
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isSplitPane = maxWidth >= 840.dp

                if (isSplitPane) {
                    // Classic Desktop DataEditorX 2-Column Split: Card DB Left | Inspector / Lua Right
                    Row(modifier = Modifier.fillMaxSize()) {
                        DataEditorXDatabasePane(
                            uiState = uiState,
                            viewModel = viewModel,
                            activeMseCardName = activeMseCardName,
                            activeMseCardType = activeMseCardType,
                            activeMseCardRules = activeMseCardRules,
                            onCardSelectedForEdit = { card ->
                                viewModel.loadStagedCardForEditing(card)
                                tabletRightTabIndex = 0
                            },
                            onStartNewCard = {
                                viewModel.startNewBlankCodedCard()
                                tabletRightTabIndex = 0
                            },
                            modifier = Modifier
                                .weight(0.38f)
                                .fillMaxHeight(),
                        )

                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Column(
                            modifier = Modifier
                                .weight(0.62f)
                                .fillMaxHeight(),
                        ) {
                            DexSubTabBar(
                                tabs = listOf(
                                    stringResource(R.string.dex_tab_inspector),
                                    stringResource(R.string.dex_tab_script),
                                ),
                                selectedIndex = tabletRightTabIndex,
                                onSelect = { tabletRightTabIndex = it },
                            )
                            if (tabletRightTabIndex == 0) {
                                DataEditorXInspectorPane(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onStagedOpenScript = { tabletRightTabIndex = 1 },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                DataEditorXScriptExportPane(
                                    isDeviceOnline = isDeviceOnline,
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onCopyLua = { lua -> copyText("card.lua", lua, "Copied Lua script to clipboard.") },
                                    onSaveLua = {
                                        val code = uiState.register21Digit.ifBlank { uiState.cardId }.ifBlank { "card" }
                                        saveLuaLauncher.launch("c$code.lua")
                                    },
                                    onSavePackJson = {
                                        val slug = uiState.packName.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").ifBlank { "card-pack" }
                                        savePackJsonLauncher.launch("$slug.json")
                                    },
                                    onOpenNexusHubSync = onOpenNexusHubSync,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    // Compact Phone: 3 Crisp DataEditorX IDE Tabs (Card DB | Inspector | Lua / Export)
                    Column(modifier = Modifier.fillMaxSize()) {
                        DexSubTabBar(
                            tabs = DexWorkbenchTab.entries.map { stringResource(it.labelRes) },
                            selectedIndex = selectedTabIndex,
                            onSelect = { selectedTabIndex = it },
                        )

                        when (DexWorkbenchTab.entries[selectedTabIndex]) {
                            DexWorkbenchTab.DATABASE -> DataEditorXDatabasePane(
                                uiState = uiState,
                                viewModel = viewModel,
                                activeMseCardName = activeMseCardName,
                                activeMseCardType = activeMseCardType,
                                activeMseCardRules = activeMseCardRules,
                                onCardSelectedForEdit = { card ->
                                    viewModel.loadStagedCardForEditing(card)
                                    selectedTabIndex = 1
                                },
                                onStartNewCard = {
                                    viewModel.startNewBlankCodedCard()
                                    selectedTabIndex = 1
                                },
                                modifier = Modifier.weight(1f),
                            )

                            DexWorkbenchTab.INSPECTOR -> DataEditorXInspectorPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                onStagedOpenScript = { selectedTabIndex = 2 },
                                modifier = Modifier.weight(1f),
                            )

                            DexWorkbenchTab.SCRIPT -> DataEditorXScriptExportPane(
                                isDeviceOnline = isDeviceOnline,
                                uiState = uiState,
                                viewModel = viewModel,
                                onCopyLua = { lua -> copyText("card.lua", lua, "Copied Lua script to clipboard.") },
                                onSaveLua = {
                                    val code = uiState.register21Digit.ifBlank { uiState.cardId }.ifBlank { "card" }
                                    saveLuaLauncher.launch("c$code.lua")
                                },
                                onSavePackJson = {
                                    val slug = uiState.packName.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").ifBlank { "card-pack" }
                                    savePackJsonLauncher.launch("$slug.json")
                                },
                                onOpenNexusHubSync = onOpenNexusHubSync,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DexSubTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEachIndexed { idx, label ->
                    val selected = idx == selectedIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(idx) }
                            .padding(vertical = 10.dp)
                            .testTag("dex_tab_$idx"),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(2.5.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface,
                                ),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun DataEditorXDatabasePane(
    uiState: NexusHubUiState,
    viewModel: NexusHubViewModel,
    activeMseCardName: String?,
    activeMseCardType: String?,
    activeMseCardRules: String?,
    onCardSelectedForEdit: (NexusCodedCard) -> Unit,
    onStartNewCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var lockedFirewallClient by remember { mutableStateOf<NexusClientDefinition?>(null) }

    val filteredCards = remember(uiState.savedCodedCards, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) uiState.savedCodedCards
        else uiState.savedCodedCards.filter { c ->
            c.cardName.lowercase().contains(q) ||
                c.cardId.lowercase().contains(q) ||
                c.register21Digit.orEmpty().lowercase().contains(q) ||
                c.cardType.lowercase().contains(q) ||
                c.targetClientId.lowercase().contains(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- Target Non-Locked Client Selector ----
        DexInspectorGroupCard(title = "TARGET CLIENT (NON-LOCKED FIREWALL)") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                uiState.appliedIndex.clients.forEach { client ->
                    val isSelected = client.id == uiState.selectedClientId
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (client.upstreamSourceLock) {
                                lockedFirewallClient = client
                            } else {
                                lockedFirewallClient = null
                                viewModel.selectClient(client.id)
                            }
                        },
                        leadingIcon = if (client.upstreamSourceLock) {
                            {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Locked upstream client",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else null,
                        label = {
                            Text(
                                text = client.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.testTag("dex_client_${client.id}"),
                    )
                }
            }

            val blocked = lockedFirewallClient
            if (blocked != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Locked-Client Firewall: '${blocked.name}' is read-only.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Button(
                            onClick = {
                                viewModel.redirectFromLockedClientToFork(blocked)
                                lockedFirewallClient = null
                            },
                            modifier = Modifier.testTag("dex_redirect_locked_fork"),
                        ) {
                            Text("Target Non-Locked 21-Digit Fork Instead", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // ---- Search & Quick Create Bar ----
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            placeholder = { Text("Search Code / 21-Digit / Name / Type…") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dex_db_search"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onStartNewCard,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dex_new_card_button"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Record", style = MaterialTheme.typography.labelMedium)
            }

            if (!activeMseCardName.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        viewModel.prefillFromActiveMseCard(
                            name = activeMseCardName,
                            type = activeMseCardType.orEmpty(),
                            rulesText = activeMseCardRules.orEmpty(),
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dex_import_mse_card_button"),
                ) {
                    Text(
                        text = "Pull MSE Text",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ---- DataEditorX Card Database Table ----
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (filteredCards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (uiState.savedCodedCards.isEmpty()) {
                                "Local Card Database is Empty"
                            } else {
                                "No cards match \"$searchQuery\""
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Tap 'New Record' to code a card in the DataEditorX Inspector and stage it locally offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "CODE / NAME",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "ACTIONS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    items(filteredCards, key = { it.cardId }) { card ->
                        val isActiveInEditor = card.cardId == uiState.cardId && uiState.cardId.isNotBlank()
                        Surface(
                            color = if (isActiveInEditor) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCardSelectedForEdit(card) }
                                .testTag("dex_db_row_${card.cardId}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = card.register21Digit ?: card.cardId,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = card.cardName.ifBlank { "(Untitled Card)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(card.cardType.ifBlank { "No type" })
                                            if (card.colorPiePips.isNotEmpty()) {
                                                append(" • ")
                                                append(card.colorPiePips.joinToString(""))
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.duplicateStagedCard(card) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Duplicate ${card.cardName}",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeStagedCard(card.cardId) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete ${card.cardName}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DataEditorXScriptExportPane(
    isDeviceOnline: Boolean,
    uiState: NexusHubUiState,
    viewModel: NexusHubViewModel,
    onCopyLua: (String) -> Unit,
    onSaveLua: () -> Unit,
    onSavePackJson: () -> Unit,
    onOpenNexusHubSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val luaText = uiState.generatedLuaPreview.orEmpty()
    val report = uiState.validationReport

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- DataEditorX Monospace Lua Script Viewer ----
        DexInspectorGroupCard(title = "LUA / SVAR SCRIPT PREVIEW") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (luaText.isBlank()) {
                    Text(
                        text = "-- Enter a Card ID / 21-digit register and Card Name in the Inspector to generate script output.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        luaText.lines().forEachIndexed { idx, line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "${idx + 1}".padStart(3, ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                )
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (line.trimStart().startsWith("--")) {
                                        StatusConnected
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { if (luaText.isNotBlank()) onCopyLua(luaText) },
                    enabled = luaText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dex_copy_lua_button"),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Lua")
                }
                OutlinedButton(
                    onClick = onSaveLua,
                    enabled = luaText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dex_save_lua_button"),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save .lua")
                }
            }
        }

        // ---- Guardrail Diagnostics Console ----
        DexInspectorGroupCard(title = "GUARDRAIL DIAGNOSTICS CONSOLE") {
            val issues = report?.issues.orEmpty()
            if (issues.isEmpty()) {
                Text(
                    text = "OK • Zero guardrail errors or warnings. Ready for local pack export.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = StatusConnected,
                )
            } else {
                issues.forEach { issue ->
                    val color = if (issue.isBlocking) {
                        MaterialTheme.colorScheme.error
                    } else {
                        StatusOfflineAmber
                    }
                    val level = if (issue.isBlocking) "ERROR" else "WARN"
                    Text(
                        text = "[$level] ${issue.code}: ${issue.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                    )
                }
            }
        }

        // ---- Offline Local Pack Export & Online Hub Push ----
        DexInspectorGroupCard(title = "LOCAL PACK EXPORT & NEXUS HUB PUSH") {
            Button(
                onClick = onSavePackJson,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dex_export_pack_json_button"),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export Card Pack JSON (100% Offline)")
            }

            if (!isDeviceOnline) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = StatusOfflineAmber,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Offline — Direct GitHub push & upstream DataEditorX comparison unlock automatically when officially online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::commitCardPackToLinkedHub,
                        enabled = uiState.isMutualLinkVerified && !uiState.isCommitting,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dex_push_hub_button"),
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Push Pack to Hub", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onOpenNexusHubSync,
                        modifier = Modifier.testTag("dex_open_hub_sync"),
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hub Sync", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DexStatusTag(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
