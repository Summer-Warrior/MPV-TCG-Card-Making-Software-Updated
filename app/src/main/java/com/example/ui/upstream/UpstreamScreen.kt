package com.example.ui.upstream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.upstream.GitHubCommitCheckResult
import com.example.data.upstream.OpenResourceConnector
import com.example.data.upstream.UpdateCheckState
import com.example.data.upstream.UpstreamManifest
import com.example.data.upstream.UpstreamRepoSplit
import com.example.ui.theme.JetBrainsMonoFamily

/**
 * Upstream Splits & Manifest Manager Screen (Section 3 & Phase 1.6).
 * Reads/edits `upstream_manifest.json`, checks GitHub public ETag/Atom updates,
 * supports SHA pinning + 1-tap rollback, Open-Resource Game Connectors, and bridge delta sync.
 */
@Composable
fun UpstreamScreen(
    manifest: UpstreamManifest,
    commitChecks: Map<String, GitHubCommitCheckResult>,
    isCheckingUpdates: Boolean,
    isSyncingBridge: Boolean,
    exportManifestJson: () -> String,
    onCheckGitHubUpdates: () -> Unit,
    onTriggerBridgeDeltaSync: () -> Unit,
    onToggleSplitEnabled: (splitId: String, enabled: Boolean) -> Unit,
    onUpdateSplitBranch: (splitId: String, branch: String) -> Unit,
    onPinSplitSha: (splitId: String, sha: String) -> Unit,
    onRollbackSplitSha: (splitId: String) -> Unit,
    onUpdateWebhookRelayUrl: (String) -> Unit,
    onAddConnector: (gameName: String, owner: String, repo: String, branch: String, resourceType: String) -> Unit,
    onRemoveConnector: (String) -> Unit,
    onSaveRawManifestJson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddConnectorDialog by remember { mutableStateOf(false) }
    var showRawJsonDialog by remember { mutableStateOf(false) }
    var branchEditTarget by remember { mutableStateOf<UpstreamRepoSplit?>(null) }
    var webhookInput by remember(manifest.webhookRelayUrl) { mutableStateOf(manifest.webhookRelayUrl) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Read-only upstream splits & local template overrides",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { showRawJsonDialog = true },
                        modifier = Modifier.testTag("upstream_edit_json_button"),
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Edit upstream_manifest.json")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onCheckGitHubUpdates,
                        enabled = !isCheckingUpdates,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upstream_check_github_button"),
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Check GitHub", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onTriggerBridgeDeltaSync,
                        enabled = !isSyncingBridge,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upstream_bridge_sync_button"),
                    ) {
                        if (isSyncingBridge) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Bridge", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // ---- Engine Splits ----
        item {
            SectionHeader(
                title = "Engine Splits (MagicSetEditor2)",
                subtitle = "Modern 2.5+/2.6+ primary engine + legacy twanvl fallback",
            )
        }
        items(manifest.engineSplits, key = { it.id }) { split ->
            UpstreamSplitCard(
                split = split,
                checkResult = commitChecks[split.id],
                onToggleEnabled = { onToggleSplitEnabled(split.id, it) },
                onSelectBranch = { onUpdateSplitBranch(split.id, it) },
                onOpenCustomBranch = { branchEditTarget = split },
                onPinSha = { sha -> onPinSplitSha(split.id, sha) },
                onRollbackSha = { onRollbackSplitSha(split.id) },
            )
        }

        // ---- Template & Font Pack Splits ----
        item {
            SectionHeader(
                title = "Template & Font Splits (MagicSetEditorPacks)",
                subtitle = "Delta-synced splits with per-channel opt-in and branch selection",
            )
        }
        items(manifest.packSplits, key = { it.id }) { split ->
            UpstreamSplitCard(
                split = split,
                checkResult = commitChecks[split.id],
                onToggleEnabled = { onToggleSplitEnabled(split.id, it) },
                onSelectBranch = { onUpdateSplitBranch(split.id, it) },
                onOpenCustomBranch = { branchEditTarget = split },
                onPinSha = { sha -> onPinSplitSha(split.id, sha) },
                onRollbackSha = { onRollbackSplitSha(split.id) },
            )
        }

        // ---- Open-Resource Game Connectors ----
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Open-Resource Game Connectors",
                    subtitle = "Public GitHub repos for frames, symbols, datasets, scripts & rulebooks",
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = { showAddConnectorDialog = true },
                    label = { Text(stringResource(R.string.add_connector)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("upstream_add_connector_button"),
                )
            }
        }

        if (manifest.openResourceConnectors.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No custom Open-Resource Game Connectors registered yet. Tap 'Add Open Connector' to track additional public GitHub repos per card game.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(manifest.openResourceConnectors, key = { it.id }) { conn ->
                ConnectorCard(
                    connector = conn,
                    checkResult = commitChecks[conn.id],
                    onRemove = { onRemoveConnector(conn.id) },
                )
            }
        }

        // ---- Optional GitHub Actions Webhook Relay ----
        item {
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
                        text = "Optional GitHub Actions Webhook Relay",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Optionally connect a user-hosted webhook relay URL alongside public ETag/Atom polling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = webhookInput,
                            onValueChange = { webhookInput = it },
                            placeholder = { Text("https://your-relay.example/github-hook") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upstream_webhook_input"),
                        )
                        Button(
                            onClick = { onUpdateWebhookRelayUrl(webhookInput) },
                            modifier = Modifier.testTag("upstream_webhook_save_button"),
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    // ---- Add Open-Resource Connector Dialog ----
    if (showAddConnectorDialog) {
        AddConnectorDialog(
            onDismiss = { showAddConnectorDialog = false },
            onAdd = { game, owner, repo, branch, resType ->
                onAddConnector(game, owner, repo, branch, resType)
                showAddConnectorDialog = false
            },
        )
    }

    // ---- Custom Branch Dialog ----
    branchEditTarget?.let { target ->
        var branchText by remember(target.id) { mutableStateOf(target.branch) }
        AlertDialog(
            onDismissRequest = { branchEditTarget = null },
            title = { Text("Set Branch for ${target.name}") },
            text = {
                OutlinedTextField(
                    value = branchText,
                    onValueChange = { branchText = it },
                    label = { Text("GitHub Branch Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSplitBranch(target.id, branchText)
                        branchEditTarget = null
                    }
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { branchEditTarget = null }) { Text("Cancel") }
            },
        )
    }

    // ---- Raw upstream_manifest.json Editor Dialog ----
    if (showRawJsonDialog) {
        var jsonDraft by remember { mutableStateOf(exportManifestJson()) }
        AlertDialog(
            onDismissRequest = { showRawJsonDialog = false },
            title = { Text("upstream_manifest.json") },
            text = {
                OutlinedTextField(
                    value = jsonDraft,
                    onValueChange = { jsonDraft = it },
                    minLines = 10,
                    maxLines = 16,
                    textStyle = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveRawManifestJson(jsonDraft)
                        showRawJsonDialog = false
                    }
                ) { Text("Save JSON") }
            },
            dismissButton = {
                TextButton(onClick = { showRawJsonDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpstreamSplitCard(
    split: UpstreamRepoSplit,
    checkResult: GitHubCommitCheckResult?,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectBranch: (String) -> Unit,
    onOpenCustomBranch: () -> Unit,
    onPinSha: (String) -> Unit,
    onRollbackSha: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("split_card_${split.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = split.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${split.owner}/${split.repo} • ${split.channel} • ${split.engineTarget}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(
                    checked = split.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("split_toggle_${split.id}"),
                )
            }

            if (split.description.isNotBlank()) {
                Text(
                    text = split.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Selectable branches row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Branch:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                split.availableBranches.forEach { branchName ->
                    FilterChip(
                        selected = split.branch == branchName,
                        onClick = { onSelectBranch(branchName) },
                        label = { Text(branchName, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                AssistChip(
                    onClick = onOpenCustomBranch,
                    label = { Text("Custom…", style = MaterialTheme.typography.labelSmall) },
                )
            }

            // Font folder / Pinned SHA metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pinned: ${split.pinnedSha?.take(8) ?: "HEAD (unpinned)"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                split.fontFolder?.let { folder ->
                    Text(
                        text = "• Fonts: $folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            // Live GitHub ETag/Atom check result
            checkResult?.let { result ->
                val statusColor = when (result.status) {
                    UpdateCheckState.UP_TO_DATE,
                    UpdateCheckState.NOT_MODIFIED_304 -> MaterialTheme.colorScheme.tertiary

                    UpdateCheckState.UPDATE_AVAILABLE -> MaterialTheme.colorScheme.primary
                    UpdateCheckState.ERROR -> MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${result.sourceMethod} • ${result.detail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                        result.latestSha?.let { sha ->
                            Text(
                                text = "HEAD ${sha.take(8)}: ${result.commitMessage.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Pin SHA & 1-Tap Rollback actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val candidateSha = checkResult?.latestSha
                if (!candidateSha.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onPinSha(candidateSha) },
                        modifier = Modifier.testTag("split_pin_${split.id}"),
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${stringResource(R.string.pin_sha)} ${candidateSha.take(7)}")
                    }
                }
                if (!split.previousSha.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onRollbackSha,
                        modifier = Modifier.testTag("split_rollback_${split.id}"),
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${stringResource(R.string.rollback_sha)} ${split.previousSha.take(7)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectorCard(
    connector: OpenResourceConnector,
    checkResult: GitHubCommitCheckResult?,
    onRemove: () -> Unit,
) {
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
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${connector.gameName} (${connector.resourceType})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${connector.owner}/${connector.repo}@${connector.branch}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                checkResult?.let {
                    Text(
                        text = "${it.sourceMethod}: ${it.latestSha?.take(8) ?: it.detail}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remove connector",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddConnectorDialog(
    onDismiss: () -> Unit,
    onAdd: (gameName: String, owner: String, repo: String, branch: String, resourceType: String) -> Unit,
) {
    var gameName by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var resourceType by remember { mutableStateOf("frames") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Open-Resource Game Connector") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gameName,
                    onValueChange = { gameName = it },
                    label = { Text("Card Game Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("GitHub Owner / Org") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Repository Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("frames", "symbol_fonts", "datasets", "scripts", "rulebooks").forEach { type ->
                        FilterChip(
                            selected = resourceType == type,
                            onClick = { resourceType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = gameName.isNotBlank() && owner.isNotBlank() && repo.isNotBlank(),
                onClick = { onAdd(gameName, owner, repo, branch, resourceType) },
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
