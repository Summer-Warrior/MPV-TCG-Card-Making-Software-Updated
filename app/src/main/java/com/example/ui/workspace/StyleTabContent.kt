package com.example.ui.workspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.mse.CatalogSyncState
import com.example.data.mse.DiscoveredGame
import com.example.data.mse.DiscoveredStyle
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.MseTemplateCatalog
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.ui.welcome.StyleGalleryTile

/**
 * Authentic Visual MSE `Style` Tab:
 * Displays active `.mse-style` thumbnail & metadata, direct Searchable Font Dropdown button,
 * Physical 1:1 profile chips, SAF `.mse-style` loader, and a searchable 2-column visual frame gallery
 * of ALL discovered `.mse-style` frames for the active game with real MSE preview thumbnails.
 */
@Composable
fun StyleTabContent(
    archive: MseSetArchive,
    styleSpec: MseStyleSpec,
    settings: StudioSettings,
    discoveredGames: List<DiscoveredGame>,
    discoveredStyles: List<DiscoveredStyle>,
    catalogSyncState: CatalogSyncState,
    missingFontCount: Int,
    onRefreshCatalog: () -> Unit,
    onSwitchStylesheet: (DiscoveredStyle) -> Unit,
    onLoadStyleUri: (Uri) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    onOpenFontDropdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    val gameStyles = remember(archive.document.game, discoveredStyles, searchQuery) {
        val q = searchQuery.trim().lowercase()
        discoveredStyles.filter { style ->
            style.gameId.equals(archive.document.game, ignoreCase = true) &&
                (q.isEmpty() ||
                    style.displayName.lowercase().contains(q) ||
                    style.stylesheetId.lowercase().contains(q) ||
                    style.packLabel.lowercase().contains(q))
        }
    }

    val activeDiscoveredStyle = remember(archive.document.game, archive.document.stylesheet, discoveredStyles) {
        discoveredStyles.firstOrNull {
            it.gameId.equals(archive.document.game, ignoreCase = true) &&
                it.stylesheetId.equals(archive.document.stylesheet, ignoreCase = true)
        }
    }

    val loadStyleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onLoadStyleUri(uri)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- Active Template Metadata & Typography ----
        item(span = { GridItemSpan(2) }) {
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!activeDiscoveredStyle?.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = activeDiscoveredStyle?.iconUrl,
                                    contentDescription = styleSpec.shortName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Style,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = styleSpec.shortName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${MseTemplateCatalog.displayGameName(archive.document.game, discoveredGames)} • " +
                                    "${styleSpec.cardWidthPx}×${styleSpec.cardHeightPx} @ ${styleSpec.cardDpi} DPI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (styleSpec.referencedFonts.isNotEmpty()) {
                        Text(
                            text = "Template fonts: ${styleSpec.referencedFonts.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onOpenFontDropdown,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("style_tab_font_picker"),
                        ) {
                            Icon(Icons.Default.FontDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (missingFontCount > 0) "Fonts ($missingFontCount)" else "Fonts",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { loadStyleLauncher.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("style_tab_load_file"),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Load Style", maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---- Physical Card Dimensions Profile ----
        item(span = { GridItemSpan(2) }) {
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

        // ---- Visual Frame Gallery Header + Search ----
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${MseTemplateCatalog.displayGameName(archive.document.game, discoveredGames)} Frames (${gameStyles.size})",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onRefreshCatalog,
                        enabled = !catalogSyncState.isSyncing,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh frame gallery",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (catalogSyncState.isSyncing) {
                    LinearProgressIndicator(
                        progress = { catalogSyncState.progressFraction.coerceIn(0.05f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search frames…", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("style_tab_search_input"),
                )
            }
        }

        if (gameStyles.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (catalogSyncState.isSyncing) {
                            "Indexing upstream card frames…"
                        } else {
                            "No frames matched. Tap refresh or load a local .mse-style file."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(gameStyles, key = { "${it.packRepo}:${it.folderName}" }) { option ->
                val selected = archive.document.stylesheet.equals(option.stylesheetId, ignoreCase = true)
                StyleGalleryTile(
                    style = option,
                    selected = selected,
                    onClick = { onSwitchStylesheet(option) },
                )
            }
        }
    }
}
