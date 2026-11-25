package com.example.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.mse.MseSetArchive
import com.example.domain.comparison.SetComparisonEngine
import com.example.domain.frameworks.UniversalCardFramework

/**
 * Interactive Style Field Mapper Dialog for Fan-Fiction Split-Off Sets and Cross-Game Style Binding.
 *
 * Non-destructively maps source card fields (e.g. `atk`, `def`, `level`) to target framework/style
 * field keys (e.g. `power`, `toughness`, `casting cost`) while keeping `CANON_ORIGINAL` cards
 * protected and strictly never modifying `"image"`.
 */
@Composable
fun StyleFieldMapperDialog(
    archive: MseSetArchive,
    frameworks: List<UniversalCardFramework>,
    initialTargetFramework: UniversalCardFramework?,
    onDismiss: () -> Unit,
    onApplyMapping: (
        fieldMapping: Map<String, String>,
        targetFramework: UniversalCardFramework?,
    ) -> Unit,
) {
    val sourceFieldKeys = remember(archive) {
        archive.document.cards
            .flatMap { it.allEditableFields.keys }
            .filter {
                !it.equals("image", ignoreCase = true) &&
                    !it.equals("has styling", ignoreCase = true) &&
                    !it.equals("time created", ignoreCase = true) &&
                    !it.equals("time modified", ignoreCase = true) &&
                    !it.equals(SetComparisonEngine.MSE_FIELD_CARD_ORIGIN, ignoreCase = true) &&
                    !it.equals(SetComparisonEngine.MSE_FIELD_CANON_SOURCE_ID, ignoreCase = true) &&
                    !it.equals(SetComparisonEngine.MSE_FIELD_LINKED_CANON_ID, ignoreCase = true)
            }
            .distinct()
            .sorted()
    }

    var targetFramework by remember(initialTargetFramework, frameworks) {
        mutableStateOf(initialTargetFramework ?: frameworks.firstOrNull())
    }

    val targetFieldKeys = remember(targetFramework) {
        targetFramework?.resolvedFieldSpecs()?.map { it.key }
            ?: listOf("name", "casting cost", "super type", "sub type", "rule text", "power", "toughness", "flavor text")
    }

    var mapping by remember(sourceFieldKeys, targetFieldKeys) {
        mutableStateOf(
            SetComparisonEngine.suggestDynamicFieldMapping(sourceFieldKeys.toSet(), targetFieldKeys)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Style Field Mapper") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Map source card fields to a target TCG framework style without modifying Canon Original cards or artwork.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Target Framework", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    frameworks.take(18).forEach { fw ->
                        FilterChip(
                            selected = targetFramework?.id == fw.id,
                            onClick = {
                                targetFramework = fw
                                val keys = fw.resolvedFieldSpecs().map { it.key }
                                mapping = SetComparisonEngine.suggestDynamicFieldMapping(sourceFieldKeys.toSet(), keys)
                            },
                            label = { Text(fw.effectiveShortName, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                if (sourceFieldKeys.isEmpty()) {
                    Text(
                        text = "No custom source fields detected in the current set yet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = "Tap any target pill to cycle its mapped target key:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sourceFieldKeys.forEach { src ->
                        val currentDst = mapping[src]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = src,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (currentDst != null) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clickable {
                                        val options = listOf<String?>(null) + targetFieldKeys
                                        val nextIdx = (options.indexOf(currentDst) + 1) % options.size
                                        val nextVal = options[nextIdx]
                                        mapping = if (nextVal == null) {
                                            mapping - src
                                        } else {
                                            mapping + (src to nextVal)
                                        }
                                    }
                                    .testTag("field_mapper_row_$src"),
                            ) {
                                Text(
                                    text = "→ ${currentDst ?: "Keep as-is"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyMapping(mapping, targetFramework) },
                modifier = Modifier.testTag("field_mapper_apply_button"),
            ) {
                Text("Apply Mapping")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
