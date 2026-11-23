package com.example.ui.fonts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.fonts.FontAvailability
import com.example.data.fonts.FontEntry

/**
 * Full Font Manager Screen (Section 5) with unified searchable font catalog,
 * SAF `.ttf`/`.otf`/`.zip` uploader to `POST /fonts/import`, and commercial licensing modal flow.
 */
@Composable
fun FontsScreen(
    fonts: List<FontEntry>,
    selectedFontFamily: String?,
    isBusy: Boolean,
    onRefreshBridgeFonts: () -> Unit,
    onApplyReadyFont: (FontEntry) -> Unit,
    onDownloadOpenFont: (FontEntry) -> Unit,
    onImportPurchasedFont: (Uri, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manualFontImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            onImportPurchasedFont(uri, null)
        }
    }

    val readyCount = fonts.count { it.availability.isReady }
    val paidCount = fonts.count { it.availability == FontAvailability.COMMERCIAL_PAYMENT_REQUIRED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$readyCount ready • $paidCount licensed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onRefreshBridgeFonts,
                    modifier = Modifier.testTag("fonts_refresh_bridge_button"),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh engine fonts")
                }
                FilledTonalButton(
                    onClick = {
                        manualFontImportLauncher.launch(
                            arrayOf(
                                "font/ttf",
                                "font/otf",
                                "application/zip",
                                "application/octet-stream",
                                "*/*",
                            )
                        )
                    },
                    modifier = Modifier.testTag("fonts_manual_import_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Import", maxLines = 1)
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FontDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Commercial fonts are never bundled or silently substituted. Tap any Paid 🔒 entry to open the official foundry license page in your browser, then import your .ttf/.otf/.zip file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FontPickerList(
            fonts = fonts,
            selectedFontFamily = selectedFontFamily,
            isBusy = isBusy,
            onApplyReadyFont = onApplyReadyFont,
            onDownloadOpenFont = onDownloadOpenFont,
            onImportPurchasedFont = { uri, expected -> onImportPurchasedFont(uri, expected) },
            modifier = Modifier.weight(1f),
        )
    }
}
