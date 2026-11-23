package com.example.ui.nexus

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Input
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.domain.nexus.CardCodeValidator
import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.TwentyColorPieCatalog
import com.example.ui.theme.StatusConnected
import java.math.BigInteger

/**
 * Interactive Card Coder, 21-Digit BigInteger Register Builder, Canonical 20-Color Pie Pip Picker
 * (strictly ZERO default colors selected), Notebook Time-Travel Guardrails, and Lua/JSON Exporter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NexusCardCoderSection(
    uiState: NexusHubUiState,
    activeMseCardName: String?,
    activeMseCardType: String?,
    activeMseCardRules: String?,
    viewModel: NexusHubViewModel,
    onCopyPackJson: () -> Unit,
    onDownloadPackJson: () -> Unit,
    onDownloadLua: () -> Unit,
    onCopyLua: () -> Unit,
    onCommitPackToHub: () -> Unit,
) {
    var varKeyInput by remember { mutableStateOf("") }
    var varValInput by remember { mutableStateOf("") }
    var pairFirst by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Active Non-Locked Target & Mode Switcher ----
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
                        text = "Target Client: ${uiState.selectedClient?.name ?: "None"} (Non-Locked)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!activeMseCardName.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.prefillFromActiveMseCard(
                                    name = activeMseCardName,
                                    type = activeMseCardType.orEmpty(),
                                    rulesText = activeMseCardRules.orEmpty(),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nexus_import_active_mse_card_button"),
                        ) {
                            Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import Active Set Card: $activeMseCardName")
                        }
                    }

                    Text(
                        text = "Editor Mode & Time-Travel Client Rule",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CardEditorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.editorMode == mode,
                                onClick = { viewModel.setEditorMode(mode) },
                                label = { Text(mode.displayLabel) },
                                modifier = Modifier.testTag("nexus_mode_${mode.name.lowercase()}"),
                            )
                        }
                    }
                    Text(
                        text = if (uiState.editorMode == CardEditorMode.MATCH_GAMEPLAY) {
                            "Match Gameplay Guardrail: allowTimeTravelFlagsOnMatchCards = false. Filters 19–21 blocked; ONLY 'End Turn' and 'Extra Turn' temporal effects allowed."
                        } else {
                            "Avatar Profile / Memorial Forge Dossier Mode: Filters 19–21 unlocked for dossier entries."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Arbitrary-Length Card ID & 21-Digit BigInteger Register ----
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
                        text = "Card Identity & 21-Digit BigInteger Register",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = uiState.cardId,
                        onValueChange = { viewModel.updateCardFields(cardId = it) },
                        label = { Text("Card ID (8-digit, 21-digit, UUID, or String)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nexus_card_id_input"),
                    )
                    OutlinedTextField(
                        value = uiState.register21Digit,
                        onValueChange = { viewModel.updateCardFields(register21Digit = it) },
                        label = { Text("21-Digit Register (^[0-9]{1,21}$)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nexus_register21_input"),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = { viewModel.incrementRegister21(BigInteger.ONE) },
                            label = { Text("BigInt +1") },
                            modifier = Modifier.testTag("nexus_bigint_plus1"),
                        )
                        AssistChip(
                            onClick = { viewModel.incrementRegister21(BigInteger("1000000000000")) },
                            label = { Text("BigInt +10^12") },
                        )
                        AssistChip(
                            onClick = viewModel::assignUuidCardId,
                            label = { Text("Generate UUID") },
                        )
                    }
                    OutlinedTextField(
                        value = uiState.cardName,
                        onValueChange = { viewModel.updateCardFields(cardName = it) },
                        label = { Text("Card Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nexus_card_name_input"),
                    )
                    OutlinedTextField(
                        value = uiState.cardType,
                        onValueChange = { viewModel.updateCardFields(cardType = it) },
                        label = { Text("Card Type / Attributes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.rulesText,
                        onValueChange = { viewModel.updateCardFields(rulesText = it) },
                        label = { Text("Rules Text") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Temporal effects & Filters 19-21
                    Text(
                        text = "Allowed Match Temporal Effects",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardCodeValidator.ALLOWED_MATCH_TEMPORAL_EFFECTS.forEach { effect ->
                            FilterChip(
                                selected = effect in uiState.selectedTemporalEffects,
                                onClick = { viewModel.toggleTemporalEffect(effect) },
                                label = { Text(effect) },
                            )
                        }
                    }

                    Text(
                        text = "Flavor Filters 19–21 (Blocked on Match Cards)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(19, 20, 21).forEach { filterId ->
                            FilterChip(
                                selected = filterId in uiState.selectedFlavorFilters,
                                onClick = { viewModel.toggleFlavorFilter(filterId) },
                                enabled = uiState.editorMode == CardEditorMode.AVATAR_PROFILE_MEMORIAL_DOSSIER,
                                label = { Text("Filter $filterId") },
                                modifier = Modifier.testTag("nexus_filter_$filterId"),
                            )
                        }
                    }
                }
            }
        }

        // ---- Canonical 20-Color Pie Pip Picker (ZERO Default Colors Selected) ----
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
                            text = "20-Color Pie Pip Picker",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (uiState.selectedColorPiePips.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearAllColorPiePips) {
                                Text("Clear All")
                            }
                        }
                    }

                    if (uiState.selectedColorPiePips.isEmpty()) {
                        Text(
                            text = "No colors selected (loads empty by default). Tap any canonical pip below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            uiState.selectedColorPiePips.forEachIndexed { idx, pip ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removeColorPiePipAt(idx) },
                                    label = { Text(pip) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $pip",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        text = "Base {C} & 20 Canonical Colors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AssistChip(
                            onClick = { viewModel.appendColorPiePip(TwentyColorPieCatalog.colorlessPip.code) },
                            label = { Text(TwentyColorPieCatalog.colorlessPip.code) },
                        )
                        TwentyColorPieCatalog.baseTwentyColors.forEach { spec ->
                            AssistChip(
                                onClick = {
                                    val pendingFirst = pairFirst
                                    if (pendingFirst == null) {
                                        viewModel.appendColorPiePip(spec.code)
                                    } else {
                                        viewModel.appendColorPiePip(
                                            TwentyColorPieCatalog.buildFusedPair(pendingFirst, spec.code)
                                        )
                                        pairFirst = null
                                    }
                                },
                                label = { Text("${spec.code} ${spec.label}") },
                            )
                        }
                    }

                    Text(
                        text = "Value States (:T / :S / :M), Layer 2 Fused/Split & Layer 20 Omega",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TwentyColorPieCatalog.valueStates.forEach { (suffix, label) ->
                            AssistChip(
                                onClick = { viewModel.applyValueStateToLastPip(suffix) },
                                label = { Text(label) },
                            )
                        }
                        AssistChip(
                            onClick = {
                                val last = uiState.selectedColorPiePips.lastOrNull()
                                if (last != null) pairFirst = last
                            },
                            label = {
                                Text(if (pairFirst != null) "Tap 2nd Color to Fuse" else "Fuse Next Pair (L2)")
                            },
                        )
                        AssistChip(
                            onClick = {
                                if (uiState.selectedColorPiePips.size >= 2) {
                                    val a = uiState.selectedColorPiePips[uiState.selectedColorPiePips.size - 2]
                                    val b = uiState.selectedColorPiePips.last()
                                    viewModel.appendColorPiePip(TwentyColorPieCatalog.buildChoiceSplit(a, b))
                                }
                            },
                            label = { Text("Choice Split (A/B)") },
                        )
                        AssistChip(
                            onClick = {
                                if (uiState.selectedColorPiePips.size >= 2) {
                                    val a = uiState.selectedColorPiePips[uiState.selectedColorPiePips.size - 2]
                                    val b = uiState.selectedColorPiePips.last()
                                    viewModel.appendColorPiePip(TwentyColorPieCatalog.buildWeightedLead(a, 2, b))
                                }
                            },
                            label = { Text("Weighted Lead (2A/B)") },
                        )
                        TwentyColorPieCatalog.layer20OmegaPips.forEach { omega ->
                            AssistChip(
                                onClick = { viewModel.appendColorPiePip(omega.code) },
                                label = { Text(omega.code) },
                            )
                        }
                    }
                }
            }
        }

        // ---- 21-Digit Coding Variables & Ultimate TCG Modular Manual Fields ----
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
                        text = "Coding Variables (21-Digit Max Boundary) & Ultimate TCG Manual Notes",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = varKeyInput,
                            onValueChange = { varKeyInput = it },
                            label = { Text("Variable Key") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = varValInput,
                            onValueChange = { varValInput = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                viewModel.putCodingVariable(varKeyInput, varValInput)
                                varKeyInput = ""
                                varValInput = ""
                            },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add variable", modifier = Modifier.size(16.dp))
                        }
                    }
                    uiState.codingVariables.forEach { (k, v) ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeCodingVariable(k) },
                            label = { Text("$k = $v") },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove $k", modifier = Modifier.size(14.dp))
                            },
                        )
                    }

                    val manual = uiState.ultimateTcgManual
                    OutlinedTextField(
                        value = manual.batch2ResourceNote,
                        onValueChange = { viewModel.updateUltimateTcgManual(manual.copy(batch2ResourceNote = it)) },
                        label = { Text("Batch 2 Resource (Modular Manual Note)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = manual.batch3ResourceNote,
                        onValueChange = { viewModel.updateUltimateTcgManual(manual.copy(batch3ResourceNote = it)) },
                        label = { Text("Batch 3 Resource (Modular Manual Note)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = manual.subDeckDragNote,
                        onValueChange = { viewModel.updateUltimateTcgManual(manual.copy(subDeckDragNote = it)) },
                        label = { Text("Sub-Deck Dragging / Mana Number Split (Manual Note)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ---- Live Validation Report & Generated Script / Export ----
        item {
            val report = uiState.validationReport
            val isValid = report?.isValid == true
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (isValid) StatusConnected else MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = "Validation Status",
                            tint = if (isValid) StatusConnected else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = if (isValid) {
                                "Card Passed All Guardrails (${report?.cardIdKind?.name})"
                            } else {
                                "Validation Issues (${report?.blockingErrors?.size ?: 0})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }

                    report?.issues?.forEach { issue ->
                        Text(
                            text = "• [${issue.code}] ${issue.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    OutlinedTextField(
                        value = uiState.packName,
                        onValueChange = { viewModel.updateCardFields(packName = it) },
                        label = { Text("Export Pack Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::stageCurrentCardIntoPack,
                            enabled = isValid,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nexus_stage_card_button"),
                        ) {
                            Text("Stage Card (${uiState.savedCodedCards.size})")
                        }
                        Button(
                            onClick = onCommitPackToHub,
                            enabled = isValid && uiState.isMutualLinkVerified && !uiState.isCommitting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Push Pack")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(onClick = onCopyPackJson, enabled = isValid) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Pack JSON")
                        }
                        OutlinedButton(onClick = onDownloadPackJson, enabled = isValid) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Pack JSON")
                        }
                        OutlinedButton(onClick = onDownloadLua, enabled = isValid) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save c<21>.lua")
                        }
                        TextButton(onClick = onCopyLua, enabled = isValid) {
                            Text("Copy Lua")
                        }
                    }

                    uiState.generatedLuaPreview?.let { lua ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = lua,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
