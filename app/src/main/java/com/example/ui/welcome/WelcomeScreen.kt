package com.example.ui.welcome

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Hub
import com.example.R
import com.example.data.mse.CatalogPackFilter
import com.example.data.mse.CatalogSyncState
import com.example.data.mse.DiscoveredGame
import com.example.data.mse.DiscoveredStyle
import com.example.data.mse.MseTemplateCatalog
import com.example.data.mse.RecentSetRecord
import com.example.domain.frameworks.UniversalCardFramework
import com.example.domain.nexus.MutualLinkStatus
import com.example.ui.BridgeHealthState
import com.example.ui.components.BridgeStatusPill

/**
 * Authentic Magic Set Editor Welcome Window (Startup Screen).
 * Refined warm-obsidian drafting studio craft with MSE emblem, live dynamic catalog status pill,
 * zero clutter, zero clipping on 360dp phones, and strictly real user recent sets.
 */
@Composable
fun WelcomeScreen(
    darkTheme: Boolean,
    isDeviceOnline: Boolean,
    bridgeHealth: BridgeHealthState,
    discoveredGames: List<DiscoveredGame>,
    discoveredStyles: List<DiscoveredStyle>,
    catalogSyncState: CatalogSyncState,
    recentSets: List<RecentSetRecord>,
    onToggleDarkTheme: (Boolean) -> Unit,
    onBackToModeChooser: () -> Unit,
    onSwitchToCardCoder: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onToggleOptInPack: (CatalogPackFilter, Boolean) -> Unit,
    onOpenBridgeSheet: () -> Unit,
    onCreateNewSet: (title: String, gameId: String, styleOption: DiscoveredStyle) -> Unit,
    onOpenSetUri: (Uri) -> Unit,
    onOpenRecentSet: (RecentSetRecord) -> Unit,
    onOpenFontsManager: () -> Unit,
    onOpenUpstreamManager: () -> Unit,
    onOpenBridgeSetup: () -> Unit,
    nexusLinkStatus: MutualLinkStatus = MutualLinkStatus.UNLINKED,
    onOpenNexusHub: () -> Unit = {},
    universalFrameworks: List<UniversalCardFramework> = emptyList(),
    onCreateSetFromFramework: (title: String, framework: UniversalCardFramework) -> Unit = { _, _ -> },
    onGenerateFrameworkScaffold: (UniversalCardFramework) -> Unit = {},
    onOpenOfficialSetComparison: () -> Unit = {},
    onOpenUsageReports: () -> Unit = {},
    betaNewCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    var showNewSetWizard by remember { mutableStateOf(false) }

    val openSetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onOpenSetUri(uri)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Top MSE Emblem Header + Engine Status Pill ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        IconButton(
                            onClick = onBackToModeChooser,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("welcome_back_to_chooser"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Mode Chooser",
                            )
                        }

                        // Clean MSE Studio Emblem
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesomeMosaic,
                                    contentDescription = "Magic Set Editor Emblem",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.welcome_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Card Designer • ${if (isDeviceOnline) "Online" else "Offline Mode"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { onToggleDarkTheme(!darkTheme) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("welcome_theme_toggle"),
                        ) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Theme",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        BridgeStatusPill(
                            healthState = bridgeHealth,
                            onClick = onOpenBridgeSheet,
                        )
                    }
                }

                // ---- Live Catalog Sync Status Pill ----
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val pillLabel = when {
                                catalogSyncState.isSyncing ->
                                    catalogSyncState.statusMessage.ifBlank { "Indexing Magic & Non-Magic packs…" }

                                discoveredStyles.isNotEmpty() ->
                                    "${discoveredGames.size} Games • ${discoveredStyles.size} Frames Synced"

                                else -> "Tap refresh to index Magic & Non-Magic packs"
                            }
                            Text(
                                text = pillLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (catalogSyncState.isSyncing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = onRefreshCatalog,
                                enabled = !catalogSyncState.isSyncing,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("welcome_refresh_catalog"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh template catalog",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        if (catalogSyncState.isSyncing) {
                            LinearProgressIndicator(
                                progress = { catalogSyncState.progressFraction.coerceIn(0.05f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // ---- Primary MSE Welcome Actions ----
        item {
            WelcomeActionCard(
                icon = Icons.Default.AddBox,
                title = stringResource(R.string.welcome_new_set),
                subtitle = stringResource(R.string.welcome_new_set_desc),
                emphasized = true,
                onClick = { showNewSetWizard = true },
                modifier = Modifier.testTag("welcome_new_set_card"),
            )
        }

        item {
            WelcomeActionCard(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.welcome_open_set),
                subtitle = stringResource(R.string.welcome_open_set_desc),
                emphasized = false,
                onClick = {
                    openSetLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                modifier = Modifier.testTag("welcome_open_set_card"),
            )
        }

        // Quick "Open Last Set" action when at least one real recent set exists
        recentSets.firstOrNull()?.let { lastSet ->
            item {
                WelcomeActionCard(
                    icon = Icons.Default.History,
                    title = "${stringResource(R.string.welcome_open_last)}: ${lastSet.title}",
                    subtitle = "${MseTemplateCatalog.displayGameName(lastSet.game, discoveredGames)} • ${lastSet.stylesheet} • ${lastSet.cardCount} card(s)",
                    emphasized = false,
                    onClick = { onOpenRecentSet(lastSet) },
                    modifier = Modifier.testTag("welcome_open_last_card"),
                )
            }
        }

        // ---- Official Sets, Comparison & Fan-Fiction Split-Off Sets ----
        item {
            WelcomeActionCard(
                icon = Icons.Default.CompareArrows,
                title = stringResource(R.string.welcome_official_sets_title),
                subtitle = stringResource(R.string.welcome_official_sets_desc),
                emphasized = false,
                onClick = onOpenOfficialSetComparison,
                modifier = Modifier.testTag("welcome_official_sets_card"),
            )
        }

        // ---- Custom Card Usage & Interaction Telemetry Reports ----
        item {
            WelcomeActionCard(
                icon = Icons.Default.Insights,
                title = stringResource(R.string.welcome_usage_reports_title),
                subtitle = stringResource(R.string.welcome_usage_reports_desc),
                emphasized = false,
                onClick = onOpenUsageReports,
                modifier = Modifier.testTag("welcome_usage_reports_card"),
            )
        }

        // ---- Secondary Tools Row ----
        item {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryToolTile(
                    icon = Icons.Default.FontDownload,
                    label = stringResource(R.string.tool_fonts),
                    onClick = onOpenFontsManager,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_tool_fonts"),
                )
                SecondaryToolTile(
                    icon = Icons.Default.CloudSync,
                    label = stringResource(R.string.tool_upstream),
                    badgeText = if (betaNewCount > 0) "$betaNewCount New Beta" else null,
                    onClick = onOpenUpstreamManager,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_tool_upstream"),
                )
                SecondaryToolTile(
                    icon = Icons.Default.Terminal,
                    label = stringResource(R.string.tool_bridge),
                    onClick = onOpenBridgeSetup,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_tool_bridge"),
                )
            }
        }

        // ---- Separate Workbench Switcher: Jump to DataEditorX Coding UI ----
        item {
            WelcomeActionCard(
                icon = Icons.Default.Code,
                title = "Switch to Coding Cards (DataEditorX UI)",
                subtitle = "Open the dedicated offline DataEditorX card database, 21-digit registers & Lua workbench",
                emphasized = false,
                onClick = onSwitchToCardCoder,
                modifier = Modifier.testTag("welcome_switch_to_coder"),
            )
        }

        // ---- Nexus Master Hub Bidirectional Sync Card ----
        item {
            WelcomeActionCard(
                icon = Icons.Default.Hub,
                title = stringResource(R.string.nexus_hub_title),
                subtitle = "${nexusLinkStatus.displayLabel} • Bidirectional GitHub Hub & Engine Sync",
                emphasized = false,
                onClick = onOpenNexusHub,
                modifier = Modifier.testTag("welcome_tool_nexus_hub"),
            )
        }

        // ---- Recent Sets Section (Strictly Real User Sets Only) ----
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.welcome_recent_sets),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (recentSets.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = stringResource(R.string.welcome_no_recent_sets),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Create a new set or open an existing .mse-set file to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(recentSets, key = { it.id }) { record ->
                RecentSetRow(
                    record = record,
                    discoveredGames = discoveredGames,
                    onClick = { onOpenRecentSet(record) },
                )
            }
        }
    }

    if (showNewSetWizard) {
        NewSetWizardDialog(
            games = discoveredGames,
            styles = discoveredStyles,
            syncState = catalogSyncState,
            onRefreshCatalog = onRefreshCatalog,
            onToggleOptInPack = onToggleOptInPack,
            onDismiss = { showNewSetWizard = false },
            onCreateSet = { title, gameId, styleOption ->
                showNewSetWizard = false
                onCreateNewSet(title, gameId, styleOption)
            },
            universalFrameworks = universalFrameworks,
            onCreateSetFromFramework = { title, framework ->
                showNewSetWizard = false
                onCreateSetFromFramework(title, framework)
            },
            onGenerateFrameworkScaffold = onGenerateFrameworkScaffold,
        )
    }
}

@Composable
private fun WelcomeActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(10.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecondaryToolTile(
    icon: ImageVector,
    label: String,
    badgeText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.testTag("welcome_beta_new_badge"),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSetRow(
    record: RecentSetRecord,
    discoveredGames: List<DiscoveredGame>,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recent_set_${record.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${MseTemplateCatalog.displayGameName(record.game, discoveredGames)} • ${record.stylesheet} • ${record.cardCount} card(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = record.lastOpenedAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
