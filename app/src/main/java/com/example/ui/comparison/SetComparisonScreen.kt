package com.example.ui.comparison

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.mse.MseSetArchive
import com.example.domain.comparison.BalanceOutlierFlag
import com.example.domain.comparison.CardDiffEntry
import com.example.domain.comparison.CardOrigin
import com.example.domain.comparison.ComparableCardRecord
import com.example.domain.comparison.ComparableSet
import com.example.domain.comparison.ComparisonCategory
import com.example.domain.comparison.OfficialSetSummary
import com.example.domain.comparison.SetComparisonResult
import com.example.domain.comparison.SplitOffKind
import com.example.domain.comparison.SplitOffSetManifest
import com.example.domain.frameworks.UniversalCardFramework
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnectedContainer
import com.example.ui.theme.StatusOfflineAmber
import com.example.ui.theme.StatusOfflineAmberContainer

/**
 * Official Set Puller, Real-File Set Comparison Engine & Fan-Fiction Split-Off Set Forker.
 *
 * Strictly pulls REAL official sets from Scryfall, YGOPRODeck, Pokémon TCG API, Open-Resource URLs,
 * and local SAF real files (.mse-set, SQLite .cdb, Card-Forge .txt, Nexus JSON).
 * Zero mock sets, zero auto-copied official art (`image` remains strictly null).
 */
@Composable
fun SetComparisonScreen(
    uiState: SetComparisonUiState,
    activeArchive: MseSetArchive?,
    splitOffManifest: SplitOffSetManifest?,
    universalFrameworks: List<UniversalCardFramework>,
    isDeviceOnline: Boolean,
    onSelectSourceTab: (OfficialSourceTab) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCustomOpenUrlChanged: (String) -> Unit,
    onRefreshSets: () -> Unit,
    onSelectOfficialSet: (OfficialSetSummary) -> Unit,
    onLoadLocalFileUri: (Uri) -> Unit,
    onLoadOpenResourceUrl: (String) -> Unit,
    onRecomputeComparison: () -> Unit,
    onCopyReferenceBaseline: (ComparableCardRecord) -> Unit,
    onCreateSplitOffSet: (
        officialSet: ComparableSet,
        title: String,
        kind: SplitOffKind,
        divergenceNotes: String,
        targetFramework: UniversalCardFramework?,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSplitOffWizard by remember { mutableStateOf(false) }
    var detailSubTab by remember { mutableIntStateOf(0) } // 0 = Diffs, 1 = Balance & Curve, 2 = Official Cards
    var diffCategoryFilter by remember { mutableStateOf<ComparisonCategory?>(null) }

    val localFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onLoadLocalFileUri(uri)
    }

    LaunchedEffect(uiState.activeSourceTab) {
        if (uiState.activeSourceTab != OfficialSourceTab.LOCAL_FILES &&
            uiState.setsBySource[uiState.activeSourceTab].isNullOrEmpty() &&
            isDeviceOnline
        ) {
            onRefreshSets()
        }
    }

    LaunchedEffect(activeArchive?.id, activeArchive?.document?.cards?.size) {
        onRecomputeComparison()
    }

    val currentSets = uiState.setsBySource[uiState.activeSourceTab].orEmpty()
    val filteredSets = remember(currentSets, uiState.searchQuery) {
        val q = uiState.searchQuery.trim().lowercase()
        if (q.isEmpty()) currentSets.take(80)
        else currentSets.filter {
            it.setName.lowercase().contains(q) || it.setId.lowercase().contains(q)
        }.take(80)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("set_comparison_screen"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Active Set Context Banner ----
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeArchive != null) {
                                "Comparing Active Set: ${activeArchive.document.setTitle} (${activeArchive.document.cards.size} cards)"
                            } else {
                                "Pull any Official Set to inspect or fork as a Fan-Fiction Split-Off Set"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Real public APIs + local SAF files (.mse-set, .cdb, Forge .txt, JSON) • Zero copyrighted art copied",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- Official Source Selector Chips ----
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OfficialSourceTab.entries.forEach { tab ->
                    FilterChip(
                        selected = uiState.activeSourceTab == tab,
                        onClick = { onSelectSourceTab(tab) },
                        label = { Text(tab.label, maxLines = 1) },
                        modifier = Modifier.testTag("official_source_tab_${tab.name.lowercase()}"),
                    )
                }
            }
        }

        // ---- Source Controls (Online Catalog Search vs Local Real Files) ----
        if (uiState.activeSourceTab == OfficialSourceTab.LOCAL_FILES) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Load Real Local Card File (Android SAF)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Supports .mse-set archives, Yu-Gi-Oh!/DataEditorX SQLite .cdb databases (datas + texts), Card-Forge cardsfolder/*.txt scripts, and Nexus JSON card packs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { localFileLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("comparison_load_local_file_button"),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose .mse-set / .cdb / .txt / .json File")
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "Or Pull Open-Resource Public Set JSON URL",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        OutlinedTextField(
                            value = uiState.customOpenUrl,
                            onValueChange = onCustomOpenUrlChanged,
                            placeholder = { Text("https://…/set.json") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("comparison_open_url_input"),
                        )
                        OutlinedButton(
                            enabled = uiState.customOpenUrl.trim().startsWith("https://") && !uiState.isLoadingCards,
                            onClick = { onLoadOpenResourceUrl(uiState.customOpenUrl) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("comparison_pull_open_url_button"),
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pull Open-Resource JSON")
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text("Search ${uiState.activeSourceTab.label} sets…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comparison_set_search_input"),
                    )
                    IconButton(
                        onClick = onRefreshSets,
                        enabled = !uiState.isLoadingSets,
                        modifier = Modifier.testTag("comparison_refresh_sets_button"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh official sets")
                    }
                }
            }

            if (uiState.isLoadingSets) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = StatusOfflineAmberContainer,
                        border = BorderStroke(1.dp, StatusOfflineAmber.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            // Compact horizontal picker of real pulled sets
            if (filteredSets.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${filteredSets.size} Official Sets (${uiState.activeSourceTab.label})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filteredSets.take(30).forEach { summary ->
                                val isLoaded = uiState.selectedOfficialSet?.setId == summary.setId &&
                                    uiState.selectedOfficialSet.source == summary.source
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isLoaded) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    border = BorderStroke(
                                        width = if (isLoaded) 1.5.dp else 1.dp,
                                        color = if (isLoaded) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                    modifier = Modifier
                                        .clickable { onSelectOfficialSet(summary) }
                                        .testTag("official_set_card_${summary.setId}"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = summary.setName,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${summary.cardCount} cards • ${summary.releasedAt.ifBlank { summary.gameId }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isLoadingCards) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Pulling real official set cards…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- Loaded Official Set Actions + Fork as Fan-Fiction Split-Off Set ----
        val loadedOfficial = uiState.selectedOfficialSet
        if (loadedOfficial != null) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loadedOfficial.setName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${loadedOfficial.cards.size} Official Cards • Source: ${loadedOfficial.source} • Metadata only (art null)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Button(
                            onClick = { showSplitOffWizard = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fork_split_off_set_button"),
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fork as Fan-Fiction / Custom Split-Off Set")
                        }
                    }
                }
            }

            // ---- Canon Lore Preservation Audit Banner (if comparing against an active set) ----
            val comparison = uiState.comparisonResult
            if (comparison != null) {
                item {
                    CanonRetentionAuditCard(comparison = comparison)
                }
            }

            // ---- Sub-tabs: Card Diffs | Balance & Curve | Official Cards ----
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (comparison != null) {
                        FilterChip(
                            selected = detailSubTab == 0,
                            onClick = { detailSubTab = 0 },
                            label = { Text("Card Diffs (${comparison.cardDiffs.size})") },
                            modifier = Modifier.testTag("comparison_subtab_diffs"),
                        )
                        FilterChip(
                            selected = detailSubTab == 1,
                            onClick = { detailSubTab = 1 },
                            label = { Text("Balance & Outliers (${comparison.outlierFlags.size})") },
                            modifier = Modifier.testTag("comparison_subtab_balance"),
                        )
                    }
                    FilterChip(
                        selected = detailSubTab == 2 || comparison == null,
                        onClick = { detailSubTab = 2 },
                        label = { Text("Official Cards (${loadedOfficial.cards.size})") },
                        modifier = Modifier.testTag("comparison_subtab_official_cards"),
                    )
                }
            }

            if (comparison != null && detailSubTab == 0) {
                // Category Filter Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = diffCategoryFilter == null,
                            onClick = { diffCategoryFilter = null },
                            label = { Text("All") },
                        )
                        ComparisonCategory.entries.forEach { cat ->
                            val count = comparison.cardDiffs.count { it.category == cat }
                            if (count > 0) {
                                FilterChip(
                                    selected = diffCategoryFilter == cat,
                                    onClick = { diffCategoryFilter = if (diffCategoryFilter == cat) null else cat },
                                    label = { Text("${cat.displayLabel} ($count)") },
                                )
                            }
                        }
                    }
                }

                val filteredDiffs = comparison.cardDiffs.filter {
                    diffCategoryFilter == null || it.category == diffCategoryFilter
                }
                items(filteredDiffs.take(150)) { entry ->
                    CardDiffRowCard(
                        entry = entry,
                        canCopyBaseline = activeArchive != null,
                        onCopyBaseline = onCopyReferenceBaseline,
                    )
                }
            } else if (comparison != null && detailSubTab == 1) {
                item {
                    BalanceAnalyticsPanel(comparison = comparison)
                }
            } else {
                items(loadedOfficial.cards.take(150), key = { "${it.id}_${it.name}" }) { card ->
                    OfficialCardPreviewRow(
                        card = card,
                        canCopyBaseline = activeArchive != null,
                        onCopyBaseline = { onCopyReferenceBaseline(card) },
                    )
                }
            }
        }
    }

    // ---- Fan-Fiction / Custom Split-Off Set Wizard Dialog ----
    if (showSplitOffWizard && loadedOfficial != null) {
        FanFictionSplitOffWizardDialog(
            officialSet = loadedOfficial,
            frameworks = universalFrameworks,
            onDismiss = { showSplitOffWizard = false },
            onConfirmCreate = { title, kind, notes, fw ->
                showSplitOffWizard = false
                onCreateSplitOffSet(loadedOfficial, title, kind, notes, fw)
            },
        )
    }
}

@Composable
private fun CanonRetentionAuditCard(comparison: SetComparisonResult) {
    val intact = comparison.isCanonPreservationIntact
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (intact) StatusConnectedContainer.copy(alpha = 0.65f) else StatusOfflineAmberContainer.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            if (intact) StatusConnected.copy(alpha = 0.55f) else StatusOfflineAmber.copy(alpha = 0.7f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("canon_retention_audit_card"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (intact) Icons.Default.VerifiedUser else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (intact) StatusConnected else StatusOfflineAmber,
                )
                Text(
                    text = "Canon Preserved: ${comparison.canonPreservedCount} / ${comparison.parentOfficialTotalCount} (${"%.0f".format(comparison.canonRetentionPercent)}%)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = comparison.missingCanonWarning
                    ?: "100% of parent official cards from '${comparison.rightOfficialSetName}' are preserved intact alongside your custom variants and OCs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CardDiffRowCard(
    entry: CardDiffEntry,
    canCopyBaseline: Boolean,
    onCopyBaseline: (ComparableCardRecord) -> Unit,
) {
    val title = entry.leftCard?.name ?: entry.rightOfficialCard?.name ?: "(Unnamed)"
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (entry.category) {
                        ComparisonCategory.CANON_PRESERVED -> StatusConnectedContainer
                        ComparisonCategory.MISSING_CANON_OFFICIAL -> StatusOfflineAmberContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Text(
                        text = entry.category.displayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            if (entry.fieldDiffs.isNotEmpty()) {
                entry.fieldDiffs.forEach { diff ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = diff.fieldName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Yours: ${diff.leftValue.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Official: ${diff.rightValue.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (canCopyBaseline && entry.rightOfficialCard != null &&
                entry.category == ComparisonCategory.MISSING_CANON_OFFICIAL
            ) {
                OutlinedButton(
                    onClick = { onCopyBaseline(entry.rightOfficialCard) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Official Metadata Baseline (Art Blank)")
                }
            }
        }
    }
}

@Composable
private fun BalanceAnalyticsPanel(comparison: SetComparisonResult) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // KPI row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Your Avg Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.2f".format(comparison.leftAverageCost), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Official Avg Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.2f".format(comparison.rightAverageCost), style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // Cost Bracket Comparison Table
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Cost Curve & Avg Stat-to-Cost Bracket Comparison", style = MaterialTheme.typography.titleSmall)
                }
                comparison.costBrackets.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Cost ${b.bracketLabel}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
                        Text(
                            text = "Yours: ${b.leftCount} (avg ${b.leftAvgStatTotal?.let { "%.1f".format(it) } ?: "—"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Off: ${b.rightCount} (avg ${b.rightAvgStatTotal?.let { "%.1f".format(it) } ?: "—"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Outlier & Power-Creep Flags
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Balance & Power-Creep Outliers (${comparison.outlierFlags.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (comparison.outlierFlags.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusConnected, modifier = Modifier.size(18.dp))
                        Text("No power-creep or stat-curve outliers detected against official brackets.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    comparison.outlierFlags.forEach { flag: BalanceOutlierFlag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusOfflineAmberContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, StatusOfflineAmber.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${flag.cardName} • ${flag.severity} (${flag.cardOrigin.displayLabel})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(text = flag.reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialCardPreviewRow(
    card: ComparableCardRecord,
    canCopyBaseline: Boolean,
    onCopyBaseline: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (card.costOrLevel.isNotBlank()) {
                    Text(
                        text = card.costOrLevel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (card.typeLine.isNotBlank()) {
                Text(
                    text = buildString {
                        append(card.typeLine)
                        if (card.powerOrAtk.isNotBlank() || card.toughnessOrDef.isNotBlank()) {
                            append(" • ${card.powerOrAtk}/${card.toughnessOrDef}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.rulesText.isNotBlank()) {
                Text(
                    text = card.rulesText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canCopyBaseline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(
                        onClick = onCopyBaseline,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copy Baseline (Art Blank)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FanFictionSplitOffWizardDialog(
    officialSet: ComparableSet,
    frameworks: List<UniversalCardFramework>,
    onDismiss: () -> Unit,
    onConfirmCreate: (
        title: String,
        kind: SplitOffKind,
        divergenceNotes: String,
        targetFramework: UniversalCardFramework?,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("${officialSet.setName} — Fan-Fiction Split-Off") }
    var selectedKind by remember { mutableStateOf(SplitOffKind.FAN_FICTION_AU) }
    var divergenceNotes by remember { mutableStateOf("") }
    var selectedFramework by remember(frameworks, officialSet.gameId) {
        mutableStateOf(
            frameworks.firstOrNull {
                it.id.equals(officialSet.gameId, ignoreCase = true) ||
                    it.upstreamMseGameId.equals(officialSet.gameId, ignoreCase = true)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fork Fan-Fiction Split-Off Set") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Mandatory Canon Lore Preservation Invariant Notice
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusConnectedContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, StatusConnected.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StatusConnected, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Mandatory Canon Preservation: All ${officialSet.cards.size} original official card(s) will be kept intact as protected Canon Originals (metadata only, strictly blank art).",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Split-Off Set Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_off_title_input"),
                )

                Text("Split-Off Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SplitOffKind.entries.forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind },
                            label = { Text(kind.displayLabel, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                OutlinedTextField(
                    value = divergenceNotes,
                    onValueChange = { divergenceNotes = it },
                    label = { Text("Canon Divergence & Lore Notes") },
                    placeholder = { Text("Describe your AU branch, new faction, or crossover timeline…") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_off_notes_input"),
                )

                if (frameworks.isNotEmpty()) {
                    Text(
                        text = "Target Card Framework: ${selectedFramework?.name ?: "Match Parent Game (${officialSet.gameId})"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCreate(title, selectedKind, divergenceNotes, selectedFramework) },
                modifier = Modifier.testTag("split_off_confirm_create_button"),
            ) {
                Text("Create Split-Off Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
