package com.example.ui.telemetry

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.comparison.CardOrigin
import com.example.domain.telemetry.AggregatedCardUsageRow
import com.example.domain.telemetry.CardInteractionMetric
import com.example.domain.telemetry.TelemetryBadgeKind
import com.example.domain.telemetry.TelemetryInteractionType
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnectedContainer
import com.example.ui.theme.StatusOfflineAmber
import com.example.ui.theme.StatusOfflineAmberContainer

/**
 * Reusable Custom Card Usage & Interaction Telemetry Dashboard.
 *
 * Accessible from:
 * 1. Workspace `Stats` tab ("Usage & Interactions" sub-tab)
 * 2. Nexus Hub Sync screen ("Usage Reports" tab)
 * 3. Direct Android Intent / Deep-Link handoff from `com.aistudio.nexustcg.vxqrmk`
 */
@Composable
fun CardUsageReportsContent(
    uiState: CardUsageReportsUiState,
    githubPat: String = "",
    hasActiveSetOpen: Boolean = false,
    onPullFromGitHub: (String) -> Unit,
    onImportFromUri: (Uri) -> Unit,
    onSelectOriginFilter: (CardOrigin?) -> Unit,
    onSelectInteractionFilter: (TelemetryInteractionType?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onEditCardInStudio: (cardId: String, cardName: String) -> Unit,
    onBranchCardVariant: (cardId: String, cardName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeSection by remember { mutableIntStateOf(0) } // 0 = Per-Card Table, 1 = Interactions

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onImportFromUri(uri)
    }

    val dashboard = uiState.dashboard

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("card_usage_reports_list"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Ingestion Control Panel ----
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Card Usage & Interaction Telemetry",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${dashboard.reportCount} report(s) • ${dashboard.totalMatchesTracked} matches • Deduplicated by reportId",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onPullFromGitHub(githubPat) },
                            enabled = !uiState.isPullingFromGitHub,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("telemetry_pull_github_button"),
                        ) {
                            if (uiState.isPullingFromGitHub) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Pull GitHub", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { safImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("telemetry_import_saf_button"),
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON", maxLines = 1)
                        }
                    }

                    uiState.lastPullSummary?.let { summary ->
                        Text(
                            text = summary.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // ---- Origin Comparison Summary Strip (Canon Original vs Fan Variant vs Original OC) ----
        if (dashboard.reportCount > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dashboard.originSummaries.forEach { summary ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = when (summary.origin) {
                                            CardOrigin.CANON_ORIGINAL -> "Canon"
                                            CardOrigin.FAN_ALTERNATE_VARIANT -> "AU Variant"
                                            CardOrigin.ORIGINAL_OC_CARD -> "Original OC"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${"%.0f".format(summary.averageWinRatePercent)}% WR",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${summary.distinctCards} cards • ${summary.totalPlays} plays",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Cross-origin synergy highlight pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Canon ↔ OC Interactions: ${dashboard.canonVsOcInteractionCount}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Canon ↔ AU: ${dashboard.canonVsAlternateInteractionCount} • Errors: ${dashboard.totalScriptErrors}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dashboard.totalScriptErrors > 0) StatusOfflineAmber
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ---- Origin Filter Chips ----
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = uiState.selectedOriginFilter == null,
                    onClick = { onSelectOriginFilter(null) },
                    label = { Text("All Origins") },
                    modifier = Modifier.testTag("telemetry_filter_all"),
                )
                CardOrigin.entries.forEach { origin ->
                    FilterChip(
                        selected = uiState.selectedOriginFilter == origin,
                        onClick = {
                            onSelectOriginFilter(if (uiState.selectedOriginFilter == origin) null else origin)
                        },
                        label = { Text(origin.displayLabel, maxLines = 1) },
                        modifier = Modifier.testTag("telemetry_filter_${origin.wireValue.lowercase()}"),
                    )
                }
            }
        }

        // ---- Search + Section Toggle (Card Table vs Interactions) ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search cards, client IDs, or log lines…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telemetry_search_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = activeSection == 0,
                        onClick = { activeSection = 0 },
                        label = { Text("Card Telemetry (${dashboard.cardRows.size})") },
                        modifier = Modifier.testTag("telemetry_section_cards"),
                    )
                    FilterChip(
                        selected = activeSection == 1,
                        onClick = { activeSection = 1 },
                        label = { Text("Interactions (${dashboard.interactions.size})") },
                        modifier = Modifier.testTag("telemetry_section_interactions"),
                    )
                }
            }
        }

        if (dashboard.reportCount == 0) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "No Telemetry Reports Ingested Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Play matches with your custom cards or Split-Off Sets in NexusTCG (com.aistudio.nexustcg.vxqrmk), then share the JSON report directly into TCG Studio, tap 'Pull GitHub' (nexus-bridge/usage-reports/), or tap 'Import JSON'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (activeSection == 0) {
            items(dashboard.cardRows, key = { "${it.cardId}_${it.cardOrigin.wireValue}" }) { row ->
                TelemetryCardRowItem(
                    row = row,
                    hasActiveSetOpen = hasActiveSetOpen,
                    onEditInStudio = { onEditCardInStudio(row.cardId, row.cardName) },
                    onBranchVariant = { onBranchCardVariant(row.cardId, row.cardName) },
                )
            }
        } else {
            // Interaction type chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = uiState.selectedInteractionFilter == null,
                        onClick = { onSelectInteractionFilter(null) },
                        label = { Text("All Types") },
                    )
                    TelemetryInteractionType.entries.forEach { type ->
                        FilterChip(
                            selected = uiState.selectedInteractionFilter == type,
                            onClick = {
                                onSelectInteractionFilter(if (uiState.selectedInteractionFilter == type) null else type)
                            },
                            label = { Text(type.displayLabel, maxLines = 1) },
                        )
                    }
                }
            }

            items(dashboard.interactions) { interaction ->
                InteractionRowCard(interaction = interaction)
            }
        }
    }
}

@Composable
private fun TelemetryCardRowItem(
    row: AggregatedCardUsageRow,
    hasActiveSetOpen: Boolean,
    onEditInStudio: () -> Unit,
    onBranchVariant: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_card_row_${row.cardId}"),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.cardName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${row.cardOrigin.displayLabel} • ${row.targetClientId.ifBlank { "Custom Client" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${"%.0f".format(row.winRatePercent)}% WR",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Played: ${row.timesPlayed}/${row.timesDrawn} (${"%.0f".format(row.playRatePercent)}%)", style = MaterialTheme.typography.labelSmall)
                Text("Avg Turn: ${"%.1f".format(row.averageTurnPlayed)}", style = MaterialTheme.typography.labelSmall)
                Text("DMG: ${row.totalDamageDealt}", style = MaterialTheme.typography.labelSmall)
            }

            if (row.badges.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.badges.forEach { badge ->
                        val isAlert = badge.kind == TelemetryBadgeKind.SCRIPT_ERRORS ||
                            badge.kind == TelemetryBadgeKind.OVERPERFORMING
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAlert) StatusOfflineAmberContainer else StatusConnectedContainer,
                        ) {
                            Text(
                                text = badge.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAlert) StatusOfflineAmber else StatusConnected,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            if (hasActiveSetOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onEditInStudio,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit in 1:1 Studio", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    if (row.cardOrigin == CardOrigin.CANON_ORIGINAL) {
                        OutlinedButton(
                            onClick = onBranchVariant,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Branch AU Variant", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractionRowCard(interaction: CardInteractionMetric) {
    val typeEnum = TelemetryInteractionType.fromWire(interaction.interactionType)
    val isError = typeEnum == TelemetryInteractionType.RULE_VALIDATION_ERROR ||
        typeEnum == TelemetryInteractionType.SCRIPT_EXCEPTION ||
        typeEnum == TelemetryInteractionType.STACK_CONFLICT

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isError) StatusOfflineAmber.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
        ),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.BugReport else Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = if (isError) StatusOfflineAmber else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "${interaction.sourceCardName} ↔ ${interaction.targetCardName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "×${interaction.occurrenceCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = "${typeEnum.displayLabel} • ${CardOrigin.fromWire(interaction.sourceCardOrigin).displayLabel} ↔ ${CardOrigin.fromWire(interaction.targetCardOrigin).displayLabel}" +
                    if (interaction.phase.isNotBlank()) " • Phase: ${interaction.phase}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (interaction.sampleLogLine.isNotBlank()) {
                Text(
                    text = interaction.sampleLogLine,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                )
            }
        }
    }
}
