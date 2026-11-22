package com.example.ui.fonts

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.fonts.FontAvailability
import com.example.data.fonts.FontEntry
import com.example.data.fonts.FontFilter

/**
 * Reusable searchable Font Picker list with filter chips (`All`, `Ready ✅`, `Free ⬇️`, `Paid 🔒`),
 * dynamic missing-font alerting, and automatic routing to CommercialFontLicenseModal.
 */
@Composable
fun FontPickerList(
    fonts: List<FontEntry>,
    selectedFontFamily: String?,
    isBusy: Boolean,
    onApplyReadyFont: (FontEntry) -> Unit,
    onDownloadOpenFont: (FontEntry) -> Unit,
    onImportPurchasedFont: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(FontFilter.ALL) }
    var pendingCommercialFont by remember { mutableStateOf<FontEntry?>(null) }

    val missingTemplateFonts = remember(fonts) {
        fonts.filter { it.detectedMissingInTemplate && !it.availability.isReady }
    }

    val filteredFonts = remember(fonts, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase()
        fonts.filter { entry ->
            val matchesFilter = when (activeFilter) {
                FontFilter.ALL -> true
                FontFilter.READY -> entry.availability.isReady
                FontFilter.FREE -> entry.availability == FontAvailability.DOWNLOADABLE_OPEN
                FontFilter.PAID -> entry.availability == FontAvailability.COMMERCIAL_PAYMENT_REQUIRED
            }
            val matchesQuery = q.isEmpty() ||
                entry.familyName.lowercase().contains(q) ||
                entry.foundry.lowercase().contains(q) ||
                entry.sourcePack.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (missingTemplateFonts.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${missingTemplateFonts.size} template font(s) missing: " +
                            missingTemplateFonts.joinToString(", ") { it.familyName } +
                            ". Tap to license & import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.font_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("font_search_input"),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontFilter.entries.forEach { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(filter.label) },
                    modifier = Modifier.testTag("font_filter_${filter.name.lowercase()}"),
                )
            }
        }

        if (filteredFonts.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "No matching fonts found for \"$searchQuery\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredFonts, key = { it.id }) { entry ->
                    FontRowItem(
                        entry = entry,
                        isSelected = selectedFontFamily.equals(entry.familyName, ignoreCase = true),
                        onClick = {
                            when (entry.availability) {
                                FontAvailability.INSTALLED_FREE,
                                FontAvailability.USER_PURCHASED_INSTALLED -> onApplyReadyFont(entry)

                                FontAvailability.DOWNLOADABLE_OPEN -> onDownloadOpenFont(entry)

                                FontAvailability.COMMERCIAL_PAYMENT_REQUIRED -> {
                                    pendingCommercialFont = entry
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    pendingCommercialFont?.let { lockedFont ->
        CommercialFontLicenseModal(
            font = lockedFont,
            isImporting = isBusy,
            onDismiss = { pendingCommercialFont = null },
            onImportFontFile = { uri, expectedName ->
                onImportPurchasedFont(uri, expectedName)
                pendingCommercialFont = null
            },
        )
    }
}

@Composable
private fun FontRowItem(
    entry: FontEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val badgeColors = when (entry.availability) {
        FontAvailability.INSTALLED_FREE ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        FontAvailability.USER_PURCHASED_INSTALLED ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

        FontAvailability.DOWNLOADABLE_OPEN ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

        FontAvailability.COMMERCIAL_PAYMENT_REQUIRED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    val leadingIcon = when (entry.availability) {
        FontAvailability.INSTALLED_FREE,
        FontAvailability.USER_PURCHASED_INSTALLED -> Icons.Default.CheckCircle

        FontAvailability.DOWNLOADABLE_OPEN -> Icons.Default.Download

        FontAvailability.COMMERCIAL_PAYMENT_REQUIRED -> Icons.Default.Lock
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                entry.detectedMissingInTemplate -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("font_row_${entry.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = entry.availability.badgeText,
                tint = badgeColors.second,
                modifier = Modifier.size(20.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.familyName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.detectedMissingInTemplate) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.error,
                        ) {
                            Text(
                                text = "TEMPLATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "${entry.foundry} • ${entry.sourcePack}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = badgeColors.first,
            ) {
                Text(
                    text = entry.availability.badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColors.second,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Searchable Font Dropdown Modal Bottom Sheet launched from the 1:1 Card Editor toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontDropdownBottomSheet(
    fonts: List<FontEntry>,
    selectedFontFamily: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onApplyReadyFont: (FontEntry) -> Unit,
    onDownloadOpenFont: (FontEntry) -> Unit,
    onImportPurchasedFont: (Uri, String) -> Unit,
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
                .height(540.dp)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.font_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "MSE Pack, Open SIL OFL, and Licensed Commercial Foundries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("font_sheet_close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close font picker")
                }
            }
            Spacer(Modifier.height(10.dp))
            FontPickerList(
                fonts = fonts,
                selectedFontFamily = selectedFontFamily,
                isBusy = isBusy,
                onApplyReadyFont = { entry ->
                    onApplyReadyFont(entry)
                    onDismiss()
                },
                onDownloadOpenFont = onDownloadOpenFont,
                onImportPurchasedFont = onImportPurchasedFont,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
