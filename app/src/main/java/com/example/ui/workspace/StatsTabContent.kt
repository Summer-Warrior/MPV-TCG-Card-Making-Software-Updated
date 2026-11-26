package com.example.ui.workspace

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.mse.MseCardView
import com.example.data.mse.MseSetArchive
import com.example.domain.comparison.CardOrigin
import com.example.domain.telemetry.TelemetryInteractionType
import com.example.ui.telemetry.CardUsageReportsContent
import com.example.ui.telemetry.CardUsageReportsUiState

private data class StatBucket(
    val label: String,
    val count: Int,
    val color: Color,
)

private data class SetStatistics(
    val totalCards: Int,
    val cardsWithArt: Int,
    val namedCards: Int,
    val averageCost: Double,
    val colorBuckets: List<StatBucket>,
    val typeBuckets: List<StatBucket>,
    val costCurve: List<Pair<String, Int>>,
)

/**
 * Authentic 5th MSE Workspace Tab (`Stats`):
 * Computes real live set statistics directly from the active `.mse-set` archive:
 * Card Count, Art Coverage, Color/Frame Distribution, Type Distribution, and Cost/Mana Curve.
 */
@Composable
fun StatsTabContent(
    archive: MseSetArchive,
    usageReportsState: CardUsageReportsUiState = CardUsageReportsUiState(),
    githubPat: String = "",
    onOpenSetComparison: () -> Unit = {},
    onPullUsageReports: (String) -> Unit = {},
    onImportUsageReportUri: (Uri) -> Unit = {},
    onSelectUsageOriginFilter: (CardOrigin?) -> Unit = {},
    onSelectUsageInteractionFilter: (TelemetryInteractionType?) -> Unit = {},
    onUsageSearchQueryChanged: (String) -> Unit = {},
    onEditTelemetryCardInStudio: (cardId: String, cardName: String) -> Unit = { _, _ -> },
    onBranchTelemetryCardVariant: (cardId: String, cardName: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val cards = archive.document.cards
    val stats = remember(cards) { computeSetStatistics(cards) }
    var subTab by remember { mutableIntStateOf(0) } // 0 = Set Stats, 1 = Usage & Interactions

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    label = { Text("Set Stats") },
                    modifier = Modifier.testTag("stats_subtab_set"),
                )
                FilterChip(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    label = {
                        Text(
                            if (usageReportsState.dashboard.totalReportsCount > 0) {
                                "Usage (${usageReportsState.dashboard.totalReportsCount})"
                            } else {
                                "Usage & Interactions"
                            }
                        )
                    },
                    modifier = Modifier.testTag("stats_subtab_usage"),
                )
            }

            FilledTonalButton(
                onClick = onOpenSetComparison,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("stats_compare_set_button"),
            ) {
                Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Compare Set", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        if (subTab == 1) {
            CardUsageReportsContent(
                uiState = usageReportsState,
                githubPat = githubPat,
                hasActiveSetOpen = true,
                onPullFromGitHub = onPullUsageReports,
                onImportFromUri = onImportUsageReportUri,
                onSelectOriginFilter = onSelectUsageOriginFilter,
                onSelectInteractionFilter = onSelectUsageInteractionFilter,
                onSearchQueryChanged = onUsageSearchQueryChanged,
                onEditCardInStudio = onEditTelemetryCardInStudio,
                onBranchCardVariant = onBranchTelemetryCardVariant,
                modifier = Modifier.weight(1f),
            )
            return
        }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("stats_tab_list"),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Top Summary KPI Strip ----
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile(
                    label = "Cards",
                    value = stats.totalCards.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "With Art",
                    value = "${stats.cardsWithArt}/${stats.totalCards}",
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "Avg Cost",
                    value = if (stats.averageCost > 0.0) "%.1f".format(stats.averageCost) else "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- Cost / Mana Curve Bar Chart ----
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Cost / Mana Curve",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val maxBucket = stats.costCurve.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        stats.costCurve.forEach { (bucketLabel, count) ->
                            val heightFraction = (count.toFloat() / maxBucket.toFloat()).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Text(
                                    text = if (count > 0) count.toString() else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.76f)
                                            .fillMaxHeight(if (count == 0) 0.04f else heightFraction.coerceAtLeast(0.10f))
                                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                            .background(
                                                if (count > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = bucketLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Color / Frame Distribution ----
        item {
            DistributionCard(
                title = "Color / Frame Distribution",
                buckets = stats.colorBuckets,
                total = stats.totalCards,
            )
        }

        // ---- Card Type Distribution ----
        item {
            DistributionCard(
                title = "Card Type Distribution",
                buckets = stats.typeBuckets,
                total = stats.totalCards,
            )
        }
    }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DistributionCard(
    title: String,
    buckets: List<StatBucket>,
    total: Int,
) {
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
            Text(text = title, style = MaterialTheme.typography.titleMedium)

            val denominator = total.coerceAtLeast(1)
            buckets.forEach { bucket ->
                val ratio = (bucket.count.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(bucket.color),
                            )
                            Text(
                                text = bucket.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "${bucket.count} (${(ratio * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        if (ratio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(bucket.color),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun computeSetStatistics(cards: List<MseCardView>): SetStatistics {
    var withArt = 0
    var named = 0
    var costSum = 0
    var costCount = 0

    val colorCounts = linkedMapOf(
        "White" to 0,
        "Blue" to 0,
        "Black" to 0,
        "Red" to 0,
        "Green" to 0,
        "Multicolor" to 0,
        "Colorless / Other" to 0,
    )

    val typeCounts = linkedMapOf<String, Int>()
    val curve = IntArray(7) // 0, 1, 2, 3, 4, 5, 6+

    for (card in cards) {
        if (card.image != null) withArt++
        if (card.name.isNotBlank()) named++

        val costStr = card.castingCost.trim()
        val parsedCmc = parseManaValue(costStr)
        if (costStr.isNotEmpty()) {
            costSum += parsedCmc
            costCount++
            curve[parsedCmc.coerceIn(0, 6)]++
        }

        // Color classification (explicit `card color` first, else inferred from casting cost)
        val explicitColor = card.field("card color").lowercase()
        val colorBucket = classifyColor(explicitColor, costStr)
        colorCounts[colorBucket] = (colorCounts[colorBucket] ?: 0) + 1

        // Supertype / card type classification
        val typeBucket = classifyType(card.superType)
        typeCounts[typeBucket] = (typeCounts[typeBucket] ?: 0) + 1
    }

    val colorPalette = mapOf(
        "White" to Color(0xFFE8DFC0),
        "Blue" to Color(0xFF4F93D1),
        "Black" to Color(0xFF7B6994),
        "Red" to Color(0xFFD45B49),
        "Green" to Color(0xFF479B65),
        "Multicolor" to Color(0xFFD4A84B),
        "Colorless / Other" to Color(0xFF8C97A8),
    )

    val colorBuckets = colorCounts.entries.map { (k, v) ->
        StatBucket(label = k, count = v, color = colorPalette[k] ?: Color(0xFF8C97A8))
    }

    val typePalette = listOf(
        Color(0xFFD4A84B),
        Color(0xFF5D9BD5),
        Color(0xFF5BB37D),
        Color(0xFFE07A5F),
        Color(0xFF9C7EC8),
        Color(0xFF7F95A8),
    )
    val typeBuckets = typeCounts.entries
        .sortedByDescending { it.value }
        .mapIndexed { idx, (label, count) ->
            StatBucket(label = label, count = count, color = typePalette[idx % typePalette.size])
        }
        .ifEmpty {
            listOf(StatBucket(label = "Untyped", count = cards.size, color = typePalette[0]))
        }

    val curveList = listOf(
        "0" to curve[0],
        "1" to curve[1],
        "2" to curve[2],
        "3" to curve[3],
        "4" to curve[4],
        "5" to curve[5],
        "6+" to curve[6],
    )

    return SetStatistics(
        totalCards = cards.size,
        cardsWithArt = withArt,
        namedCards = named,
        averageCost = if (costCount > 0) costSum.toDouble() / costCount.toDouble() else 0.0,
        colorBuckets = colorBuckets,
        typeBuckets = typeBuckets,
        costCurve = curveList,
    )
}

internal fun parseManaValue(rawCost: String): Int {
    if (rawCost.isBlank()) return 0
    val cleaned = rawCost.replace("{", " ").replace("}", " ").trim()
    var total = 0
    val numberRegex = Regex("\\d+")
    val symbolLetters = Regex("[WUBRGCS]")
    if (rawCost.contains("{")) {
        val tokens = Regex("\\{([^}]+)\\}").findAll(rawCost).map { it.groupValues[1].trim() }.toList()
        for (tok in tokens) {
            val asInt = tok.toIntOrNull()
            when {
                asInt != null -> total += asInt
                tok.equals("X", ignoreCase = true) -> Unit
                tok.contains("/") -> total += 1
                tok.isNotEmpty() -> total += 1
            }
        }
    } else {
        numberRegex.findAll(cleaned).forEach { m ->
            total += m.value.toIntOrNull() ?: 0
        }
        total += symbolLetters.findAll(cleaned.uppercase()).count()
    }
    return total.coerceAtLeast(0)
}

private fun classifyColor(explicitColor: String, castingCost: String): String {
    val active = mutableSetOf<String>()
    if (explicitColor.isNotBlank()) {
        if (explicitColor.contains("white")) active.add("White")
        if (explicitColor.contains("blue")) active.add("Blue")
        if (explicitColor.contains("black")) active.add("Black")
        if (explicitColor.contains("red")) active.add("Red")
        if (explicitColor.contains("green")) active.add("Green")
        if (explicitColor.contains("multi") || explicitColor.contains("gold")) return "Multicolor"
    }
    if (active.isEmpty() && castingCost.isNotBlank()) {
        val upper = castingCost.uppercase()
        if (upper.contains('W')) active.add("White")
        if (upper.contains('U')) active.add("Blue")
        if (upper.contains('B')) active.add("Black")
        if (upper.contains('R')) active.add("Red")
        if (upper.contains('G')) active.add("Green")
    }
    return when {
        active.size >= 2 -> "Multicolor"
        active.size == 1 -> active.first()
        else -> "Colorless / Other"
    }
}

private fun classifyType(superType: String): String {
    val low = superType.trim().lowercase()
    return when {
        low.isEmpty() -> "Untyped"
        low.contains("creature") -> "Creature"
        low.contains("instant") -> "Instant"
        low.contains("sorcery") -> "Sorcery"
        low.contains("artifact") -> "Artifact"
        low.contains("enchantment") -> "Enchantment"
        low.contains("planeswalker") -> "Planeswalker"
        low.contains("land") -> "Land"
        low.contains("monster") -> "Monster"
        low.contains("spell") -> "Spell"
        low.contains("trap") -> "Trap"
        low.contains("trainer") -> "Trainer"
        else -> superType.trim().split(' ').firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Other"
    }
}
