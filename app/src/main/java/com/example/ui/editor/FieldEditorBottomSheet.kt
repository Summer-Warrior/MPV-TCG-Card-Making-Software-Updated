package com.example.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.R

sealed class CardFieldEditTarget {
    data class SingleField(val fieldKey: String, val initialValue: String) : CardFieldEditTarget()
    data class TypeLine(val superType: String, val subType: String) : CardFieldEditTarget()
    data class PowerToughness(val power: String, val toughness: String) : CardFieldEditTarget()
}

private data class MseFormattingToken(
    val label: String,
    val insertText: String,
    val wrapStart: String? = null,
    val wrapEnd: String? = null,
)

private val TEXT_MSE_TOKENS = listOf(
    MseFormattingToken("{T}", "<sym>T</sym>"),
    MseFormattingToken("{W}", "<sym>W</sym>"),
    MseFormattingToken("{U}", "<sym>U</sym>"),
    MseFormattingToken("{B}", "<sym>B</sym>"),
    MseFormattingToken("{R}", "<sym>R</sym>"),
    MseFormattingToken("{G}", "<sym>G</sym>"),
    MseFormattingToken("{C}", "<sym>C</sym>"),
    MseFormattingToken("{1}", "<sym>1</sym>"),
    MseFormattingToken("<i>", "", "<i>", "</i>"),
    MseFormattingToken("<b>", "", "<b>", "</b>"),
    MseFormattingToken("—", " — "),
    MseFormattingToken("•", "• "),
)

private val COST_MSE_TOKENS = listOf(
    MseFormattingToken("{1}", "1"),
    MseFormattingToken("{2}", "2"),
    MseFormattingToken("{X}", "X"),
    MseFormattingToken("{W}", "W"),
    MseFormattingToken("{U}", "U"),
    MseFormattingToken("{B}", "B"),
    MseFormattingToken("{R}", "R"),
    MseFormattingToken("{G}", "G"),
    MseFormattingToken("{C}", "C"),
)

/**
 * Clean Material 3 ModalBottomSheet editor for card text, cost, type line, rules/flavor, or P/T.
 * Uses clean human-readable labels and MSE symbol helper chips, writing losslessly to `.mse-set`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditorBottomSheet(
    target: CardFieldEditTarget,
    onDismiss: () -> Unit,
    onSaveFields: (Map<String, String?>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (target) {
                is CardFieldEditTarget.SingleField -> {
                    SingleFieldEditorBody(
                        fieldKey = target.fieldKey,
                        initialValue = target.initialValue,
                        onDismiss = onDismiss,
                        onSave = { value -> onSaveFields(mapOf(target.fieldKey to value)) },
                    )
                }

                is CardFieldEditTarget.TypeLine -> {
                    TypeLineEditorBody(
                        initialSuperType = target.superType,
                        initialSubType = target.subType,
                        onDismiss = onDismiss,
                        onSave = { superT, subT ->
                            onSaveFields(mapOf("super type" to superT, "sub type" to subT))
                        },
                    )
                }

                is CardFieldEditTarget.PowerToughness -> {
                    PowerToughnessEditorBody(
                        initialPower = target.power,
                        initialToughness = target.toughness,
                        onDismiss = onDismiss,
                        onSave = { p, t ->
                            onSaveFields(mapOf("power" to p, "toughness" to t))
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SingleFieldEditorBody(
    fieldKey: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var textValue by remember(fieldKey, initialValue) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length),
            )
        )
    }

    val title = humanFieldTitle(fieldKey)
    val isMultiline = fieldKey in setOf("rule text", "flavor text", "notes", "text")
    val tokens = when (fieldKey) {
        "casting cost" -> COST_MSE_TOKENS
        "rule text", "flavor text", "text" -> TEXT_MSE_TOKENS
        else -> emptyList()
    }

    SheetHeaderRow(title = title, onDismiss = onDismiss)

    if (tokens.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tokens.forEach { token ->
                AssistChip(
                    onClick = { textValue = applyMseToken(textValue, token) },
                    label = { Text(token.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("mse_token_${token.label}"),
                )
            }
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { textValue = it },
        label = { Text(title) },
        minLines = if (isMultiline) 4 else 1,
        maxLines = if (isMultiline) 8 else 2,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_editor_input"),
    )

    SheetActionButtons(
        onDismiss = onDismiss,
        onSave = { onSave(textValue.text) },
    )
}

@Composable
private fun TypeLineEditorBody(
    initialSuperType: String,
    initialSubType: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var superType by remember(initialSuperType) { mutableStateOf(initialSuperType) }
    var subType by remember(initialSubType) { mutableStateOf(initialSubType) }

    SheetHeaderRow(title = "Type Line", onDismiss = onDismiss)

    OutlinedTextField(
        value = superType,
        onValueChange = { superType = it },
        label = { Text("Card Type / Supertype") },
        placeholder = { Text("e.g., Legendary Creature") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_editor_supertype"),
    )

    OutlinedTextField(
        value = subType,
        onValueChange = { subType = it },
        label = { Text("Subtype") },
        placeholder = { Text("e.g., Human Artificer") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_editor_subtype"),
    )

    SheetActionButtons(
        onDismiss = onDismiss,
        onSave = { onSave(superType.trim(), subType.trim()) },
    )
}

@Composable
private fun PowerToughnessEditorBody(
    initialPower: String,
    initialToughness: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var power by remember(initialPower) { mutableStateOf(initialPower) }
    var toughness by remember(initialToughness) { mutableStateOf(initialToughness) }

    SheetHeaderRow(title = "Power / Toughness", onDismiss = onDismiss)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = power,
            onValueChange = { power = it },
            label = { Text("Power") },
            placeholder = { Text("0") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("field_editor_power"),
        )
        OutlinedTextField(
            value = toughness,
            onValueChange = { toughness = it },
            label = { Text("Toughness") },
            placeholder = { Text("0") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("field_editor_toughness"),
        )
    }

    SheetActionButtons(
        onDismiss = onDismiss,
        onSave = { onSave(power.trim(), toughness.trim()) },
    )
}

@Composable
private fun SheetHeaderRow(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("field_editor_close")) {
            Icon(Icons.Default.Close, contentDescription = "Close editor")
        }
    }
}

@Composable
private fun SheetActionButtons(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("field_editor_cancel"),
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            onClick = onSave,
            modifier = Modifier.testTag("field_editor_save"),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.save))
        }
    }
}

private fun humanFieldTitle(fieldKey: String): String = when (fieldKey.lowercase()) {
    "name" -> "Card Name"
    "casting cost" -> "Mana / Cost"
    "super type" -> "Card Type"
    "sub type" -> "Subtype"
    "rule text" -> "Rules Text"
    "flavor text" -> "Flavor Text"
    "power" -> "Power"
    "toughness" -> "Toughness"
    "illustrator" -> "Illustrator"
    "notes" -> "Card Notes"
    else -> fieldKey.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}

private fun applyMseToken(current: TextFieldValue, token: MseFormattingToken): TextFieldValue {
    val start = current.selection.min.coerceIn(0, current.text.length)
    val end = current.selection.max.coerceIn(0, current.text.length)
    val selected = current.text.substring(start, end)

    return if (token.wrapStart != null && token.wrapEnd != null) {
        val inserted = "${token.wrapStart}$selected${token.wrapEnd}"
        val newText = current.text.replaceRange(start, end, inserted)
        val cursorPos = if (selected.isEmpty()) start + token.wrapStart.length else start + inserted.length
        TextFieldValue(text = newText, selection = TextRange(cursorPos))
    } else {
        val newText = current.text.replaceRange(start, end, token.insertText)
        val cursorPos = start + token.insertText.length
        TextFieldValue(text = newText, selection = TextRange(cursorPos))
    }
}
