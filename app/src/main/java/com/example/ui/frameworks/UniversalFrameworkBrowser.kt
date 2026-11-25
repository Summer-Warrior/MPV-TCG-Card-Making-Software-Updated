package com.example.ui.frameworks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.frameworks.FrameworkCategory
import com.example.domain.frameworks.UniversalCardFramework

enum class FrameworkFilterChip(val key: String, val label: String) {
    ALL("ALL", "All"),
    PHYSICAL("PHYSICAL_ACTIVE", "Physical"),
    DIGITAL("DIGITAL_ACTIVE", "Digital"),
    VIDEO_GAMES("VIDEO_GAME_TCG", "Video Games"),
    MINI_GAMES("VIDEO_GAME_MINIGAME", "Mini-Games"),
    NICHE_LCG("NICHE_INDIE_LCG", "Niche/LCG"),
    DEFUNCT("DEFUNCT", "Defunct"),
    ULTIMATE_NOTEBOOK("ULTIMATE_NOTEBOOK", "Ultimate/Notebook"),
}

fun filterUniversalFrameworks(
    frameworks: List<UniversalCardFramework>,
    query: String,
    chip: FrameworkFilterChip,
): List<UniversalCardFramework> {
    val q = query.trim().lowercase()
    return frameworks.filter { fw ->
        val catMatch = when (chip) {
            FrameworkFilterChip.ALL -> true
            FrameworkFilterChip.DEFUNCT -> fw.categoryEnum == FrameworkCategory.DEFUNCT_PHYSICAL ||
                fw.categoryEnum == FrameworkCategory.DEFUNCT_DIGITAL
            else -> fw.category.equals(chip.key, ignoreCase = true)
        }
        val qMatch = q.isEmpty() ||
            fw.name.lowercase().contains(q) ||
            fw.shortName.lowercase().contains(q) ||
            fw.id.lowercase().contains(q) ||
            fw.mechanicsSummary.lowercase().contains(q)
        catMatch && qMatch
    }
}

@Composable
fun FrameworkCategoryFilterBar(
    selectedChip: FrameworkFilterChip,
    onSelectChip: (FrameworkFilterChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FrameworkFilterChip.entries.forEach { chip ->
            FilterChip(
                selected = selectedChip == chip,
                onClick = { onSelectChip(chip) },
                label = { Text(chip.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier.testTag("framework_chip_${chip.name.lowercase()}"),
            )
        }
    }
}

@Composable
fun FrameworkSearchField(
    query: String,
    totalCount: Int,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search $totalCount+ physical & digital TCGs…", maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("framework_search_input"),
    )
}

@Composable
fun UniversalFrameworkCard(
    framework: UniversalCardFramework,
    selected: Boolean,
    hasInstalledUpstreamStyle: Boolean,
    onSelect: () -> Unit,
    onGenerateScaffold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("universal_framework_${framework.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = framework.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${framework.categoryEnum.chipLabel} • ${"%.0f".format(framework.cardWidthMm)}×${"%.0f".format(framework.cardHeightMm)}mm • ${framework.cardWidthPx}×${framework.cardHeightPx}px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (framework.mechanicsSummary.isNotBlank()) {
                Text(
                    text = framework.mechanicsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = if (hasInstalledUpstreamStyle) {
                            "Upstream MSE Pack Ready"
                        } else {
                            "Clean-Room Scaffold"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                FilledTonalButton(
                    onClick = onGenerateScaffold,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("generate_scaffold_${framework.id}"),
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gen MSE Scaffold", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
