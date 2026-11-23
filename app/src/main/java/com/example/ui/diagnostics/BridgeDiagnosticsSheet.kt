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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.BridgeHealthState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber

private const val QUICK_TERMUX_CMD = """mse-bridge --host 127.0.0.1 --port 8765 \
  --modern-bin ~/mse-modern/magicseteditor \
  --legacy-bin ~/mse-legacy/magicseteditor"""

/**
 * Clean ModalBottomSheet for quick Bridge health checks, editable Bridge URL,
 * and Termux ARM64 launch commands opened by tapping the compact status dot/pill.
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
    var copied by remember { mutableStateOf(false) }

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
                .padding(bottom = 20.dp)
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

            Text(
                text = stringResource(R.string.bridge_offline_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(stringResource(R.string.bridge_url_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bridge_sheet_url_input"),
            )

            // Compact Termux command block
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = QUICK_TERMUX_CMD,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Termux MSE Bridge", QUICK_TERMUX_CMD))
                            copied = true
                        },
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Termux command")
                    }
                }
            }
            if (copied) {
                Text(
                    text = "Copied launch command to clipboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusConnected,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
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
                    Text("Full Setup")
                }
            }
        }
    }
}
