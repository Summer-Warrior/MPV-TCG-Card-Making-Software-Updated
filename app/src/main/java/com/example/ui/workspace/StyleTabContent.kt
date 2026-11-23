package com.example.ui.workspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.bridge.BridgeTemplateDto
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseStyleOption
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.MseTemplateCatalog
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings

/**
 * Classic MSE `Style` Tab.
 * Displays active `.mse-style` selector, template options, DPI & physical profile metadata,
 * SAF `.mse-style` loader, and direct Font Picker trigger.
 */
@Composable
fun StyleTabContent(
    archive: MseSetArchive,
    styleSpec: MseStyleSpec,
    settings: StudioSettings,
    bridgeTemplates: List<BridgeTemplateDto>,
    missingFontCount: Int,
    onSwitchStylesheet: (MseStyleOption) -> Unit,
    onLoadStyleUri: (Uri) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    onOpenFontDropdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val styles = remember(archive.document.game, bridgeTemplates) {
        MseTemplateCatalog.resolveStylesForGame(archive.document.game, bridgeTemplates)
    }

    val loadStyleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onLoadStyleUri(uri)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Active Template Metadata & Typography ----
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
                        text = styleSpec.shortName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${MseTemplateCatalog.displayGameName(archive.document.game)} • " +
                            "${styleSpec.cardWidthPx} × ${styleSpec.cardHeightPx} px at ${styleSpec.cardDpi} DPI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (styleSpec.referencedFonts.isNotEmpty()) {
                        Text(
                            text = "Template fonts: ${styleSpec.referencedFonts.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onOpenFontDropdown,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("style_tab_font_picker"),
                        ) {
                            Icon(Icons.Default.FontDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (missingFontCount > 0) "Fonts ($missingFontCount missing)" else "Choose Font",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { loadStyleLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("style_tab_load_file"),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Load Style")
                        }
                    }
                }
            }
        }

        // ---- Physical Card Dimensions Profile ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Physical 1:1 Card Profile",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhysicalCardProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = settings.physicalProfile == profile,
                            onClick = { onUpdatePhysicalProfile(profile) },
                            label = { Text(profile.label) },
                        )
                    }
                }
            }
        }

        // ---- Available Stylesheets for Active Game ----
        item {
            Text(
                text = "Card Styles for ${MseTemplateCatalog.displayGameName(archive.document.game)}",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        items(styles, key = { it.stylesheetId }) { option ->
            val selected = archive.document.stylesheet.equals(option.stylesheetId, ignoreCase = true)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSwitchStylesheet(option) }
                    .testTag("style_option_${option.stylesheetId}"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${option.packSplit} • ${option.dpiLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active style",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
