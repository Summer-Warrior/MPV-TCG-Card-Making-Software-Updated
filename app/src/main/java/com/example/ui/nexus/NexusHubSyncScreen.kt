package com.example.ui.nexus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.comparison.CardOrigin
import com.example.domain.nexus.MutualLinkStatus
import com.example.domain.nexus.NexusClientDefinition
import com.example.domain.nexus.NexusHubChangelogDiff
import com.example.domain.nexus.NexusRepoLinkManifest
import com.example.domain.telemetry.TelemetryInteractionType
import com.example.ui.telemetry.CardUsageReportsContent
import com.example.ui.telemetry.CardUsageReportsUiState
import com.example.ui.theme.StatusOfflineAmber
import com.example.ui.theme.StatusConnected

const val NEXUS_TAB_HUB_LINK = 0
const val NEXUS_TAB_ENGINE_SYNC = 1
const val NEXUS_TAB_USAGE_REPORTS = 5

private enum class NexusHubSection(val label: String) {
    SYNC_LINK("Hub Link"),
    ENGINE_SYNC("Engine Sync"),
    FRAME_REVIEW("Frame Review"),
    CLIENTS("Clients"),
    CODER_EXPORT("Card Coder"),
    USAGE_REPORTS("Usage Reports"),
}

@Composable
fun NexusHubSyncScreen(
    uiState: NexusHubUiState,
    engineSyncState: EngineSyncUiState,
    engineSyncViewModel: EngineSyncViewModel,
    autoTriggerSync: Boolean,
    requestedTabIndex: Int? = null,
    activeMseCardName: String?,
    activeMseCardType: String?,
    activeMseCardRules: String?,
    activeFrameWidthPx: Int,
    activeFrameHeightPx: Int,
    onAutoSyncConsumed: () -> Unit,
    onTabRequestConsumed: () -> Unit = {},
    onLaunchNexusTcgApp: () -> Unit = {},
    onSaveManifest: (NexusRepoLinkManifest) -> Unit,
    onVerifyAndPullHub: () -> Unit,
    onApplyPendingDiff: () -> Unit,
    onDismissPendingDiff: () -> Unit,
    onSelectClient: (String) -> Unit,
    onRedirectLockedToFork: (NexusClientDefinition) -> Unit,
    onUpdatePat: (String, Boolean) -> Unit,
    onCommitPackToHub: () -> Unit,
    onCommitManifestToThisRepo: () -> Unit,
    onBuildPackJson: () -> String?,
    onBuildManifestJson: () -> String,
    onBuildWorkflowYaml: () -> String,
    onWriteFileToUri: (Context, Uri, String, String) -> Unit,
    viewModel: NexusHubViewModel,
    onNotify: (String) -> Unit,
    usageReportsState: CardUsageReportsUiState = CardUsageReportsUiState(),
    hasActiveSetOpen: Boolean = false,
    onPullUsageReports: (String) -> Unit = {},
    onImportUsageReportUri: (Uri) -> Unit = {},
    onSelectUsageOriginFilter: (CardOrigin?) -> Unit = {},
    onSelectUsageInteractionFilter: (TelemetryInteractionType?) -> Unit = {},
    onUsageSearchQueryChanged: (String) -> Unit = {},
    onEditTelemetryCardInStudio: (String, String) -> Unit = { _, _ -> },
    onBranchTelemetryCardVariant: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(requestedTabIndex) {
        if (requestedTabIndex != null && requestedTabIndex in NexusHubSection.entries.indices) {
            selectedSectionIndex = requestedTabIndex
            onTabRequestConsumed()
        }
    }

    // Supports ?hubSync=1 deep-link / intent handshake trigger
    LaunchedEffect(autoTriggerSync) {
        if (autoTriggerSync) {
            onVerifyAndPullHub()
            engineSyncViewModel.syncLiveGameEngine(uiState.fineGrainedPat.ifBlank { null }, isManualRefresh = true)
            onAutoSyncConsumed()
        }
    }

    val savePackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val json = onBuildPackJson()
            if (json != null) {
                onWriteFileToUri(context, uri, json, "Card Pack JSON")
            }
        }
    }

    val saveWorkflowLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/yaml"),
    ) { uri ->
        if (uri != null) {
            onWriteFileToUri(context, uri, onBuildWorkflowYaml(), "mpv-nexus-sync.yml")
        }
    }

    val saveLuaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val lua = uiState.generatedLuaPreview
        if (uri != null && !lua.isNullOrBlank()) {
            onWriteFileToUri(context, uri, lua, "c21-digits.lua")
        }
    }

    val saveHealthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            onWriteFileToUri(
                context,
                uri,
                engineSyncViewModel.buildDiagnosticReportJson(),
                "engine-health-status.json",
            )
        }
    }

    fun copyToClipboard(label: String, content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
        onNotify("Copied $label to clipboard.")
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Horizontally Scrollable 5-Section Selector Strip (Zero Clipping on 360dp Phones) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NexusHubSection.entries.forEachIndexed { idx, section ->
                FilterChip(
                    selected = selectedSectionIndex == idx,
                    onClick = { selectedSectionIndex = idx },
                    label = {
                        Text(
                            text = section.label,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    modifier = Modifier.testTag("nexus_tab_${section.name.lowercase()}"),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when (NexusHubSection.entries[selectedSectionIndex]) {
            NexusHubSection.SYNC_LINK -> {
                SyncAndLinkTab(
                    uiState = uiState,
                    onSaveManifest = onSaveManifest,
                    onVerifyAndPullHub = onVerifyAndPullHub,
                    onApplyPendingDiff = onApplyPendingDiff,
                    onDismissPendingDiff = onDismissPendingDiff,
                    onUpdatePat = onUpdatePat,
                    onCommitPackToHub = onCommitPackToHub,
                    onCommitManifestToThisRepo = onCommitManifestToThisRepo,
                    onCopyManifestJson = { copyToClipboard("nexus-repo-link.json", onBuildManifestJson()) },
                    onCopyPackJson = {
                        val json = onBuildPackJson()
                        if (json != null) copyToClipboard("Card Pack JSON", json)
                    },
                    onDownloadPackJson = { savePackLauncher.launch("nexus-card-pack.json") },
                    onDownloadWorkflowYml = { saveWorkflowLauncher.launch("mpv-nexus-sync.yml") },
                    onCopyWorkflowYml = { copyToClipboard("mpv-nexus-sync.yml", onBuildWorkflowYaml()) },
                )
            }

            NexusHubSection.ENGINE_SYNC -> {
                EngineSyncSection(
                    uiState = engineSyncState,
                    fineGrainedPat = uiState.fineGrainedPat,
                    onSyncLiveEngine = {
                        engineSyncViewModel.syncLiveGameEngine(uiState.fineGrainedPat.ifBlank { null }, isManualRefresh = true)
                    },
                    onRecordCardMakerConsent = engineSyncViewModel::recordCardMakerConsent,
                    onFinalizeStagedUpdate = engineSyncViewModel::finalizeStagedEngineSnapshot,
                    onTogglePauseSync = engineSyncViewModel::pauseOrResumeSync,
                    onSaveAdminGateAndRelays = engineSyncViewModel::updateAdminGateAndRelays,
                    onDispatchAdminNotifications = {
                        engineSyncViewModel.dispatchManualAdminNotifications(uiState.fineGrainedPat.ifBlank { null })
                    },
                    onExportHealthJson = { saveHealthLauncher.launch("engine-health-status.json") },
                    onCopyContractJson = {
                        copyToClipboard("handshake-contract.json", engineSyncViewModel.buildHandshakeContractJson())
                    },
                    onLaunchMailto = {
                        val intent = Intent(Intent.ACTION_SENDTO, engineSyncViewModel.buildMailtoEscalationUri())
                        runCatching { context.startActivity(intent) }
                            .onFailure { onNotify("No email client found; use Save Health JSON or Copy.") }
                    },
                    onLaunchNexusTcgApp = onLaunchNexusTcgApp,
                    onRefreshCompanionApp = engineSyncViewModel::refreshCompanionAppStatus,
                )
            }

            NexusHubSection.FRAME_REVIEW -> {
                CardFrameReviewerSection(
                    uiState = engineSyncState,
                    activeMseCardName = activeMseCardName,
                    activeMseCardType = activeMseCardType,
                    activeMseCardRules = activeMseCardRules,
                    activeFrameWidthPx = activeFrameWidthPx,
                    activeFrameHeightPx = activeFrameHeightPx,
                    viewModel = engineSyncViewModel,
                    onCopyText = ::copyToClipboard,
                )
            }

            NexusHubSection.CLIENTS -> {
                ClientsAndFirewallTab(
                    clients = uiState.appliedIndex.clients,
                    selectedClientId = uiState.selectedClientId,
                    indexRevision = uiState.appliedIndex.indexRevision,
                    onSelectClient = {
                        onSelectClient(it)
                        selectedSectionIndex = NexusHubSection.CODER_EXPORT.ordinal
                    },
                    onRedirectLockedToFork = { locked ->
                        onRedirectLockedToFork(locked)
                        selectedSectionIndex = NexusHubSection.CODER_EXPORT.ordinal
                    },
                )
            }

            NexusHubSection.CODER_EXPORT -> {
                NexusCardCoderSection(
                    uiState = uiState,
                    activeMseCardName = activeMseCardName,
                    activeMseCardType = activeMseCardType,
                    activeMseCardRules = activeMseCardRules,
                    viewModel = viewModel,
                    onCopyPackJson = {
                        val json = onBuildPackJson()
                        if (json != null) copyToClipboard("Card Pack JSON", json)
                    },
                    onDownloadPackJson = { savePackLauncher.launch("nexus-card-pack.json") },
                    onDownloadLua = {
                        val reg = uiState.register21Digit.trim().padStart(21, '0')
                        saveLuaLauncher.launch("c$reg.lua")
                    },
                    onCopyLua = {
                        uiState.generatedLuaPreview?.let { copyToClipboard("Lua Script", it) }
                    },
                    onCommitPackToHub = onCommitPackToHub,
                )
            }

            NexusHubSection.USAGE_REPORTS -> {
                CardUsageReportsContent(
                    uiState = usageReportsState,
                    githubPat = uiState.fineGrainedPat,
                    hasActiveSetOpen = hasActiveSetOpen,
                    onPullFromGitHub = onPullUsageReports,
                    onImportFromUri = onImportUsageReportUri,
                    onSelectOriginFilter = onSelectUsageOriginFilter,
                    onSelectInteractionFilter = onSelectUsageInteractionFilter,
                    onSearchQueryChanged = onUsageSearchQueryChanged,
                    onEditCardInStudio = onEditTelemetryCardInStudio,
                    onBranchCardVariant = onBranchTelemetryCardVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncAndLinkTab(
    uiState: NexusHubUiState,
    onSaveManifest: (NexusRepoLinkManifest) -> Unit,
    onVerifyAndPullHub: () -> Unit,
    onApplyPendingDiff: () -> Unit,
    onDismissPendingDiff: () -> Unit,
    onUpdatePat: (String, Boolean) -> Unit,
    onCommitPackToHub: () -> Unit,
    onCommitManifestToThisRepo: () -> Unit,
    onCopyManifestJson: () -> Unit,
    onCopyPackJson: () -> Unit,
    onDownloadPackJson: () -> Unit,
    onDownloadWorkflowYml: () -> Unit,
    onCopyWorkflowYml: () -> Unit,
) {
    var thisRepo by remember(uiState.manifest.thisRepo) { mutableStateOf(uiState.manifest.thisRepo) }
    var thisBranch by remember(uiState.manifest.thisBranch) { mutableStateOf(uiState.manifest.thisBranch) }
    var hubRepo by remember(uiState.manifest.linkedHubRepo) { mutableStateOf(uiState.manifest.linkedHubRepo) }
    var hubBranch by remember(uiState.manifest.linkedHubBranch) { mutableStateOf(uiState.manifest.linkedHubBranch) }
    var hubIndexPath by remember(uiState.manifest.hubIndexPath) { mutableStateOf(uiState.manifest.hubIndexPath) }
    var packsDir by remember(uiState.manifest.exportedPacksDir) { mutableStateOf(uiState.manifest.exportedPacksDir) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Mutual Link Status Banner ----
        item {
            val statusColor = when (uiState.linkStatus) {
                MutualLinkStatus.LINKED_AND_CURRENT -> StatusConnected
                MutualLinkStatus.LINKED_OUT_OF_SYNC, MutualLinkStatus.ONE_WAY_ONLY -> StatusOfflineAmber
                MutualLinkStatus.UNLINKED -> MaterialTheme.colorScheme.error
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (uiState.isMutualLinkVerified) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = "Mutual Link Status",
                            tint = statusColor,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mutual Link: ${uiState.linkStatus.wireValue}",
                                style = MaterialTheme.typography.titleSmall,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Index Rev: ${uiState.appliedIndex.indexRevision} • ${uiState.appliedIndex.clients.count { !it.upstreamSourceLock }} Non-Locked Clients",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    uiState.lastHandshake?.let { handshake ->
                        Text(
                            text = handshake.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = onVerifyAndPullHub,
                        enabled = !uiState.isCheckingHub,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nexus_verify_and_pull_button"),
                    ) {
                        if (uiState.isCheckingHub) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Verifying Mutual Link & Pulling Index…")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Verify Link & Check Hub Updates")
                        }
                    }
                }
            }
        }

        // ---- Pending Hub Changelog Diff Review ----
        uiState.pendingDiff?.let { diff ->
            item {
                ChangelogDiffReviewCard(
                    diff = diff,
                    onApply = onApplyPendingDiff,
                    onDismiss = onDismissPendingDiff,
                )
            }
        }

        // ---- Canonical nexus-repo-link.json Editor ----
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
                        text = "Mutual Repo Link Manifest (nexus-repo-link.json)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = thisRepo,
                        onValueChange = { thisRepo = it },
                        label = { Text("thisRepo (owner/repo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = thisBranch,
                            onValueChange = { thisBranch = it },
                            label = { Text("thisBranch") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = hubBranch,
                            onValueChange = { hubBranch = it },
                            label = { Text("hubBranch") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = hubRepo,
                        onValueChange = { hubRepo = it },
                        label = { Text("linkedHubRepo (Nexus Master Hub)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = hubIndexPath,
                        onValueChange = { hubIndexPath = it },
                        label = { Text("hubIndexPath") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = packsDir,
                        onValueChange = { packsDir = it },
                        label = { Text("exportedPacksDir") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                onSaveManifest(
                                    uiState.manifest.copy(
                                        thisRepo = thisRepo.trim(),
                                        thisBranch = thisBranch.trim().ifBlank { "main" },
                                        linkedHubRepo = hubRepo.trim(),
                                        linkedHubBranch = hubBranch.trim().ifBlank { "main" },
                                        hubIndexPath = hubIndexPath.trim(),
                                        exportedPacksDir = packsDir.trim(),
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nexus_save_manifest_button"),
                        ) {
                            Text("Save Link Manifest")
                        }
                        OutlinedButton(
                            onClick = onCopyManifestJson,
                            modifier = Modifier.testTag("nexus_copy_manifest_button"),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                }
            }
        }

        // ---- Option A: Direct GitHub Commit (Only When Mutual Link Verified) ----
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
                        text = "Option A — Direct GitHub Commit (Mutual Link Required)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Optional Fine-Grained GitHub PAT scoped ONLY to Contents: Read & Write. Stored strictly in session memory unless you opt in below. Uses GET-SHA + Base64 PUT with automatic 409/422 conflict recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.fineGrainedPat,
                        onValueChange = { onUpdatePat(it, uiState.rememberPatOptIn) },
                        label = { Text("Fine-Grained GitHub PAT (Contents: R/W)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nexus_pat_input"),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = uiState.rememberPatOptIn,
                            onCheckedChange = { onUpdatePat(uiState.fineGrainedPat, it) },
                            modifier = Modifier.testTag("nexus_pat_remember_checkbox"),
                        )
                        Text(
                            text = "Remember PAT locally on this device (off = session only)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onCommitPackToHub,
                            enabled = uiState.isMutualLinkVerified && !uiState.isCommitting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nexus_direct_commit_pack_button"),
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.isCommitting) "Committing…" else "Commit Pack")
                        }
                        OutlinedButton(
                            onClick = onCommitManifestToThisRepo,
                            enabled = !uiState.isCommitting && uiState.fineGrainedPat.isNotBlank(),
                            modifier = Modifier.testTag("nexus_commit_manifest_button"),
                        ) {
                            Text("Push Link")
                        }
                    }

                    if (!uiState.isMutualLinkVerified) {
                        Text(
                            text = "Direct pack commit is locked until Mutual Link verification reports LINKED_AND_CURRENT or LINKED_OUT_OF_SYNC.",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusOfflineAmber,
                        )
                    }
                    uiState.lastCommitSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // ---- Option B: Zero-Token Mobile & GitHub Actions Sync ----
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Option B — Zero-Token Mobile & GitHub Actions Sync",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "No PAT needed. Export Card Pack JSON or the automated mpv-nexus-sync.yml workflow directly on Android.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onCopyPackJson,
                            modifier = Modifier.testTag("nexus_copy_pack_json_button"),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy JSON")
                        }
                        OutlinedButton(
                            onClick = onDownloadPackJson,
                            modifier = Modifier.testTag("nexus_download_pack_json_button"),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download Card Pack JSON")
                        }
                        OutlinedButton(
                            onClick = onDownloadWorkflowYml,
                            modifier = Modifier.testTag("nexus_download_workflow_button"),
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download mpv-nexus-sync.yml")
                        }
                        TextButton(
                            onClick = onCopyWorkflowYml,
                            modifier = Modifier.testTag("nexus_copy_workflow_button"),
                        ) {
                            Text("Copy Workflow YAML")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogDiffReviewCard(
    diff: NexusHubChangelogDiff,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Hub Update Diff (${diff.fromRevision} → ${diff.toRevision})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (diff.addedClients.isNotEmpty()) {
                Text(
                    text = "+ Added Non-Locked Clients (${diff.addedClients.size}): ${diff.addedClients.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (diff.updatedClients.isNotEmpty()) {
                Text(
                    text = "~ Updated Non-Locked Clients (${diff.updatedClients.size}): ${diff.updatedClients.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (diff.addedNotebookFormats.isNotEmpty() || diff.updatedNotebookFormats.isNotEmpty()) {
                Text(
                    text = "Notebook Formats: +${diff.addedNotebookFormats.size} added, ~${diff.updatedNotebookFormats.size} updated",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (diff.addedCombos.isNotEmpty() || diff.updatedCombos.isNotEmpty()) {
                Text(
                    text = "AI/User Combos: +${diff.addedCombos.size} added, ~${diff.updatedCombos.size} updated",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApply,
                    modifier = Modifier.testTag("nexus_apply_diff_button"),
                ) {
                    Text("Apply Updates")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("nexus_dismiss_diff_button"),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun ClientsAndFirewallTab(
    clients: List<NexusClientDefinition>,
    selectedClientId: String,
    indexRevision: String,
    onSelectClient: (String) -> Unit,
    onRedirectLockedToFork: (NexusClientDefinition) -> Unit,
) {
    val nonLocked = remember(clients) { clients.filter { !it.upstreamSourceLock } }
    val locked = remember(clients) { clients.filter { it.upstreamSourceLock } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Non-Locked Coding Targets (${nonLocked.size}) • Rev $indexRevision",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        items(nonLocked, key = { it.id }) { client ->
            val isSelected = client.id == selectedClientId
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Writable Non-Locked Client",
                            tint = StatusConnected,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "ID Format: ${client.idFormat} • Zones: ${client.zones.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (client.resourcePools.isNotEmpty()) {
                        Text(
                            text = "Resources: ${client.resourcePools.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { onSelectClient(client.id) },
                            modifier = Modifier.testTag("nexus_select_client_${client.id}"),
                        ) {
                            Text(if (isSelected) "Active Target (Code)" else "Target for Coding")
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Locked-Client Firewall",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Locked Upstream Clients — Read-Only Firewall (${locked.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        items(locked, key = { it.id }) { lockedClient ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked Upstream Reference",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = lockedClient.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = "READ-ONLY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    lockedClient.upstreamRepo?.let { repo ->
                        Text(
                            text = "Upstream Repo: $repo (upstreamSourceLock = true — writing & pack export strictly blocked)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { onRedirectLockedToFork(lockedClient) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nexus_redirect_fork_${lockedClient.id}"),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Target Non-Locked 21-Digit Fork Instead")
                    }
                }
            }
        }
    }
}
