package com.example.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.bridge.BridgeTemplateDto
import com.example.data.bridge.TemplateValidateResponse
import com.example.data.mse.MseStyleSpec
import com.example.data.settings.EngineSplit
import com.example.data.settings.StudioSettings
import com.example.ui.BridgeHealthState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber

private const val TERMUX_STARTUP_SNIPPET = """# Inside Termux ARM64 (Ubuntu proot / native ARM64):
# 1. Verify upstream MSE CLI binaries:
magicseteditor --version
# 2. Start your local MSE Bridge on 127.0.0.1:8765:
mse-bridge --host 127.0.0.1 --port 8765 \
  --modern-bin ~/mse-modern/magicseteditor \
  --legacy-bin ~/mse-legacy/magicseteditor
# 3. Verify health endpoint:
curl -s http://127.0.0.1:8765/health"""

/**
 * Termux ARM64 MSE Bridge & Diagnostics Screen (Section 2).
 * Provides live endpoint verification, editable Bridge URL, engine split routing,
 * `POST /templates/validate` interactive CLI check, and Termux startup instructions.
 */
@Composable
fun BridgeDiagnosticsScreen(
    settings: StudioSettings,
    bridgeHealth: BridgeHealthState,
    bridgeTemplates: List<BridgeTemplateDto>,
    styleSpec: MseStyleSpec,
    templateValidation: TemplateValidateResponse?,
    isValidatingTemplate: Boolean,
    onSaveBridgeUrl: (String) -> Unit,
    onTestHealth: () -> Unit,
    onUpdateEngineSplit: (EngineSplit) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onValidateTemplate: (templateId: String, styleSource: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var urlInput by remember(settings.bridgeBaseUrl) { mutableStateOf(settings.bridgeBaseUrl) }
    var copiedNotice by remember { mutableStateOf(false) }
    var validateTemplateId by remember { mutableStateOf("custom-test.mse-style") }
    var validateStyleSource by remember {
        mutableStateOf(
            "mse version: 2.0.0\ngame: magic\nshort name: Custom Test\ncard width: 375\ncard height: 523\ncard dpi: 150"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ---- Connection & Base URL Card ----
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (bridgeHealth) {
                                is BridgeHealthState.Checking -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )

                                is BridgeHealthState.Online -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Online",
                                    tint = StatusConnected,
                                )

                                is BridgeHealthState.Offline -> Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = "Offline",
                                    tint = StatusOfflineAmber,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (bridgeHealth) {
                                    is BridgeHealthState.Checking -> "Checking engine…"
                                    is BridgeHealthState.Online -> "Connected (${bridgeHealth.latencyMs}ms)"
                                    is BridgeHealthState.Offline -> "Offline (${bridgeHealth.latencyMs}ms)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        IconButton(
                            onClick = onTestHealth,
                            modifier = Modifier.testTag("diagnostics_refresh_health"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry health check")
                        }
                    }

                    when (bridgeHealth) {
                        is BridgeHealthState.Online -> {
                            Text(
                                text = "Engines: Modern=${bridgeHealth.response.engines.mseModern}, " +
                                    "Legacy=${bridgeHealth.response.engines.mseLegacy}\n" +
                                    "Installed splits: ${bridgeHealth.response.installedSplits.joinToString(", ").ifEmpty { "none" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusConnected,
                            )
                        }

                        is BridgeHealthState.Offline -> {
                            Text(
                                text = "Unable to reach ${bridgeHealth.baseUrl}/health (${bridgeHealth.detail}). Start the Termux bridge below for live MSE rendering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        is BridgeHealthState.Checking -> Unit
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(R.string.bridge_url_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diagnostics_url_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onSaveBridgeUrl(urlInput) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("diagnostics_save_url_button"),
                        ) {
                            Text(stringResource(R.string.bridge_save_url))
                        }
                        OutlinedButton(
                            onClick = {
                                urlInput = StudioSettings.DEFAULT_BRIDGE_URL
                                onSaveBridgeUrl(StudioSettings.DEFAULT_BRIDGE_URL)
                            },
                            modifier = Modifier.testTag("diagnostics_reset_url_button"),
                        ) {
                            Text("Reset", maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---- Default Engine Split & Appearance ----
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
                    Text("Active MSE Engine Split", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EngineSplit.entries.forEach { split ->
                            FilterChip(
                                selected = settings.activeEngineSplit == split,
                                onClick = { onUpdateEngineSplit(split) },
                                label = { Text(split.label) },
                                modifier = Modifier.testTag("engine_split_${split.apiValue}"),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Obsidian Dark Studio Theme", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = settings.darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.testTag("diagnostics_theme_switch"),
                        )
                    }
                }
            }
        }

        // ---- Termux ARM64 Startup Guide ----
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.bridge_termux_help_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Termux MSE Bridge", TERMUX_STARTUP_SNIPPET))
                                copiedNotice = true
                            },
                            modifier = Modifier.testTag("diagnostics_copy_termux"),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Termux startup commands")
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = TERMUX_STARTUP_SNIPPET,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                            ),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }

                    if (copiedNotice) {
                        Text(
                            text = "Copied Termux startup commands to clipboard.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        // ---- Bridge Templates & Active .mse-style Spec ----
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Active .mse-style Geometry & Bridge Templates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Loaded style: ${styleSpec.shortName} (${styleSpec.cardWidthPx}×${styleSpec.cardHeightPx} px @ ${styleSpec.cardDpi} DPI)\n" +
                            "Source: ${styleSpec.sourceLabel}\n" +
                            "Hitboxes parsed: ${styleSpec.hitboxes.size} • Referenced fonts: ${styleSpec.referencedFonts.joinToString(", ").ifEmpty { "none" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "GET /templates discovered: ${bridgeTemplates.size} template(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ---- POST /templates/validate Live Runner ----
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
                        text = "MSE CLI Template Validator (POST /templates/validate)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = validateTemplateId,
                        onValueChange = { validateTemplateId = it },
                        label = { Text("Template ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = validateStyleSource,
                        onValueChange = { validateStyleSource = it },
                        label = { Text(".mse-style Source") },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = !isValidatingTemplate,
                        onClick = { onValidateTemplate(validateTemplateId, validateStyleSource) },
                        modifier = Modifier.testTag("diagnostics_validate_template_button"),
                    ) {
                        if (isValidatingTemplate) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Run magicseteditor --cli --raw")
                    }

                    templateValidation?.let { res ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "valid=${res.valid} • exit=${res.statusCode}\n" +
                                    if (res.errors.isEmpty()) "No MSE CLI errors." else res.errors.joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (res.valid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
