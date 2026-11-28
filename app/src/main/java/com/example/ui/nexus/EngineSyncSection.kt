package com.example.ui.nexus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.nexus.AlignmentWarningEvaluation
import com.example.domain.nexus.ConsentStatus
import com.example.domain.nexus.EngineCommitDiff
import com.example.domain.nexus.EngineHealthSeverity
import com.example.domain.nexus.EngineHealthStatusPayload
import com.example.domain.nexus.HandshakeContract
import com.example.domain.nexus.InstalledCompanionAppInfo
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber

/**
 * Live Engine Data Reader (`Summer-Warrior/Tcg-game-engine-test`), Two-Way Handshake
 * Dual-Engine Update Consent UI, Private Play Store Companion App Bridge,
 * and Master Hub Admin Gate / Tri-Channel Notification Controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineSyncSection(
    uiState: EngineSyncUiState,
    isDeviceOnline: Boolean = false,
    fineGrainedPat: String,
    onVerifyLocalEngineFiles: () -> Unit = {},
    onImportLocalEngineFile: () -> Unit = {},
    onSyncLiveEngine: () -> Unit,
    onRecordCardMakerConsent: (ConsentStatus, String) -> Unit,
    onFinalizeStagedUpdate: () -> Unit,
    onTogglePauseSync: (Boolean) -> Unit,
    onSaveAdminGateAndRelays: (
        adminRoleIdsCsv: String,
        adminUserIdsCsv: String,
        adminEmailsCsv: String,
        currentUserRoleIdsCsv: String,
        currentUserId: String,
        currentUserEmail: String,
        discordWebhookUrl: String,
        emailRelayUrl: String,
    ) -> Unit,
    onDispatchAdminNotifications: () -> Unit,
    onExportHealthJson: () -> Unit,
    onCopyContractJson: () -> Unit,
    onLaunchMailto: () -> Unit,
    onLaunchNexusTcgApp: () -> Unit,
    onRefreshCompanionApp: () -> Unit,
) {
    val gate = uiState.appManifest.masterHubAdminGate
    val local = uiState.adminLocalState

    var adminRolesCsv by remember(gate.adminRoleIds) { mutableStateOf(gate.adminRoleIds.joinToString(", ")) }
    var adminUsersCsv by remember(gate.adminUserIds) { mutableStateOf(gate.adminUserIds.joinToString(", ")) }
    var adminEmailsCsv by remember(gate.adminEmails) { mutableStateOf(gate.adminEmails.joinToString(", ")) }
    var myRolesCsv by remember(local.currentUserRoleIds) { mutableStateOf(local.currentUserRoleIds.joinToString(", ")) }
    var myUserId by remember(local.currentUserId) { mutableStateOf(local.currentUserId) }
    var myEmail by remember(local.currentUserEmail) { mutableStateOf(local.currentUserEmail) }
    var discordWebhook by remember(local.discordAdminWebhookUrl) { mutableStateOf(local.discordAdminWebhookUrl) }
    var emailRelay by remember(local.emailRelayWebhookUrl) { mutableStateOf(local.emailRelayWebhookUrl) }

    val severityColor = when (uiState.healthStatus.severity) {
        EngineHealthSeverity.BREAKING_INCOMPATIBLE.wireValue -> MaterialTheme.colorScheme.error
        EngineHealthSeverity.DEGRADED.wireValue -> StatusOfflineAmber
        else -> StatusConnected
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Live Engine Health & Two-Way Handshake Status ----
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, severityColor.copy(alpha = 0.75f)),
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
                            imageVector = when (uiState.healthStatus.severity) {
                                EngineHealthSeverity.BREAKING_INCOMPATIBLE.wireValue -> Icons.Default.Error
                                EngineHealthSeverity.DEGRADED.wireValue -> Icons.Default.Warning
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = "Engine Health",
                            tint = severityColor,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Engine Audit: ${uiState.healthStatus.severity}",
                                style = MaterialTheme.typography.titleSmall,
                                color = severityColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${uiState.contract.gameEngineRepo} • Master Hub Ack: ${uiState.healthStatus.masterHubAckStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = uiState.lastSyncMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (uiState.healthStatus.brokenCapabilities.isNotEmpty()) {
                        Text(
                            text = "Broken Capabilities: ${uiState.healthStatus.brokenCapabilities.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // Primary 100% offline local-file handshake verification & SAF import
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onVerifyLocalEngineFiles,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("engine_verify_local_files_button"),
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Verify Local Handshake Files")
                        }
                        OutlinedButton(
                            onClick = onImportLocalEngineFile,
                            modifier = Modifier.testTag("engine_import_local_file_button"),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import JSON")
                        }
                    }

                    // Explicit opt-in online update check (strictly gated on device connectivity)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onSyncLiveEngine,
                            enabled = isDeviceOnline && !uiState.isSyncingEngine && !local.syncPaused,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("engine_sync_live_button"),
                        ) {
                            if (uiState.isSyncingEngine) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Checking Online Updates…")
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isDeviceOnline) {
                                        "Optional Online Update Check"
                                    } else {
                                        "Offline — Online Update Check Paused"
                                    }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { onTogglePauseSync(!local.syncPaused) },
                            modifier = Modifier.testTag("engine_pause_sync_button"),
                        ) {
                            Icon(
                                imageVector = if (local.syncPaused) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (local.syncPaused) "Resume" else "Pause")
                        }
                    }
                }
            }
        }

        // ---- Private Play Store Companion App Detection & Deep-Link Handshake ----
        item {
            val companion = uiState.companionEngineApp
            val companionTint = if (companion.isInstalled) StatusConnected else StatusOfflineAmber
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, companionTint.copy(alpha = 0.65f)),
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
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Companion Android App",
                            tint = companionTint,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Companion App: NexusTCG (${companion.packageName})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = companion.summaryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = companionTint,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Text(
                        text = "Distribution Policy: ${uiState.appManifest.distributionPolicy} • Deep Link: ${uiState.appManifest.gameEngineDeepLinkScheme}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onLaunchNexusTcgApp,
                            enabled = companion.isInstalled || companion.canHandleDeepLink,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("engine_open_companion_app_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Open NexusTCG App (Handshake)")
                        }
                        OutlinedButton(
                            onClick = onRefreshCompanionApp,
                            modifier = Modifier.testTag("engine_refresh_companion_button"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Check")
                        }
                    }
                }
            }
        }

        // ---- Dual-Engine Update Consent Contract Card ----
        item {
            val c = uiState.contract
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
                        text = "Dual-Engine Update Consent (handshake-contract.json v${c.contractVersion})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Master Hub: ${c.masterHubRepo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Card Maker Consent: ${c.cardMakerConsent.status} (${c.cardMakerConsent.commitSha.take(10).ifBlank { "none" }})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Game Engine Consent: ${c.gameEngineConsent.status} (${c.gameEngineConsent.commitSha.take(10).ifBlank { "none" }})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Rule: Incoming engine schemas stay in STAGED preview until BOTH engines record CONSENTED for the exact target commit SHA.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    uiState.stagedDiff?.let { diff ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "STAGED Diff: ${diff.fromCommitSha.take(8)} → ${diff.toCommitSha.take(8)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Variants +${diff.addedVariantIds.size}/-${diff.removedVariantIds.size} • BigInt21 +${diff.addedBigIntFunctions.size}/-${diff.removedBigIntFunctions.size} • Phases +${diff.addedTurnPhases.size}/-${diff.removedTurnPhases.size} • Bounds changed: ${diff.boundsChanged}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = { onRecordCardMakerConsent(ConsentStatus.CONSENTED, myUserId.ifBlank { "card-maker-admin" }) },
                            modifier = Modifier.testTag("engine_consent_approve_button"),
                        ) {
                            Text("Consent (Card Maker)")
                        }
                        OutlinedButton(
                            onClick = { onRecordCardMakerConsent(ConsentStatus.REJECTED, myUserId.ifBlank { "card-maker-admin" }) },
                        ) {
                            Text("Reject")
                        }
                        Button(
                            onClick = onFinalizeStagedUpdate,
                            enabled = uiState.canFinalizeStagedUpdate,
                            modifier = Modifier.testTag("engine_finalize_staged_button"),
                        ) {
                            Text("Finalize Sync")
                        }
                        TextButton(onClick = onCopyContractJson) {
                            Text("Copy Contract JSON")
                        }
                    }
                }
            }
        }

        // ---- Parsed Live Engine Baseline Snapshot Summary ----
        item {
            val snap = uiState.activeSnapshotForReview
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Active Engine Snapshot (${if (uiState.stagedSnapshot != null) "STAGED" else "VERIFIED BASELINE"} • ${snap.commitSha.take(10)})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Non-Locked Variants: ${snap.baseVariants.joinToString { it.baseVariantId }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "BigInt21Engine (${snap.bigInt21Spec.maxDigits} digits): ${snap.bigInt21Spec.supportedFunctions.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Firestore Bounds: Name≤${snap.firestoreBounds.maxCardNameLength}, Rules≤${snap.firestoreBounds.maxRulesTextLength}, Delimiters=${snap.firestoreBounds.reservedDelimiters.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "TurnPhases (${snap.schemaDefinitions.turnPhases.size}): ${snap.schemaDefinitions.turnPhases.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "CardEffectTypes (${snap.schemaDefinitions.cardEffectTypes.size}): ${snap.schemaDefinitions.cardEffectTypes.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Master Hub Admin Gate & Tri-Channel Notification Relays ----
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Master Hub Admin Gate",
                            tint = if (uiState.isCurrentUserAdmin) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Master Hub Admin Gate (${if (uiState.isCurrentUserAdmin) "ADMIN VERIFIED" else "NON-ADMIN"})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Text(
                        text = "Alignment & Update Warning pop-ups always appear on open when misalignment is detected so you can inspect diffs or open NexusTCG. Destructive Pause Sync, Discord Admin Webhook, and Email Relay actions remain strictly gated to verified Master Hub Admins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = adminRolesCsv,
                        onValueChange = { adminRolesCsv = it },
                        label = { Text("Master Hub Admin Role IDs (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = adminUsersCsv,
                        onValueChange = { adminUsersCsv = it },
                        label = { Text("Master Hub Admin User IDs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = adminEmailsCsv,
                        onValueChange = { adminEmailsCsv = it },
                        label = { Text("Master Hub Admin Emails") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = myRolesCsv,
                        onValueChange = { myRolesCsv = it },
                        label = { Text("Your Active Role IDs (for local Admin Gate)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = myUserId,
                            onValueChange = { myUserId = it },
                            label = { Text("Your User ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = myEmail,
                            onValueChange = { myEmail = it },
                            label = { Text("Your Email") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = discordWebhook,
                        onValueChange = { discordWebhook = it },
                        label = { Text("Discord Admin Alert Webhook URL (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = emailRelay,
                        onValueChange = { emailRelay = it },
                        label = { Text("Admin Email Relay HTTPS Webhook (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = {
                                onSaveAdminGateAndRelays(
                                    adminRolesCsv,
                                    adminUsersCsv,
                                    adminEmailsCsv,
                                    myRolesCsv,
                                    myUserId,
                                    myEmail,
                                    discordWebhook,
                                    emailRelay,
                                )
                            },
                            modifier = Modifier.testTag("engine_save_admin_gate_button"),
                        ) {
                            Text("Save Admin Gate")
                        }
                        if (uiState.isCurrentUserAdmin) {
                            OutlinedButton(onClick = onDispatchAdminNotifications) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (fineGrainedPat.isNotBlank()) "Send Hub+Discord+Email" else "Send Webhook Alerts")
                            }
                            OutlinedButton(onClick = onLaunchMailto) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mailto Escalation")
                            }
                        }
                        TextButton(onClick = onExportHealthJson) {
                            Text("Save Health JSON")
                        }
                    }

                    uiState.lastNotificationResults.forEach { res ->
                        Text(
                            text = "• ${res.channel}: ${res.detail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (res.success) StatusConnected else StatusOfflineAmber,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Automatic On-Open Alignment & Update Warning Pop-Up (Private Play Store & Live Sync).
 *
 * Visibility is NEVER hidden behind empty admin gate lists so the user is always warned
 * on open when `Tcg-game-engine-test`, `Project-MPV-RP-Master-Hub`, or Dual-Engine Consent
 * is misaligned. Destructive / external escalation actions remain strictly gated to verified
 * Master Hub Admins (`isCurrentUserAdmin == true`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlignmentWarningDialog(
    evaluation: AlignmentWarningEvaluation,
    healthStatus: EngineHealthStatusPayload,
    contract: HandshakeContract,
    missingEngineFiles: List<String>,
    stagedDiff: EngineCommitDiff?,
    companionEngineApp: InstalledCompanionAppInfo,
    isCurrentUserAdmin: Boolean,
    onOpenHandshakeArea: () -> Unit,
    onLaunchNexusTcgApp: () -> Unit,
    onExportDiagnosticReport: () -> Unit,
    onDismissForSha: () -> Unit,
    onHideTemporarily: () -> Unit,
    onAdminPauseSync: () -> Unit,
    onAdminSendDiscordWebhook: () -> Unit,
    onAdminSendEmailRelay: () -> Unit,
    onAdminLaunchMailto: () -> Unit,
) {
    val isBreaking = evaluation.engineHealthSeverity == EngineHealthSeverity.BREAKING_INCOMPATIBLE.wireValue
    val accentColor = if (isBreaking) MaterialTheme.colorScheme.error else StatusOfflineAmber

    AlertDialog(
        onDismissRequest = onHideTemporarily,
        icon = {
            Icon(
                imageVector = if (isBreaking) Icons.Default.Error else Icons.Default.Warning,
                contentDescription = "Cross-Repo Alignment Warning",
                tint = accentColor,
            )
        },
        title = {
            Text(
                text = if (isBreaking) {
                    "Alignment Alert: BREAKING"
                } else {
                    "Cross-Repo Alignment Check"
                },
                color = accentColor,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Engine Health: ${evaluation.engineHealthSeverity} (${healthStatus.targetEngineRepo})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Master Hub Link (${contract.masterHubRepo}): ${evaluation.mutualLinkStatus.wireValue}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Commit SHA: ${evaluation.failingCommitSha.take(12).ifBlank { "unknown" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Dual-Engine Consent: Card Maker=${contract.cardMakerConsent.status} • Game Engine=${contract.gameEngineConsent.status}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "NexusTCG Android App (${companionEngineApp.packageName}): ${companionEngineApp.summaryLabel}",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (evaluation.reasons.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        text = "Detected Misalignment:",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                    )
                    evaluation.reasons.forEach { reason ->
                        Text(
                            text = "• $reason",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (missingEngineFiles.isNotEmpty()) {
                    Text(
                        text = "Missing Engine Files: ${missingEngineFiles.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (healthStatus.brokenCapabilities.isNotEmpty()) {
                    Text(
                        text = "Broken Capabilities: ${healthStatus.brokenCapabilities.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                stagedDiff?.takeIf { it.hasDiff }?.let { diff ->
                    Text(
                        text = "Staged Schema Diff (${diff.fromCommitSha.take(8)} → ${diff.toCommitSha.take(8)}): Variants +${diff.addedVariantIds.size}/-${diff.removedVariantIds.size}, Phases +${diff.addedTurnPhases.size}/-${diff.removedTurnPhases.size}, Effects +${diff.addedEffectTypes.size}/-${diff.removedEffectTypes.size}, BoundsChanged=${diff.boundsChanged}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Inspection & Cross-App Actions (Available to ALL users)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = onOpenHandshakeArea,
                        modifier = Modifier.testTag("alignment_modal_open_handshake"),
                    ) {
                        Text("Open Handshake Area")
                    }
                    if (companionEngineApp.isInstalled || companionEngineApp.canHandleDeepLink) {
                        OutlinedButton(
                            onClick = onLaunchNexusTcgApp,
                            modifier = Modifier.testTag("alignment_modal_open_nexustcg"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Open NexusTCG App")
                        }
                    }
                    OutlinedButton(
                        onClick = onExportDiagnosticReport,
                        modifier = Modifier.testTag("alignment_modal_export_json"),
                    ) {
                        Text("Export Diagnostic JSON")
                    }
                }

                // Destructive & External Escalation Actions (Strictly Gated to Master Hub Admins)
                if (isCurrentUserAdmin) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Admin-Only Actions (Master Hub Verified):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = onAdminPauseSync,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("alignment_modal_admin_pause_sync"),
                        ) {
                            Text("Pause Sync")
                        }
                        OutlinedButton(
                            onClick = onAdminSendDiscordWebhook,
                            modifier = Modifier.testTag("alignment_modal_admin_discord"),
                        ) {
                            Text("Discord Webhook")
                        }
                        OutlinedButton(
                            onClick = onAdminSendEmailRelay,
                            modifier = Modifier.testTag("alignment_modal_admin_email"),
                        ) {
                            Text("Email Relay")
                        }
                        TextButton(
                            onClick = onAdminLaunchMailto,
                            modifier = Modifier.testTag("alignment_modal_admin_mailto"),
                        ) {
                            Text("Mailto")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissForSha,
                modifier = Modifier.testTag("alignment_modal_dismiss_sha"),
            ) {
                Text("Dismiss for This SHA")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onHideTemporarily,
                modifier = Modifier.testTag("alignment_modal_hide_later"),
            ) {
                Text("Close")
            }
        },
    )
}
