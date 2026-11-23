package com.example.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.BridgeHealthState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber

internal data class TermuxSetupStep(
    val id: String,
    val title: String,
    val subtitle: String,
    val command: String,
)

internal val TERMUX_SETUP_STEPS = listOf(
    TermuxSetupStep(
        id = "step1_ubuntu",
        title = "Step 1 — Termux First-Time Ubuntu Setup",
        subtitle = "Run inside Termux on Android to install Ubuntu 24.04 ARM64",
        command = "pkg update && pkg upgrade -y && termux-setup-storage && pkg install -y proot-distro git && proot-distro install ubuntu && proot-distro login ubuntu",
    ),
    TermuxSetupStep(
        id = "step2_setup",
        title = "Step 2 — Clone Repo & Build/Sync MSE Bridge",
        subtitle = "Run inside Ubuntu proot-distro to install MSE + delta-sync packs & fonts",
        command = "git clone --depth 1 https://github.com/Summer-Warrior/MPV-TCG-Card-Making-Software-Updated.git ~/tcg-studio && cd ~/tcg-studio && bash bridge/setup_termux.sh",
    ),
    TermuxSetupStep(
        id = "step3_start",
        title = "Step 3 — Start Local MSE Render Engine",
        subtitle = "Launches 127.0.0.1:8765 for live 1:1 desktop-identical rendering",
        command = "cd ~/tcg-studio && bash bridge/start_bridge.sh",
    ),
)

/**
 * Clean ModalBottomSheet for live Termux ARM64 MSE Bridge connection,
 * 1-tap copyable Android-only setup/launch steps, and editable Bridge URL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeDiagnosticsSheet(
    healthState: BridgeHealthState,
    currentBaseUrl: String,
    onSaveUrl: (String) -> Unit,
    onRetryHealth: () -> Unit,
    onOpenFullSetup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var urlInput by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }
    var copiedStepId by remember { mutableStateOf<String?>(null) }

    val displayHostPort = remember(urlInput) {
        urlInput.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
            .ifBlank { "127.0.0.1:8765" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.bridge_diagnostics_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Live status summary row
            val dotColor = when (healthState) {
                is BridgeHealthState.Online -> StatusConnected
                is BridgeHealthState.Offline -> StatusOfflineAmber
                is BridgeHealthState.Checking -> MaterialTheme.colorScheme.secondary
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (healthState is BridgeHealthState.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (healthState) {
                                is BridgeHealthState.Online -> "Connected (${healthState.latencyMs} ms)"
                                is BridgeHealthState.Offline -> "Local Bridge Offline"
                                is BridgeHealthState.Checking -> "Checking connection…"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = when (healthState) {
                                is BridgeHealthState.Online -> {
                                    val engines = buildList {
                                        if (healthState.response.engines.mseModern) add("MSE Modern")
                                        if (healthState.response.engines.mseLegacy) add("MSE Legacy")
                                    }.joinToString(" • ").ifEmpty { "Ready" }
                                    "$engines • ${healthState.response.installedSplits.size} pack split(s)"
                                }

                                is BridgeHealthState.Offline -> healthState.detail
                                is BridgeHealthState.Checking -> currentBaseUrl
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onRetryHealth,
                        modifier = Modifier.testTag("bridge_sheet_retry"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry bridge health")
                    }
                }
            }

            // Prominent Test & Connect CTA
            Button(
                onClick = {
                    val trimmed = urlInput.trim()
                    if (trimmed.isNotEmpty() && trimmed != currentBaseUrl) {
                        onSaveUrl(trimmed)
                    } else {
                        onRetryHealth()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("bridge_sheet_test_connect"),
            ) {
                if (healthState is BridgeHealthState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Test & Connect ($displayHostPort)",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Run these 3 Android-only steps in Termux to enable live desktop-identical Magic & Non-Magic rendering:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 1-Tap Copyable Step-by-Step Termux Commands
            TERMUX_SETUP_STEPS.forEach { step ->
                TermuxStepCard(
                    step = step,
                    isCopied = copiedStepId == step.id,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(step.title, step.command))
                        copiedStepId = step.id
                    },
                )
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(stringResource(R.string.bridge_url_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bridge_sheet_url_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onSaveUrl(urlInput) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bridge_sheet_save_url"),
                ) {
                    Text(stringResource(R.string.bridge_save_url))
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenFullSetup()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bridge_sheet_full_setup"),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Diagnostics")
                }
            }
        }
    }
}

@Composable
internal fun TermuxStepCard(
    step: TermuxSetupStep,
    isCopied: Boolean,
    onCopy: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = 1.dp,
            color = if (isCopied) StatusConnected.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = step.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.testTag("copy_${step.id}"),
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy ${step.title}",
                        tint = if (isCopied) StatusConnected else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = step.command,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (isCopied) {
                Text(
                    text = "Copied command to clipboard.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusConnected,
                )
            }
        }
    }
}
