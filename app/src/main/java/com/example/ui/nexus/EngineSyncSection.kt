package com.example.ui.nexus

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.nexus.ConsentStatus
import com.example.domain.nexus.EngineHealthSeverity
import com.example.domain.nexus.EngineHealthStatusPayload
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber

/**
 * Live Engine Data Reader (`Summer-Warrior/Tcg-game-engine-test`), Two-Way Handshake
 * Dual-Engine Update Consent UI, and Master Hub Admin Gate / Tri-Channel Notification Controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineSyncSection(
    uiState: EngineSyncUiState,
    fineGrainedPat: String,
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onSyncLiveEngine,
                            enabled = !uiState.isSyncingEngine && !local.syncPaused,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("engine_sync_live_button"),
                        ) {
                            if (uiState.isSyncingEngine) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Reading Live Engine…")
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync Tcg-game-engine-test")
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
                        text = "Blocking alert modals appear strictly for authenticated Master Hub Admins. Discord Webhook & Email Relay are configurable and never store Discord Bot Tokens.",
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
 * High-priority blocking alert modal shown EXCLUSIVELY to authenticated Master Hub Admins
 * when `Tcg-game-engine-test` evaluates to DEGRADED or BREAKING_INCOMPATIBLE.
 */
@Composable
fun AdminBreakingChangeAlertModal(
    healthStatus: EngineHealthStatusPayload,
    onPauseSync: () -> Unit,
    onInspectDiff: () -> Unit,
    onExportDiagnosticReport: () -> Unit,
    onLaunchMailto: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Breaking Change Alert",
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = "Admin Alert: ${healthStatus.severity}",
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Engine Repo: ${healthStatus.targetEngineRepo}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Failing Commit: ${healthStatus.failingCommitSha.ifBlank { "unknown" }}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Master Hub Ack Status: ${healthStatus.masterHubAckStatus}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (healthStatus.brokenCapabilities.isNotEmpty()) {
                    Text(
                        text = "Broken Capabilities: ${healthStatus.brokenCapabilities.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (healthStatus.affectedCardFrames.isNotEmpty()) {
                    Text(
                        text = "Affected Card Frames: ${healthStatus.affectedCardFrames.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPauseSync,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("admin_modal_pause_sync"),
            ) {
                Text("Pause Sync")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onInspectDiff) { Text("Inspect Diff") }
                TextButton(onClick = onExportDiagnosticReport) { Text("Export Report") }
                TextButton(onClick = onLaunchMailto) { Text("Email") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
