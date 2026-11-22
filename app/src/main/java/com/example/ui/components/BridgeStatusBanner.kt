package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.BridgeHealthState

/**
 * Live Bridge Connection & Diagnostics Banner.
 * Displays real HTTP health status, editable bridge base URL, engine split availability,
 * and Termux ARM64 startup instructions when the local MSE Bridge is offline.
 */
@Composable
fun BridgeStatusBanner(
    healthState: BridgeHealthState,
    currentBaseUrl: String,
    onSaveUrl: (String) -> Unit,
    onRetryHealth: () -> Unit,
    onOpenFullDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var editableUrl by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }

    val (dotColor, titleText, subtitleText, borderColor) = when (healthState) {
        is BridgeHealthState.Checking -> Quad(
            MaterialTheme.colorScheme.secondary,
            stringResource(R.string.bridge_checking),
            currentBaseUrl,
            MaterialTheme.colorScheme.outline,
        )

        is BridgeHealthState.Online -> {
            val engines = buildList {
                if (healthState.response.engines.mseModern) add("mse_modern")
                if (healthState.response.engines.mseLegacy) add("mse_legacy")
            }.joinToString(" + ").ifEmpty { "no engines reported" }
            Quad(
                MaterialTheme.colorScheme.tertiary,
                "${stringResource(R.string.bridge_online)} • ${healthState.latencyMs}ms",
                "$engines • ${healthState.response.installedSplits.size} split(s)",
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
            )
        }

        is BridgeHealthState.Offline -> Quad(
            MaterialTheme.colorScheme.error,
            stringResource(R.string.bridge_offline),
            "${healthState.baseUrl} • Tap for Termux setup & URL",
            MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("bridge_banner_toggle"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (healthState is BridgeHealthState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onRetryHealth,
                    modifier = Modifier.testTag("bridge_banner_retry"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Test bridge connection",
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse diagnostics" else "Expand diagnostics",
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (healthState is BridgeHealthState.Offline) {
                        Text(
                            text = "Last error: ${healthState.detail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.bridge_offline_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedTextField(
                        value = editableUrl,
                        onValueChange = { editableUrl = it },
                        label = { Text(stringResource(R.string.bridge_url_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bridge_banner_url_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onSaveUrl(editableUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bridge_banner_save_url"),
                        ) {
                            Text(stringResource(R.string.bridge_save_url))
                        }
                        OutlinedButton(
                            onClick = onOpenFullDiagnostics,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bridge_banner_open_diagnostics"),
                        ) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Termux Guide")
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
