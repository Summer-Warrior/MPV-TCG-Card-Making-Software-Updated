package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.diagnostics.BridgeDiagnosticsScreen
import com.example.ui.editor.CardEditorScreen
import com.example.ui.fonts.FontsScreen
import com.example.ui.theme.TcgStudioTheme
import com.example.ui.upstream.UpstreamScreen

enum class StudioDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    EDITOR("card_editor", R.string.nav_editor, Icons.Filled.Style, Icons.Outlined.Style),
    FONTS("font_manager", R.string.nav_fonts, Icons.Filled.FontDownload, Icons.Outlined.FontDownload),
    UPSTREAM("upstream_manifest", R.string.nav_upstream, Icons.Filled.CloudSync, Icons.Outlined.CloudSync),
    BRIDGE("bridge_diagnostics", R.string.nav_bridge, Icons.Filled.Terminal, Icons.Outlined.Terminal),
}

@Composable
fun TcgStudioApp(viewModel: StudioViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val archive by viewModel.archive.collectAsStateWithLifecycle()
    val activeCardIndex by viewModel.activeCardIndex.collectAsStateWithLifecycle()
    val styleSpec by viewModel.styleSpec.collectAsStateWithLifecycle()
    val bridgeHealth by viewModel.bridgeHealth.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    val bridgeTemplates by viewModel.bridgeTemplates.collectAsStateWithLifecycle()
    val fonts by viewModel.fonts.collectAsStateWithLifecycle()
    val manifest by viewModel.manifest.collectAsStateWithLifecycle()
    val commitChecks by viewModel.commitChecks.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val isSyncingBridge by viewModel.isSyncingBridge.collectAsStateWithLifecycle()
    val isFontBusy by viewModel.isFontBusy.collectAsStateWithLifecycle()
    val templateValidation by viewModel.templateValidation.collectAsStateWithLifecycle()
    val isValidatingTemplate by viewModel.isValidatingTemplate.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: StudioDestination.EDITOR.route
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    TcgStudioTheme(darkTheme = settings.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            StudioDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes),
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_bar_${dest.route}"),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (useNavigationRail) {
                        NavigationRail {
                            StudioDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes),
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_rail_${dest.route}"),
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = StudioDestination.EDITOR.route,
                        modifier = Modifier.weight(1f),
                    ) {
                        composable(StudioDestination.EDITOR.route) {
                            CardEditorScreen(
                                settings = settings,
                                archive = archive,
                                activeCardIndex = activeCardIndex,
                                styleSpec = styleSpec,
                                bridgeHealth = bridgeHealth,
                                renderState = renderState,
                                fonts = fonts,
                                isFontBusy = isFontBusy,
                                rawSetTextProvider = viewModel::rawSetText,
                                onSaveBridgeUrl = viewModel::saveBridgeBaseUrl,
                                onRetryBridgeHealth = { viewModel.checkBridgeHealth(autoRenderIfOnline = true) },
                                onOpenFullDiagnostics = { navigateTo(StudioDestination.BRIDGE.route) },
                                onRequestRender = viewModel::requestBridgeRender,
                                onSelectCard = viewModel::selectCard,
                                onAddBlankCard = viewModel::addBlankCard,
                                onCreateNewBlankSet = viewModel::createNewBlankSet,
                                onOpenMseSetUri = viewModel::openMseSetUri,
                                onExportMseSetUri = viewModel::exportMseSetUri,
                                onLoadMseStyleUri = viewModel::loadMseStyleUri,
                                onUpdateCardField = viewModel::updateActiveCardField,
                                onEmbedCroppedArt = viewModel::embedCroppedCardArt,
                                onClearCardArt = viewModel::clearActiveCardArt,
                                onUpdateCalibrationScale = viewModel::updateCalibrationScale,
                                onToggleCalibrationLock = viewModel::toggleCalibrationLock,
                                onUpdatePhysicalProfile = viewModel::updatePhysicalProfile,
                                onApplyReadyFont = viewModel::applyReadyFont,
                                onDownloadOpenFont = viewModel::downloadAndApplyOpenFont,
                                onImportPurchasedFont = viewModel::importPurchasedFontUri,
                            )
                        }

                        composable(StudioDestination.FONTS.route) {
                            FontsScreen(
                                fonts = fonts,
                                selectedFontFamily = settings.selectedFontFamily,
                                isBusy = isFontBusy,
                                onRefreshBridgeFonts = { viewModel.checkBridgeHealth(autoRenderIfOnline = false) },
                                onApplyReadyFont = viewModel::applyReadyFont,
                                onDownloadOpenFont = viewModel::downloadAndApplyOpenFont,
                                onImportPurchasedFont = viewModel::importPurchasedFontUri,
                            )
                        }

                        composable(StudioDestination.UPSTREAM.route) {
                            UpstreamScreen(
                                manifest = manifest,
                                commitChecks = commitChecks,
                                isCheckingUpdates = isCheckingUpdates,
                                isSyncingBridge = isSyncingBridge,
                                exportManifestJson = viewModel::exportManifestJson,
                                onCheckGitHubUpdates = viewModel::checkAllGitHubUpdates,
                                onTriggerBridgeDeltaSync = viewModel::triggerBridgeDeltaSync,
                                onToggleSplitEnabled = viewModel::toggleSplitEnabled,
                                onUpdateSplitBranch = viewModel::updateSplitBranch,
                                onPinSplitSha = viewModel::pinSplitSha,
                                onRollbackSplitSha = viewModel::rollbackSplitSha,
                                onUpdateWebhookRelayUrl = viewModel::updateWebhookRelayUrl,
                                onAddConnector = viewModel::addOpenResourceConnector,
                                onRemoveConnector = viewModel::removeOpenResourceConnector,
                                onSaveRawManifestJson = viewModel::saveRawManifestJson,
                            )
                        }

                        composable(StudioDestination.BRIDGE.route) {
                            BridgeDiagnosticsScreen(
                                settings = settings,
                                bridgeHealth = bridgeHealth,
                                bridgeTemplates = bridgeTemplates,
                                styleSpec = styleSpec,
                                templateValidation = templateValidation,
                                isValidatingTemplate = isValidatingTemplate,
                                onSaveBridgeUrl = viewModel::saveBridgeBaseUrl,
                                onTestHealth = { viewModel.checkBridgeHealth(autoRenderIfOnline = true) },
                                onUpdateEngineSplit = viewModel::updateEngineSplit,
                                onToggleDarkTheme = viewModel::toggleDarkTheme,
                                onValidateTemplate = viewModel::validateTemplateOnBridge,
                            )
                        }
                    }
                }
            }
        }
    }
}
