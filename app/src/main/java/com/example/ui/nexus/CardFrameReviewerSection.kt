package com.example.ui.nexus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.EngineHealthSeverity
import com.example.domain.nexus.OmnichromaSacredCatalog
import com.example.domain.nexus.SacredCelestialShape
import com.example.domain.nexus.TwentyColorPieCatalog
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber
import com.example.ui.theme.StudioGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dedicated Card Frame Reviewer auditing card frames against BOTH the live
 * `Tcg-game-engine-test` snapshot AND the WIP TCG Onboarding Spec:
 * - Zero default 20-Color Pie colors selected on load; shows ALL pips unless actively filtered.
 * - Layers 1–19 strictly render Diamond-shaped Option B pip icons (Pure, :T, :S, :M, splits, crowned leads).
 * - Layer 20 Omnichroma {Omega} breaks free from Diamonds into 6 bespoke sacred celestial geometries.
 * - Auto-generates Lua (c<codeString>.lua), Forge SVar, Hybrid script, and sanitized board descriptor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardFrameReviewerSection(
    uiState: EngineSyncUiState,
    activeMseCardName: String?,
    activeMseCardType: String?,
    activeMseCardRules: String?,
    activeFrameWidthPx: Int,
    activeFrameHeightPx: Int,
    viewModel: EngineSyncViewModel,
    onCopyText: (label: String, text: String) -> Unit,
) {
    val snapshot = uiState.activeSnapshotForReview
    var scriptTab by remember { mutableIntStateOf(0) }
    val query = uiState.reviewPipFilterQuery.trim().lowercase()

    val filteredDiamondPips = remember(query) {
        val allBase = listOf(TwentyColorPieCatalog.colorlessPip) + TwentyColorPieCatalog.baseTwentyColors
        if (query.isEmpty()) allBase else allBase.filter {
            it.code.lowercase().contains(query) || it.label.lowercase().contains(query)
        }
    }
    val filteredSacredPips = remember(query) {
        if (query.isEmpty()) OmnichromaSacredCatalog.sixSacredGeometries
        else OmnichromaSacredCatalog.sixSacredGeometries.filter {
            it.code.lowercase().contains(query) || it.geometryName.lowercase().contains(query)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Active Engine Compatibility Header ----
        item {
            val audit = uiState.reviewAuditResult
            val isOk = audit?.isCompatible == true
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (isOk) StatusConnected else MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = "Card Frame Review Status",
                            tint = if (isOk) StatusConnected else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = if (isOk) {
                                "Frame Compatible with Tcg-game-engine-test@${snapshot.commitSha.take(8)}"
                            } else {
                                "Frame Compatibility Flags (${audit?.issues?.size ?: 0})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Text(
                        text = "Audited Geometry: ${uiState.reviewFrameWidthPx}×${uiState.reviewFrameHeightPx}px • Bounds: Name≤${snapshot.firestoreBounds.maxCardNameLength}, Rules≤${snapshot.firestoreBounds.maxRulesTextLength}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!activeMseCardName.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateReviewerFields(
                                    cardName = activeMseCardName,
                                    cardType = activeMseCardType.orEmpty(),
                                    rulesText = activeMseCardRules.orEmpty(),
                                    frameWidthPx = activeFrameWidthPx,
                                    frameHeightPx = activeFrameHeightPx,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Load Active Card Frame: $activeMseCardName (${activeFrameWidthPx}×${activeFrameHeightPx})")
                        }
                    }

                    audit?.issues?.forEach { issue ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (issue.severity == EngineHealthSeverity.BREAKING_INCOMPATIBLE) {
                                    Icons.Default.Error
                                } else {
                                    Icons.Default.Warning
                                },
                                contentDescription = null,
                                tint = if (issue.severity == EngineHealthSeverity.BREAKING_INCOMPATIBLE) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    StatusOfflineAmber
                                },
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "[${issue.code}] ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (issue.severity == EngineHealthSeverity.BREAKING_INCOMPATIBLE) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    StatusOfflineAmber
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---- Target Variant, Ruleset Effect & Frame Fields ----
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
                    Text(
                        text = "Non-Locked Base Variant & Engine Ruleset",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        snapshot.baseVariants.forEach { variant ->
                            FilterChip(
                                selected = uiState.reviewBaseVariantId == variant.baseVariantId,
                                onClick = { viewModel.updateReviewerFields(baseVariantId = variant.baseVariantId) },
                                label = { Text(variant.baseVariantId) },
                                modifier = Modifier.testTag("reviewer_variant_${variant.baseVariantId}"),
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        snapshot.schemaDefinitions.cardEffectTypes.forEach { effect ->
                            FilterChip(
                                selected = uiState.reviewEffectType == effect,
                                onClick = { viewModel.updateReviewerFields(effectType = effect) },
                                label = { Text(effect) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.reviewCardName,
                        onValueChange = { viewModel.updateReviewerFields(cardName = it) },
                        label = { Text("Card Name (checks Firestore length & '|' delimiter)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.reviewCodeString,
                        onValueChange = { viewModel.updateReviewerFields(codeString = it) },
                        label = { Text("21-Digit Code String (BigInt21Engine)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.reviewCardType,
                            onValueChange = { viewModel.updateReviewerFields(cardType = it) },
                            label = { Text("Card Type") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = uiState.reviewStatText,
                            onValueChange = { viewModel.updateReviewerFields(statText = it) },
                            label = { Text("Stats (ATK/DEF or P/T)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = uiState.reviewRulesText,
                        onValueChange = { viewModel.updateReviewerFields(rulesText = it) },
                        label = { Text("Rules Text") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Cost Box Flavor Filters & Editor Mode",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CardEditorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.reviewEditorMode == mode,
                                onClick = { viewModel.updateReviewerFields(editorMode = mode) },
                                label = { Text(mode.displayLabel) },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(19, 20, 21).forEach { fid ->
                            FilterChip(
                                selected = fid in uiState.reviewFlavorFilters,
                                onClick = { viewModel.toggleReviewFlavorFilter(fid) },
                                label = { Text("Filter $fid") },
                            )
                        }
                    }
                }
            }
        }

        // ---- 20-Color Pie + {C} Diamond Option B & Layer 20 6 Bespoke Sacred Celestial Geometries ----
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "20-Color Pie & Omnichroma Pip Reviewer",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (uiState.reviewSelectedPips.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearReviewPips) { Text("Clear") }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.reviewPipFilterQuery,
                        onValueChange = { viewModel.updateReviewerFields(pipFilterQuery = it) },
                        label = { Text("Filter Pips (blank = show ALL pips)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (uiState.reviewSelectedPips.isEmpty()) {
                        Text(
                            text = "ZERO default colors selected. Tap any Diamond Option B or Layer 20 Omnichroma pip below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            uiState.reviewSelectedPips.forEachIndexed { idx, pip ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removeReviewPipAt(idx) },
                                    label = { Text(pip) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "Remove $pip", modifier = Modifier.size(14.dp))
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        text = "Layers 1–19 — Diamond Option B Pips (Pure, :T, :S, :M, Splits, Crowned Leads)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        filteredDiamondPips.forEach { spec ->
                            AssistChip(
                                onClick = { viewModel.appendReviewPip(spec.code) },
                                leadingIcon = { DiamondOptionBPipIcon(color = colorForPipCode(spec.code)) },
                                label = { Text("${spec.code} ${spec.label}") },
                            )
                        }
                    }

                    Text(
                        text = "Layer 20 Omnichroma {Ω} — 6 Bespoke Sacred Celestial Geometries",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioGold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        filteredSacredPips.forEach { sacred ->
                            AssistChip(
                                onClick = { viewModel.appendReviewPip(sacred.code) },
                                leadingIcon = { SacredCelestialPipIcon(shape = sacred.shape) },
                                label = { Text("${sacred.code} ${sacred.geometryName}") },
                                modifier = Modifier.testTag("sacred_pip_${sacred.shape.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        }

        // ---- Auto-Generated Scripts (Lua / Forge SVar / Hybrid / Safe Descriptor) ----
        uiState.reviewAuditResult?.let { audit ->
            item {
                val tabs = listOf("Lua 21", "Forge SVar", "Hybrid", "Safe Descriptor")
                val activeOutput = when (scriptTab) {
                    0 -> audit.generatedLuaScript
                    1 -> audit.generatedForgeSvarScript
                    2 -> audit.generatedHybridScript
                    else -> audit.sanitizedDescriptor
                }
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
                            text = "Auto-Generated Engine Scripts & Sanitized Descriptor",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tabs.forEachIndexed { idx, label ->
                                FilterChip(
                                    selected = scriptTab == idx,
                                    onClick = { scriptTab = idx },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = activeOutput,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = { onCopyText(tabs[scriptTab], activeOutput) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy ${tabs[scriptTab]}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiamondOptionBPipIcon(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.44f
        val path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }
        drawPath(path = path, color = color)
        drawPath(path = path, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.4f))
    }
}

@Composable
private fun SacredCelestialPipIcon(shape: SacredCelestialShape) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.45f
        val stroke = Stroke(width = 1.5f)
        val tint = StudioGold

        when (shape) {
            SacredCelestialShape.HEXAGRAM_STAR -> {
                drawPolygon(cx, cy, r, 3, -90.0, tint, stroke)
                drawPolygon(cx, cy, r, 3, 90.0, tint, stroke)
            }
            SacredCelestialShape.CELESTIAL_OCTAGRAM -> {
                drawPolygon(cx, cy, r, 4, -90.0, tint, stroke)
                drawPolygon(cx, cy, r, 4, -45.0, tint, stroke)
            }
            SacredCelestialShape.SACRED_HALO_RING -> {
                drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = stroke)
                drawCircle(color = tint, radius = r * 0.52f, center = Offset(cx, cy), style = stroke)
            }
            SacredCelestialShape.ASTRAL_MERKABA -> {
                drawPolygon(cx, cy, r, 3, -90.0, tint, stroke)
                drawPolygon(cx, cy, r * 0.72f, 3, 90.0, tint, stroke)
                drawCircle(color = tint, radius = r * 0.22f, center = Offset(cx, cy))
            }
            SacredCelestialShape.PRIMORDIAL_SUNBURST -> {
                for (i in 0 until 8) {
                    val rad = (i * 45.0) * PI / 180.0
                    drawLine(
                        color = tint,
                        start = Offset(cx, cy),
                        end = Offset(cx + (cos(rad) * r).toFloat(), cy + (sin(rad) * r).toFloat()),
                        strokeWidth = 1.4f,
                    )
                }
            }
            SacredCelestialShape.SOLAR_ASTROLABE -> {
                drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = stroke)
                drawLine(color = tint, start = Offset(cx - r, cy), end = Offset(cx + r, cy), strokeWidth = 1.3f)
                drawLine(color = tint, start = Offset(cx, cy - r), end = Offset(cx, cy + r), strokeWidth = 1.3f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygon(
    cx: Float,
    cy: Float,
    radius: Float,
    sides: Int,
    startDeg: Double,
    color: Color,
    stroke: Stroke,
) {
    val path = Path()
    for (i in 0 until sides) {
        val deg = startDeg + i * (360.0 / sides)
        val rad = deg * PI / 180.0
        val x = cx + (cos(rad) * radius).toFloat()
        val y = cy + (sin(rad) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color, style = stroke)
}

private fun colorForPipCode(code: String): Color = when (code) {
    "{C}" -> Color(0xFF9EA7B8)
    "{W}" -> Color(0xFFF5F2E6)
    "{U}" -> Color(0xFF3B82F6)
    "{B}" -> Color(0xFF3A3447)
    "{R}" -> Color(0xFFE14C4C)
    "{G}" -> Color(0xFF2EA865)
    "{P}" -> Color(0xFF8E55D9)
    "{Y}" -> Color(0xFFECC94B)
    "{O}" -> Color(0xFFEA8434)
    "{Cy}" -> Color(0xFF22C7D6)
    "{Pk}" -> Color(0xFFEC6EA8)
    "{Br}" -> Color(0xFF8D5D3B)
    "{Te}" -> Color(0xFF1F9D8B)
    "{In}" -> Color(0xFF4F5BD5)
    "{Mg}" -> Color(0xFFD63384)
    "{Li}" -> Color(0xFF84CC16)
    "{Sl}" -> Color(0xFFCBD5E1)
    "{Gd}" -> Color(0xFFD4A84B)
    "{Cr}" -> Color(0xFFB91C1C)
    "{Ob}" -> Color(0xFF1D202C)
    "{Aq}" -> Color(0xFF38BDF8)
    else -> StudioGold
}
