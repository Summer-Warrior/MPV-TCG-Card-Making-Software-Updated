package com.example.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.mse.CatalogPackConfig
import com.example.data.mse.CatalogPackFilter
import com.example.data.mse.CatalogSyncState
import com.example.data.mse.DiscoveredGame
import com.example.data.mse.DiscoveredStyle

/**
 * Authentic Magic Set Editor Visual "New Set" Browser:
 * - Search bar ("Search games or frames...") + Pack filter chips (All Packs, Magic, Non-Magic, Beta, Outdated)
 * - Step 1: Visual 2-column Game Gallery displaying ALL discovered `.mse-game` games with real MSE thumbnails
 * - Step 2: Visual 2-column Card Style Gallery displaying ALL `.mse-style` frames with real MSE icon previews
 */
@Composable
fun NewSetWizardDialog(
    games: List<DiscoveredGame>,
    styles: List<DiscoveredStyle>,
    syncState: CatalogSyncState,
    onRefreshCatalog: () -> Unit,
    onToggleOptInPack: (CatalogPackFilter, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreateSet: (title: String, gameId: String, styleOption: DiscoveredStyle) -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }
    var searchQuery by remember { mutableStateOf("") }
    var packFilter by remember { mutableStateOf(CatalogPackFilter.ALL) }
    var selectedGame by remember(games) { mutableStateOf(games.firstOrNull()) }
    var setTitle by remember { mutableStateOf("") }

    val styleCountByGame = remember(styles) {
        styles.groupingBy { it.gameId.lowercase() }.eachCount()
    }

    val filteredGames = remember(games, styles, searchQuery, packFilter) {
        val q = searchQuery.trim().lowercase()
        games.filter { game ->
            val matchesPack = matchesPackFilter(game.packRepo, game.gameId, packFilter)
            val matchesQuery = q.isEmpty() ||
                game.displayName.lowercase().contains(q) ||
                game.gameId.lowercase().contains(q) ||
                styles.any {
                    it.gameId.equals(game.gameId, ignoreCase = true) &&
                        it.displayName.lowercase().contains(q)
                }
            matchesPack && matchesQuery
        }
    }

    val stylesForSelectedGame = remember(selectedGame?.gameId, styles, searchQuery, packFilter) {
        val gId = selectedGame?.gameId ?: return@remember emptyList()
        val q = searchQuery.trim().lowercase()
        styles.filter { style ->
            style.gameId.equals(gId, ignoreCase = true) &&
                matchesPackFilter(style.packRepo, style.gameId, packFilter) &&
                (q.isEmpty() ||
                    style.displayName.lowercase().contains(q) ||
                    style.stylesheetId.lowercase().contains(q) ||
                    style.packLabel.lowercase().contains(q))
        }
    }

    var selectedStyle by remember(stylesForSelectedGame) {
        mutableStateOf(stylesForSelectedGame.firstOrNull())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---- Header ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (step == 2) {
                            IconButton(
                                onClick = {
                                    step = 1
                                    searchQuery = ""
                                },
                                modifier = Modifier.testTag("wizard_back_step"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to game gallery")
                            }
                        }
                        Column {
                            Text(
                                text = if (step == 1) "New Set — Choose Game" else "New Set — Choose Frame",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (step == 1) {
                                    "${filteredGames.size} games • ${styles.size} total frames"
                                } else {
                                    "${selectedGame?.displayName.orEmpty()} • ${stylesForSelectedGame.size} frames"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRefreshCatalog,
                            enabled = !syncState.isSyncing,
                            modifier = Modifier.testTag("wizard_refresh_catalog"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh catalog")
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("wizard_close")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // ---- Incremental Sync Progress Bar (Unobtrusive) ----
                if (syncState.isSyncing) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { syncState.progressFraction.coerceIn(0.05f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = syncState.statusMessage.ifBlank { "Indexing Magic & Non-Magic packs…" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ---- Search Bar ----
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            if (step == 1) "Search games or frames…" else "Search ${selectedGame?.shortName.orEmpty()} frames…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wizard_search_input"),
                )

                // ---- Pack Filter Chips ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CatalogPackFilter.entries.forEach { filter ->
                        val isOptInDisabled = (filter == CatalogPackFilter.BETA && !syncState.betaEnabled) ||
                            (filter == CatalogPackFilter.OUTDATED && !syncState.outdatedEnabled)
                        FilterChip(
                            selected = packFilter == filter,
                            onClick = {
                                if (isOptInDisabled) {
                                    onToggleOptInPack(filter, true)
                                }
                                packFilter = filter
                            },
                            label = {
                                Text(
                                    text = if (isOptInDisabled) "+ ${filter.label}" else filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier.testTag("wizard_pack_${filter.name.lowercase()}"),
                        )
                    }
                }

                // ---- Gallery Body ----
                if (step == 1) {
                    if (filteredGames.isEmpty()) {
                        EmptyCatalogNotice(
                            isSyncing = syncState.isSyncing,
                            message = if (syncState.isSyncing) {
                                "Discovering games from MagicSetEditorPacks…"
                            } else {
                                "No games match your filter. Tap Refresh to sync Magic & Non-Magic packs."
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(filteredGames, key = { "${it.packRepo}:${it.gameId}" }) { game ->
                                val count = styleCountByGame[game.gameId.lowercase()] ?: 0
                                GameGalleryTile(
                                    game = game,
                                    styleCount = count,
                                    selected = game.gameId == selectedGame?.gameId,
                                    onClick = { selectedGame = game },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            enabled = selectedGame != null,
                            onClick = {
                                step = 2
                                searchQuery = ""
                            },
                            modifier = Modifier.testTag("wizard_next_step"),
                        ) {
                            Text("Choose Frame")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = setTitle,
                        onValueChange = { setTitle = it },
                        label = { Text("Set Title (optional)") },
                        placeholder = { Text("Untitled Set") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wizard_set_title_input"),
                    )

                    if (stylesForSelectedGame.isEmpty()) {
                        EmptyCatalogNotice(
                            isSyncing = syncState.isSyncing,
                            message = "No card styles discovered yet for ${selectedGame?.displayName.orEmpty()}.",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(stylesForSelectedGame, key = { "${it.packRepo}:${it.folderName}" }) { style ->
                                StyleGalleryTile(
                                    style = style,
                                    selected = style.folderName == selectedStyle?.folderName &&
                                        style.packRepo == selectedStyle?.packRepo,
                                    onClick = { selectedStyle = style },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = { step = 1 }) {
                            Text("Back")
                        }
                        Button(
                            enabled = selectedStyle != null && selectedGame != null,
                            onClick = {
                                val style = selectedStyle ?: return@Button
                                val game = selectedGame ?: return@Button
                                onCreateSet(
                                    setTitle.trim().ifEmpty { "Untitled Set" },
                                    game.gameId,
                                    style,
                                )
                            },
                            modifier = Modifier.testTag("wizard_create_set_button"),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Set")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameGalleryTile(
    game: DiscoveredGame,
    styleCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("wizard_game_${game.gameId}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (!game.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = game.iconUrl,
                        contentDescription = game.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Text(
                text = game.displayName,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = if (styleCount == 1) "1 frame" else "$styleCount frames",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
fun StyleGalleryTile(
    style: DiscoveredStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("style_tile_${style.stylesheetId}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Real MSE frame thumbnail preview (`icon:` from style file)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!style.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = style.iconUrl,
                        contentDescription = style.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }

                if (selected) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(3.dp),
                        )
                    }
                }
            }

            Text(
                text = style.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = style.packLabel
                            .removePrefix("Full-")
                            .removeSuffix("-Pack")
                            .ifBlank { "Local" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = style.dpiLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyCatalogNotice(
    isSyncing: Boolean,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (isSyncing) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.width(160.dp))
            }
        }
    }
}

private fun matchesPackFilter(packRepo: String, gameId: String, filter: CatalogPackFilter): Boolean {
    return when (filter) {
        CatalogPackFilter.ALL -> true
        CatalogPackFilter.MAGIC ->
            packRepo.equals("Full-Magic-Pack", ignoreCase = true) ||
                gameId.equals("magic", ignoreCase = true)

        CatalogPackFilter.NON_MAGIC ->
            packRepo.equals("Full-Non-Magic-Pack", ignoreCase = true) ||
                (!gameId.equals("magic", ignoreCase = true) && packRepo.isBlank())

        CatalogPackFilter.BETA ->
            packRepo.equals("Beta-Update-Pack", ignoreCase = true)

        CatalogPackFilter.OUTDATED ->
            packRepo.equals("Outdated-Templates", ignoreCase = true)
    }
}
