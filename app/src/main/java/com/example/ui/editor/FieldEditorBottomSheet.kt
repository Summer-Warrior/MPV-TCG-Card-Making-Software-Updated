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

private data class MseFormattingToken(
    val label: String,
    val insertText: String,
    val wrapStart: String? = null,
    val wrapEnd: String? = null,
)

private val MSE_TOKENS = listOf(
    MseFormattingToken("<b>Bold</b>", "", "<b>", "</b>"),
    MseFormattingToken("<i>Italic</i>", "", "<i>", "</i>"),
    MseFormattingToken("<sym>", "", "<sym>", "</sym>"),
    MseFormattingToken("<kw-a>", "", "<kw-a>", "</kw-a>"),
    MseFormattingToken("{T}", "<sym>T</sym>"),
    MseFormattingToken("{W}", "<sym>W</sym>"),
    MseFormattingToken("{U}", "<sym>U</sym>"),
    MseFormattingToken("{B}", "<sym>B</sym>"),
    MseFormattingToken("{R}", "<sym>R</sym>"),
    MseFormattingToken("{G}", "<sym>G</sym>"),
    MseFormattingToken("{C}", "<sym>C</sym>"),
    MseFormattingToken("{1}", "<sym>1</sym>"),
    MseFormattingToken("—", " — "),
    MseFormattingToken("•", "• "),
)

/**
 * Material 3 ModalBottomSheet editor triggered when tapping any text hitbox on the 1:1 card.
 * Includes an MSE2 formatting & symbol bar and writes updates losslessly into the `.mse-set`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditorBottomSheet(
    fieldKey: String,
    initialValue: String,
    hitboxSummary: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textValue by remember(fieldKey, initialValue) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length),
            )
        )
    }

    val isMultilineField = fieldKey in setOf("rule text", "flavor text", "notes", "text")

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit MSE Field: $fieldKey",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = hitboxSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("field_editor_close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close editor")
                }
            }

            // MSE Formatting & Symbol Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MSE_TOKENS.forEach { token ->
                    AssistChip(
                        onClick = {
                            textValue = applyMseToken(textValue, token)
                        },
                        label = {
                            Text(
                                text = token.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        modifier = Modifier.testTag("mse_token_${token.label}"),
                    )
                }
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text(fieldKey) },
                placeholder = { Text("Enter $fieldKey value (MSE tags preserved losslessly)") },
                minLines = if (isMultilineField) 4 else 1,
                maxLines = if (isMultilineField) 8 else 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_editor_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("field_editor_cancel"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onSave(textValue.text) },
                    modifier = Modifier.testTag("field_editor_save"),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
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
