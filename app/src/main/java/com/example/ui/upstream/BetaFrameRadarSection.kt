package com.example.ui.upstream

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.upstream.BetaFrameCategory
import com.example.data.upstream.BetaFrameCodePackage
import com.example.data.upstream.BetaFrameRadarRepository
import com.example.data.upstream.BetaRadarUiState
import com.example.data.upstream.DiscoveredBetaFrameItem
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnectedContainer
import com.example.ui.theme.StatusOfflineAmber
import com.example.ui.theme.StatusOfflineAmberContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BetaRadarFilter(val label: String) {
    ALL("All"),
    UNIQUE_NO_TEMPLATE("Unique No-Template"),
    FAN_YGO_CUSTOM("Fan YGO Customs"),
    NEW_GAME("New Games"),
    CURRENT_GAME("Current Games"),
    FAN_STYLE("Fan & Art Styles"),
    BETA_ONLY("Beta Only"),
    NEW_ONLY("New Only"),
}

private val LOGIN_WALLED_DISCOVERY_SITES = listOf(
    "ArtStation" to "https://www.artstation.com/search?sort_by=date&query=tcg%20card%20frame%20template",
    "Pixiv" to "https://www.pixiv.net/en/tags/TCG%20template/artworks",
    "Pinterest" to "https://www.pinterest.com/search/pins/?q=custom%20yugioh%20tcg%20card%20frame%20template",
)

/**
 * Foreground-Active Automatic Multi-Site Beta Card Frame Radar UI.
 *
 * Mounted as Tab 0 inside [UpstreamScreen]. Keeps all fan-made source trails strictly
 * inside the downloadable `.mse-style-beta.txt` code package and inline `#` code comments—
 * never rendered on the card preview or StyleTabContent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BetaFrameRadarSection(
    radarState: BetaRadarUiState,
    isDeviceOnline: Boolean,
    onCheckNow: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onMarkAllSeen: () -> Unit,
    onAddCustomFeed: (label: String, target: String, category: BetaFrameCategory) -> Unit,
    onRemoveCustomFeed: (feedId: String) -> Unit,
    onInspectCode: (DiscoveredBetaFrameItem) -> Unit,
    onDismissCodeDialog: () -> Unit,
    onSaveCodeToUri: (Uri, BetaFrameCodePackage) -> Unit,
    onApplyBetaCodeToSet: (BetaFrameCodePackage) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf(BetaRadarFilter.ALL) }
    var showAddSitePanel by rememberSaveable { mutableStateOf(false) }
    var customFeedLabel by rememberSaveable { mutableStateOf("") }
    var customFeedTarget by rememberSaveable { mutableStateOf("") }
    var customFeedCategory by rememberSaveable { mutableStateOf(BetaFrameCategory.FAN_YGO_CUSTOM) }

    var pendingExportPackage by remember { mutableStateOf<BetaFrameCodePackage?>(null) }

    val saveBetaCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val pkg = pendingExportPackage
        if (uri != null && pkg != null) {
            onSaveCodeToUri(uri, pkg)
        }
        pendingExportPackage = null
    }

    val filteredItems = remember(radarState.items, selectedFilter) {
        when (selectedFilter) {
            BetaRadarFilter.ALL -> radarState.items
            BetaRadarFilter.UNIQUE_NO_TEMPLATE -> radarState.items.filter { it.isEligibleForUniqueNoTemplateSynthesis() }
            BetaRadarFilter.FAN_YGO_CUSTOM -> radarState.items.filter { it.category == BetaFrameCategory.FAN_YGO_CUSTOM }
            BetaRadarFilter.NEW_GAME -> radarState.items.filter { it.category == BetaFrameCategory.NEW_GAME }
            BetaRadarFilter.CURRENT_GAME -> radarState.items.filter { it.category == BetaFrameCategory.CURRENT_GAME }
            BetaRadarFilter.FAN_STYLE -> radarState.items.filter { it.category == BetaFrameCategory.FAN_STYLE }
            BetaRadarFilter.BETA_ONLY -> radarState.items.filter { it.isAlwaysBeta || it.originTrust.alwaysBeta }
            BetaRadarFilter.NEW_ONLY -> radarState.items.filter { it.isNewSinceLastSeen }
        }
    }

    val customFeeds = remember(radarState.feeds) {
        radarState.feeds.filter { !it.isBuiltIn }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Foreground-Only Radar Status Header ----
        item {
            val activeOnline = isDeviceOnline && radarState.isAppInForeground
            val accentColor = if (activeOnline) StatusConnected else StatusOfflineAmber
            val containerColor = if (activeOnline) {
                StatusConnectedContainer.copy(alpha = 0.55f)
            } else {
                StatusOfflineAmberContainer.copy(alpha = 0.65f)
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = containerColor,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta_radar_status_card"),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isDeviceOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = "Beta Radar Connectivity",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDeviceOnline) {
                                    "Active While App Open • Online"
                                } else {
                                    "Offline — Cached Radar"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val lastCheckedLabel = if (radarState.lastCheckedEpochMs > 0L) {
                                val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)
                                "Last scan: ${fmt.format(Date(radarState.lastCheckedEpochMs))} • ${radarState.feeds.size} feeds"
                            } else {
                                "${radarState.feeds.size} verified public feeds ready (15-min foreground cooldown)"
                            }
                            Text(
                                text = lastCheckedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = radarState.lastStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Check While Open",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Foreground only (pauses immediately when app closes)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = radarState.autoCheckWhenOnline,
                            onCheckedChange = onToggleAutoCheck,
                            modifier = Modifier.testTag("beta_radar_autocheck_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onCheckNow,
                            enabled = isDeviceOnline && !radarState.isScanning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("beta_radar_check_now_button"),
                        ) {
                            if (radarState.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Scanning Feeds…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isDeviceOnline) "Check Now" else "Offline",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onMarkAllSeen,
                            enabled = radarState.newCount > 0,
                            modifier = Modifier.testTag("beta_radar_mark_seen_button"),
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark Seen (${radarState.newCount})")
                        }
                    }
                }
            }
        }

        // ---- Category & Status Filter Chips ----
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BetaRadarFilter.entries.forEach { filter ->
                    val badgeCount = when (filter) {
                        BetaRadarFilter.UNIQUE_NO_TEMPLATE -> radarState.uniqueNoTemplateCount
                        BetaRadarFilter.NEW_ONLY -> radarState.newCount
                        else -> null
                    }
                    val labelText = if (badgeCount != null && badgeCount > 0) {
                        "${filter.label} ($badgeCount)"
                    } else {
                        filter.label
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        modifier = Modifier.testTag("beta_radar_filter_${filter.name.lowercase(Locale.US)}"),
                    )
                }
            }
        }

        // ---- Login-Walled Site Shortcuts + Expandable "+ Add Site / Feed" ----
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Browse Login-Walled Art Sites & Custom Feeds",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        AssistChip(
                            onClick = { showAddSitePanel = !showAddSitePanel },
                            label = { Text("+ Add Site / Feed", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showAddSitePanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.testTag("beta_radar_toggle_add_site"),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LOGIN_WALLED_DISCOVERY_SITES.forEach { (siteLabel, siteUrl) ->
                            AssistChip(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl)))
                                    }.onFailure { err ->
                                        Log.w("BetaFrameRadarSection", "Failed opening $siteLabel: ${err.message}", err)
                                        onNotify("No browser available to open $siteLabel.")
                                    }
                                },
                                label = { Text(siteLabel, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open $siteLabel in browser",
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                modifier = Modifier.testTag("beta_radar_ext_${siteLabel.lowercase(Locale.US)}"),
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAddSitePanel) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            OutlinedTextField(
                                value = customFeedLabel,
                                onValueChange = { customFeedLabel = it },
                                label = { Text("Site / Feed Label (optional)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("beta_radar_custom_label_input"),
                            )
                            OutlinedTextField(
                                value = customFeedTarget,
                                onValueChange = { customFeedTarget = it },
                                label = { Text("GitHub owner/repo, DeviantArt user/query, or RSS URL") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("beta_radar_custom_target_input"),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                BetaFrameCategory.entries.forEach { cat ->
                                    FilterChip(
                                        selected = customFeedCategory == cat,
                                        onClick = { customFeedCategory = cat },
                                        label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onAddCustomFeed(customFeedLabel, customFeedTarget, customFeedCategory)
                                    customFeedLabel = ""
                                    customFeedTarget = ""
                                    showAddSitePanel = false
                                },
                                enabled = customFeedTarget.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("beta_radar_save_custom_feed_button"),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Site / Feed to Beta Radar")
                            }

                            if (customFeeds.isNotEmpty()) {
                                Text(
                                    text = "Custom Feeds (${customFeeds.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    customFeeds.forEach { cf ->
                                        AssistChip(
                                            onClick = { onRemoveCustomFeed(cf.id) },
                                            label = { Text(cf.label, style = MaterialTheme.typography.labelSmall) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove ${cf.label}",
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Empty State or Discovered Beta Frame Cards ----
        if (filteredItems.isEmpty()) {
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
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = if (radarState.items.isEmpty()) {
                                "No cached Beta Radar frames yet"
                            } else {
                                "No frames match '${selectedFilter.label}'"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = if (isDeviceOnline) {
                                "Tap 'Check Now' to scan MSE packs, DataEditorX, OmegaDEX, GitHub YGO/TCG repos, DeviantArt, Itch.io, Reddit, and OpenGameArt."
                            } else {
                                "Connect online while the app is open to scan all 16 verified public feeds and cache results locally."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                BetaFrameRadarItemCard(
                    item = item,
                    isLoadingCode = radarState.isLoadingCodePackage,
                    onInspectCode = { onInspectCode(item) },
                )
            }
        }
    }

    // ---- Monospace Beta Frame Code Viewer Dialog ----
    val activePackage = radarState.activeCodePackage
    if (activePackage != null) {
        BetaFrameCodeViewerDialog(
            codePackage = activePackage,
            onDismiss = onDismissCodeDialog,
            onCopyCode = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Beta Frame Code", activePackage.formattedDownloadableText)
                )
                onNotify("Copied Beta Frame code package to clipboard.")
            },
            onDownloadCodeFile = {
                pendingExportPackage = activePackage
                val safeName = BetaFrameRadarRepository.sanitizeKey(activePackage.item.displayName).take(40)
                saveBetaCodeLauncher.launch("$safeName.mse-style-beta.txt")
            },
            onApplyToSet = {
                onApplyBetaCodeToSet(activePackage)
                onDismissCodeDialog()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BetaFrameRadarItemCard(
    item: DiscoveredBetaFrameItem,
    isLoadingCode: Boolean,
    onInspectCode: () -> Unit,
) {
    val isBeta = item.isAlwaysBeta || item.originTrust.alwaysBeta
    val badgeColor = if (isBeta) StatusOfflineAmber else StatusConnected
    val assessment = item.uniqueUseAssessment
    val canSynthesize = item.isEligibleForUniqueNoTemplateSynthesis()
    val canInspect = item.hasUpstreamTemplateFile || canSynthesize

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isNewSinceLastSeen) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("beta_radar_item_${item.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
                ) {
                    Text(
                        text = item.effectiveStatusBadge(),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                if (item.isNewSinceLastSeen) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }

                if (canSynthesize && assessment != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Unique Use: ${assessment.kind.label} (Auto-Code Ready)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${item.sourceFeedLabel} • ${item.category.label} • game: ${item.gameId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.descriptionSnippet.isNotBlank()) {
                Text(
                    text = item.descriptionSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onInspectCode,
                    enabled = canInspect && !isLoadingCode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("beta_radar_inspect_${item.id}"),
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            item.hasUpstreamTemplateFile -> "Inspect & Download Code"
                            canSynthesize -> "Synthesize & Download Beta Code"
                            else -> "Generic Art (No Unique Frame)"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BetaFrameCodeViewerDialog(
    codePackage: BetaFrameCodePackage,
    onDismiss: () -> Unit,
    onCopyCode: () -> Unit,
    onDownloadCodeFile: () -> Unit,
    onApplyToSet: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = codePackage.item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${codePackage.item.effectiveStatusBadge()} • ${codePackage.parsedGeometrySummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusOfflineAmber,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (codePackage.wasAutoSynthesizedForUniqueUse) {
                        "Auto-synthesized MSE style & game fields for ${codePackage.uniqueUseAssessment.kind.label} (source trail embedded in code)."
                    } else {
                        "Verbatim upstream MSE style template (fan source trail embedded in code when fan-made)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp),
                ) {
                    Text(
                        text = codePackage.formattedDownloadableText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onCopyCode,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("beta_code_copy_button"),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Code", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onDownloadCodeFile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("beta_code_download_button"),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download .txt", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApplyToSet,
                modifier = Modifier.testTag("beta_code_apply_button"),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apply Beta to Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
