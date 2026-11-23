package com.example.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.data.mse.MseKeywordView

/**
 * Classic MSE `Keywords` Tab.
 * Allows adding, editing, and deleting custom set keywords and reminder text directly in the `.mse-set` file.
 */
@Composable
fun KeywordsTabContent(
    keywords: List<MseKeywordView>,
    onAddKeyword: (keyword: String, match: String, reminder: String, rules: String) -> Unit,
    onUpdateKeyword: (index: Int, keyword: String, match: String, reminder: String, rules: String) -> Unit,
    onDeleteKeyword: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingKeyword by remember { mutableStateOf<MseKeywordView?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set Keywords (${keywords.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Custom keyword definitions stored in this .mse-set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("keywords_add_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (keywords.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No custom keywords defined in this set yet. Tap Add to define a keyword and its reminder text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(keywords, key = { "${it.index}_${it.keyword}" }) { kw ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingKeyword = kw }
                        .testTag("keyword_row_${kw.index}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = kw.keyword.ifBlank { "Unnamed Keyword" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (kw.match.isNotBlank() && kw.match != kw.keyword) {
                                Text(
                                    text = "Match: ${kw.match}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (kw.reminder.isNotBlank()) {
                                Text(
                                    text = "(${kw.reminder})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteKeyword(kw.index) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete keyword",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        KeywordEditorDialog(
            title = "Add Keyword",
            initialKeyword = "",
            initialMatch = "",
            initialReminder = "",
            initialRules = "",
            onDismiss = { showAddDialog = false },
            onSave = { kw, match, rem, rules ->
                onAddKeyword(kw, match, rem, rules)
                showAddDialog = false
            },
        )
    }

    editingKeyword?.let { existing ->
        KeywordEditorDialog(
            title = "Edit Keyword",
            initialKeyword = existing.keyword,
            initialMatch = existing.match,
            initialReminder = existing.reminder,
            initialRules = existing.rules,
            onDismiss = { editingKeyword = null },
            onSave = { kw, match, rem, rules ->
                onUpdateKeyword(existing.index, kw, match, rem, rules)
                editingKeyword = null
            },
        )
    }
}

@Composable
private fun KeywordEditorDialog(
    title: String,
    initialKeyword: String,
    initialMatch: String,
    initialReminder: String,
    initialRules: String,
    onDismiss: () -> Unit,
    onSave: (keyword: String, match: String, reminder: String, rules: String) -> Unit,
) {
    var keyword by remember { mutableStateOf(initialKeyword) }
    var match by remember { mutableStateOf(initialMatch) }
    var reminder by remember { mutableStateOf(initialReminder) }
    var rules by remember { mutableStateOf(initialRules) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Keyword Name") },
                    placeholder = { Text("e.g., Ward") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = match,
                    onValueChange = { match = it },
                    label = { Text("Match Pattern") },
                    placeholder = { Text("e.g., Ward <cost>") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("Reminder Text") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    label = { Text("Rules Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = keyword.isNotBlank(),
                onClick = { onSave(keyword, match, reminder, rules) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
