package com.example.ui.welcome

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.bridge.BridgeTemplateDto
import com.example.data.mse.MseStyleOption
import com.example.data.mse.MseTemplateCatalog
import com.example.data.mse.RecentSetRecord
import com.example.ui.BridgeHealthState
import com.example.ui.components.BridgeStatusPill

/**
 * Authentic Magic Set Editor Welcome Window (Startup Screen).
 * Zero clutter, zero clipping on 360dp phone widths, zero fake sample sets.
 */
@Composable
fun WelcomeScreen(
    bridgeHealth: BridgeHealthState,
    bridgeTemplates: List<BridgeTemplateDto>,
    recentSets: List<RecentSetRecord>,
    onOpenBridgeSheet: () -> Unit,
    onCreateNewSet: (title: String, gameId: String, styleOption: MseStyleOption) -> Unit,
    onOpenSetUri: (Uri) -> Unit,
    onOpenRecentSet: (RecentSetRecord) -> Unit,
    onOpenFontsManager: () -> Unit,
    onOpenUpstreamManager: () -> Unit,
    onOpenBridgeSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewSetWizard by remember { mutableStateOf(false) }

    val openSetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onOpenSetUri(uri)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Top MSE Header ----
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.welcome_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                BridgeStatusPill(
                    healthState = bridgeHealth,
                    onClick = onOpenBridgeSheet,
                )
            }
        }

        // ---- Primary MSE Welcome Actions ----
        item {
            WelcomeActionCard(
                icon = Icons.Default.AddBox,
                title = stringResource(R.string.welcome_new_set),
                subtitle = stringResource(R.string.welcome_new_set_desc),
                emphasized = true,
                onClick = { showNewSetWizard = true },
                modifier = Modifier.testTag("welcome_new_set_card"),
            )
        }

        item {
            WelcomeActionCard(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.welcome_open_set),
                subtitle = stringResource(R.string.welcome_open_set_desc),
                emphasized = false,
                onClick = {
                    openSetLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                modifier = Modifier.testTag("welcome_open_set_card"),
            )
        }

        // Quick "Open Last Set" action if at least one real recent set exists
        recentSets.firstOrNull()?.let { lastSet ->
            item {
                WelcomeActionCard(
                    icon = Icons.Default.History,
                    title = "${stringResource(R.string.welcome_open_last)}: ${lastSet.title}",
                    subtitle = "${MseTemplateCatalog.displayGameName(lastSet.game)} • ${lastSet.stylesheet} • ${lastSet.cardCount} card(s)",
                    emphasized = false,
                    onClick = { onOpenRecentSet(lastSet) },
                    modifier = Modifier.testTag("welcome_open_last_card"),
                )
            }
        }

        // ---- Secondary Tools Row (Fonts, Upstream Splits, Bridge & Termux) ----
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryToolTile(
                    icon = Icons.Default.FontDownload,
                    label = stringResource(R.string.tool_fonts),
                    onClick = onOpenFontsManager,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_tool_fonts"),
                )
                SecondaryToolTile(
                    icon = Icons.Default.CloudSync,
                    label = stringResource(R.string.tool_upstream),
                    onClick = onOpenUpstreamManager,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_tool_upstream"),
                )
                SecondaryToolTile(
                    icon = Icons.Default.Terminal,
                    label = stringResource(R.string.tool_bridge),
                    onClick = onOpenBridgeSetup,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_tool_bridge"),
                )
            }
        }

        // ---- Recent Sets Section (Strictly Real User Sets Only) ----
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.welcome_recent_sets),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (recentSets.isEmpty()) {
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
                            .padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = stringResource(R.string.welcome_no_recent_sets),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Create a new set or open an existing .mse-set file to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(recentSets, key = { it.id }) { record ->
                RecentSetRow(
                    record = record,
                    onClick = { onOpenRecentSet(record) },
                )
            }
        }
    }

    if (showNewSetWizard) {
        NewSetWizardDialog(
            bridgeTemplates = bridgeTemplates,
            onDismiss = { showNewSetWizard = false },
            onCreateSet = { title, gameId, styleOption ->
                showNewSetWizard = false
                onCreateNewSet(title, gameId, styleOption)
            },
        )
    }
}

@Composable
private fun WelcomeActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(10.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecondaryToolTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentSetRow(
    record: RecentSetRecord,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recent_set_${record.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${MseTemplateCatalog.displayGameName(record.game)} • ${record.stylesheet} • ${record.cardCount} card(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = record.lastOpenedAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
