package com.example.ui.coder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.nexus.CardCodeValidator
import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.TwentyColorPieCatalog
import com.example.ui.nexus.NexusHubUiState
import com.example.ui.nexus.NexusHubViewModel
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber
import java.math.BigInteger

/**
 * DataEditorX-styled structured Card Inspector Pane.
 *
 * Compact dark-IDE property grid:
 * - Card Code & 21-Digit BigInteger Register (+1, +10^12, UUID)
 * - Card Name, Type/Category, Pack Name, Effect/Rules monospace editor
 * - Strict Match Gameplay vs Dossier Time-Travel Rule selector
 * - Canonical 20-Color Pie Pip Picker (ZERO default colors selected)
 * - Temporal / Flavor Filter checkboxes
 * - Coding Variables key-value table
 * - Modular Ultimate TCG Manual fields
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataEditorXInspectorPane(
    uiState: NexusHubUiState,
    viewModel: NexusHubViewModel,
    onStagedOpenScript: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var varKeyInput by remember { mutableStateOf("") }
    var varValInput by remember { mutableStateOf("") }
    var showColorPieWheel by remember { mutableStateOf(false) }
    var showUltimateManual by remember { mutableStateOf(false) }

    val selectedClient = uiState.selectedClient
    val report = uiState.validationReport

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- Section 1: Identity & 21-Digit BigInteger Register ----
        DexInspectorGroupCard(title = "CARD IDENTITY & 21-DIGIT REGISTER") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.cardId,
                    onValueChange = { viewModel.updateCardFields(cardId = it) },
                    label = { Text("Code / ID") },
                    placeholder = { Text("21-digit or UUID") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dex_inspector_card_id"),
                )
                OutlinedTextField(
                    value = uiState.packName,
                    onValueChange = { viewModel.updateCardFields(packName = it) },
                    label = { Text("Pack / DB") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dex_inspector_pack_name"),
                )
            }

            OutlinedTextField(
                value = uiState.register21Digit,
                onValueChange = { viewModel.updateCardFields(register21Digit = it) },
                label = { Text("21-Digit BigInteger Register (Optional)") },
                placeholder = { Text("100000000000000000001") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dex_inspector_register_21"),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.incrementRegister21(BigInteger.ONE) },
                    modifier = Modifier.testTag("dex_reg_plus_1"),
                ) {
                    Text("+1", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { viewModel.incrementRegister21(BigInteger("1000000000000")) },
                    modifier = Modifier.testTag("dex_reg_plus_trillion"),
                ) {
                    Text("+10^12", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::assignUuidCardId,
                    modifier = Modifier.testTag("dex_assign_uuid"),
                ) {
                    Text("UUID", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::startNewBlankCodedCard,
                    modifier = Modifier.testTag("dex_clear_editor"),
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ---- Section 2: Card Fields & Rules Text ----
        DexInspectorGroupCard(title = "CARD ATTRIBUTES & EFFECT SCRIPT TEXT") {
            OutlinedTextField(
                value = uiState.cardName,
                onValueChange = { viewModel.updateCardFields(cardName = it) },
                label = { Text("Card Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dex_inspector_name"),
            )

            OutlinedTextField(
                value = uiState.cardType,
                onValueChange = { viewModel.updateCardFields(cardType = it) },
                label = { Text("Type / Race / Attribute / Stats Line") },
                placeholder = { Text("e.g. Monster • Spellcaster / ATK 2500 / DEF 2100") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dex_inspector_type"),
            )

            OutlinedTextField(
                value = uiState.rulesText,
                onValueChange = { viewModel.updateCardFields(rulesText = it) },
                label = { Text("Effect / Rules Text (Art stays strictly null)") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dex_inspector_rules"),
            )
        }

        // ---- Section 3: Strict Time-Travel Client Rule Mode ----
        DexInspectorGroupCard(title = "CLIENT RULE SCOPE (TIME-TRAVEL GUARD)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.editorMode == CardEditorMode.MATCH_GAMEPLAY,
                    onClick = { viewModel.setEditorMode(CardEditorMode.MATCH_GAMEPLAY) },
                    label = { Text("Match Gameplay", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dex_mode_match"),
                )
                FilterChip(
                    selected = uiState.editorMode == CardEditorMode.AVATAR_PROFILE_MEMORIAL_DOSSIER,
                    onClick = { viewModel.setEditorMode(CardEditorMode.AVATAR_PROFILE_MEMORIAL_DOSSIER) },
                    label = { Text("Dossier / Profile", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dex_mode_dossier"),
                )
            }

            Text(
                text = if (uiState.editorMode == CardEditorMode.MATCH_GAMEPLAY) {
                    "allowTimeTravelFlagsOnMatchCards = false • Filters 19–21 blocked on Match cards."
                } else {
                    "Dossier mode active • Filters 19–21 (Past/Present/Future) unlocked for Profile records."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(19 to "F19 Past", 20 to "F20 Present", 21 to "F21 Future").forEach { (id, label) ->
                    val blocked = uiState.editorMode == CardEditorMode.MATCH_GAMEPLAY
                    FilterChip(
                        selected = id in uiState.selectedFlavorFilters,
                        onClick = { viewModel.toggleFlavorFilter(id) },
                        enabled = !blocked,
                        label = {
                            Text(
                                text = if (blocked) "$label (Locked)" else label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.testTag("dex_filter_$id"),
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardCodeValidator.ALLOWED_MATCH_TEMPORAL_EFFECTS.forEach { effect ->
                    FilterChip(
                        selected = effect in uiState.selectedTemporalEffects,
                        onClick = { viewModel.toggleTemporalEffect(effect) },
                        label = { Text(effect, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("dex_temporal_$effect"),
                    )
                }
            }
        }

        // ---- Section 4: 20-Color Pie Pip Grammar (Strictly ZERO Defaults) ----
        DexInspectorGroupCard(title = "20-COLOR PIE PIP GRAMMAR (ZERO DEFAULTS)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (uiState.selectedColorPiePips.isEmpty()) {
                        "Selected: none (0 pips)"
                    } else {
                        "Pips (${uiState.selectedColorPiePips.size}): ${uiState.selectedColorPiePips.joinToString("")}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.selectedColorPiePips.isNotEmpty()) {
                    OutlinedButton(
                        onClick = viewModel::clearAllColorPiePips,
                        modifier = Modifier.testTag("dex_clear_pips"),
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (uiState.selectedColorPiePips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.selectedColorPiePips.forEachIndexed { idx, pip ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { viewModel.removeColorPiePipAt(idx) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = pip,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $pip",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                // Value state modifiers on last pip (:T Tint, :S Shade, :M Muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (listOf("" to "Normal") + TwentyColorPieCatalog.valueStates).forEach { (suffix, label) ->
                        OutlinedButton(
                            onClick = { viewModel.applyValueStateToLastPip(suffix) },
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showColorPieWheel = !showColorPieWheel },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dex_toggle_color_pie"),
            ) {
                Text(if (showColorPieWheel) "Hide 20-Color Pie Picker" else "Open 20-Color Pie + {C} + Layer 20 {Ω} Picker")
            }

            if (showColorPieWheel) {
                Text(
                    text = "Core 20-Color Wheel + Colorless {C}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (TwentyColorPieCatalog.baseTwentyColors + TwentyColorPieCatalog.colorlessPip).forEach { pip ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.appendColorPiePip(pip.code) },
                            label = {
                                Text(
                                    text = "${pip.code} ${pip.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
                Text(
                    text = "Layer 20 Omega States",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TwentyColorPieCatalog.layer20OmegaPips.forEach { omega ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.appendColorPiePip(omega.code) },
                            label = {
                                Text(
                                    text = "${omega.code} ${omega.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        }

        // ---- Section 5: DataEditorX Coding Variables Table ----
        DexInspectorGroupCard(title = "CODING VARIABLES (<=21-DIGIT SAFE)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = varKeyInput,
                    onValueChange = { varKeyInput = it },
                    label = { Text("Key") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = varValInput,
                    onValueChange = { varValInput = it },
                    label = { Text("Value") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        viewModel.putCodingVariable(varKeyInput, varValInput)
                        varKeyInput = ""
                        varValInput = ""
                    },
                    modifier = Modifier.testTag("dex_add_coding_var"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add variable")
                }
            }

            if (uiState.codingVariables.isNotEmpty()) {
                uiState.codingVariables.forEach { (k, v) ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$k = $v",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.removeCodingVariable(k) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove $k",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Section 6: Modular Ultimate TCG Manual (Collapsible) ----
        DexInspectorGroupCard(title = "ULTIMATE TCG MODULAR MANUAL") {
            OutlinedButton(
                onClick = { showUltimateManual = !showUltimateManual },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showUltimateManual) "Hide Modular Fields" else "Edit Zone / Resource / Win-Condition Notes")
            }
            if (showUltimateManual) {
                val m = uiState.ultimateTcgManual
                OutlinedTextField(
                    value = m.batch2ResourceNote,
                    onValueChange = { viewModel.updateUltimateTcgManual(m.copy(batch2ResourceNote = it)) },
                    label = { Text("Batch 2 Resource Note") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = m.batch3ResourceNote,
                    onValueChange = { viewModel.updateUltimateTcgManual(m.copy(batch3ResourceNote = it)) },
                    label = { Text("Batch 3 Resource Note") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = m.subDeckDragNote,
                    onValueChange = { viewModel.updateUltimateTcgManual(m.copy(subDeckDragNote = it)) },
                    label = { Text("Sub-Deck Drag Note") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = m.manaNumberSplitNote,
                    onValueChange = { viewModel.updateUltimateTcgManual(m.copy(manaNumberSplitNote = it)) },
                    label = { Text("Mana Number Split Note") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Inline Validation Summary + Stage CTA ----
        if (report != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (report.isValid) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (report.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (report.isValid) StatusConnected else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (report.isValid) {
                                "Guardrails passed for ${selectedClient?.name ?: "selected client"}"
                            } else {
                                "${report.blockingErrors.size} blocking error(s)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    report.issues.take(4).forEach { issue ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (issue.isBlocking) Icons.Default.Error else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (issue.isBlocking) MaterialTheme.colorScheme.error else StatusOfflineAmber,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "[${issue.code}] ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    viewModel.stageCurrentCardIntoPack()
                },
                enabled = report?.isValid == true && selectedClient?.upstreamSourceLock == false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dex_stage_card_button"),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stage to Card DB")
            }

            OutlinedButton(
                onClick = onStagedOpenScript,
                modifier = Modifier.testTag("dex_inspect_lua_button"),
            ) {
                Text("View Lua")
            }
        }
    }
}

@Composable
internal fun DexInspectorGroupCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}
