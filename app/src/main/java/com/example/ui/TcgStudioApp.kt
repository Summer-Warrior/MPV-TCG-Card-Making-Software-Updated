package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.diagnostics.BridgeDiagnosticsScreen
import com.example.ui.diagnostics.BridgeDiagnosticsSheet
import com.example.ui.fonts.FontsScreen
import com.example.ui.theme.TcgStudioTheme
import com.example.ui.upstream.UpstreamScreen
import com.example.ui.welcome.WelcomeScreen
import com.example.ui.workspace.SetWorkspaceScreen

object StudioRoutes {
    const val WELCOME = "mse_welcome"
    const val WORKSPACE = "mse_workspace"
    const val FONTS = "mse_fonts"
    const val UPSTREAM = "mse_upstream"
    const val BRIDGE = "mse_bridge"
}

/**
 * Root Compose Navigation Shell — Authentic Magic Set Editor workflow.
 * Launches directly into the MSE Welcome Window (zero generic 4-tab developer bottom bar).
 */
@Composable
fun TcgStudioApp(viewModel: StudioViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val archive by viewModel.archive.collectAsStateWithLifecycle()
    val activeCardIndex by viewModel.activeCardIndex.collectAsStateWithLifecycle()
    val recentSets by viewModel.recentSets.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showBridgeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun openWorkspace() {
        navController.navigate(StudioRoutes.WORKSPACE) {
            launchSingleTop = true
        }
    }

    TcgStudioTheme(darkTheme = settings.darkTheme) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = StudioRoutes.WELCOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(StudioRoutes.WELCOME) {
                    WelcomeScreen(
                        bridgeHealth = bridgeHealth,
                        bridgeTemplates = bridgeTemplates,
                        recentSets = recentSets,
                        onOpenBridgeSheet = { showBridgeSheet = true },
                        onCreateNewSet = { title, gameId, styleOption ->
                            viewModel.createNewSetFromWizard(
                                title = title,
                                gameId = gameId,
                                styleOption = styleOption,
                                onOpened = ::openWorkspace,
                            )
                        },
                        onOpenSetUri = { uri ->
                            viewModel.openMseSetUri(uri, onOpened = ::openWorkspace)
                        },
                        onOpenRecentSet = { record ->
                            viewModel.openRecentSet(record, onOpened = ::openWorkspace)
                        },
                        onOpenFontsManager = { navController.navigate(StudioRoutes.FONTS) },
                        onOpenUpstreamManager = { navController.navigate(StudioRoutes.UPSTREAM) },
                        onOpenBridgeSetup = { navController.navigate(StudioRoutes.BRIDGE) },
                    )
                }

                composable(StudioRoutes.WORKSPACE) {
                    val activeArchive = archive
                    if (activeArchive == null) {
                        LaunchedEffect(Unit) {
                            navController.popBackStack(StudioRoutes.WELCOME, inclusive = false)
                        }
                    } else {
                        SetWorkspaceScreen(
                            settings = settings,
                            archive = activeArchive,
                            activeCardIndex = activeCardIndex,
                            hasUnsavedChanges = hasUnsavedChanges,
                            styleSpec = styleSpec,
                            bridgeHealth = bridgeHealth,
                            renderState = renderState,
                            bridgeTemplates = bridgeTemplates,
                            fonts = fonts,
                            isFontBusy = isFontBusy,
                            onRequestCloseSet = {
                                viewModel.closeActiveSet {
                                    navController.popBackStack(StudioRoutes.WELCOME, inclusive = false)
                                }
                            },
                            onSaveSet = { viewModel.saveActiveSet() },
                            onSaveSetAndClose = {
                                viewModel.saveActiveSet {
                                    viewModel.closeActiveSet {
                                        navController.popBackStack(StudioRoutes.WELCOME, inclusive = false)
                                    }
                                }
                            },
                            onExportSetUri = viewModel::exportMseSetUri,
                            onLoadStyleUri = viewModel::loadMseStyleUri,
                            onOpenBridgeSheet = { showBridgeSheet = true },
                            onOpenUpstreamSplits = { navController.navigate(StudioRoutes.UPSTREAM) },
                            onOpenBridgeSettings = { navController.navigate(StudioRoutes.BRIDGE) },
                            onSelectCard = viewModel::selectCard,
                            onAddBlankCard = viewModel::addBlankCard,
                            onDuplicateCard = viewModel::duplicateCard,
                            onDeleteCard = viewModel::deleteCard,
                            onUpdateCardFields = viewModel::updateActiveCardFields,
                            onEmbedCroppedArt = viewModel::embedCroppedCardArt,
                            onClearCardArt = viewModel::clearActiveCardArt,
                            onUpdateSetInfo = viewModel::updateSetInfo,
                            onSwitchStylesheet = viewModel::switchStylesheet,
                            onAddKeyword = viewModel::addKeyword,
                            onUpdateKeyword = viewModel::updateKeyword,
                            onDeleteKeyword = viewModel::deleteKeyword,
                            onUpdateCalibrationScale = viewModel::updateCalibrationScale,
                            onToggleCalibrationLock = viewModel::toggleCalibrationLock,
                            onUpdatePhysicalProfile = viewModel::updatePhysicalProfile,
                            onApplyReadyFont = viewModel::applyReadyFont,
                            onDownloadOpenFont = viewModel::downloadAndApplyOpenFont,
                            onImportPurchasedFont = viewModel::importPurchasedFontUri,
                        )
                    }
                }

                composable(StudioRoutes.FONTS) {
                    ToolScreenScaffold(
                        title = "Fonts & Licenses",
                        onBack = { navController.popBackStack() },
                    ) {
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
                }

                composable(StudioRoutes.UPSTREAM) {
                    ToolScreenScaffold(
                        title = "Upstream Splits & Updates",
                        onBack = { navController.popBackStack() },
                    ) {
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
                }

                composable(StudioRoutes.BRIDGE) {
                    ToolScreenScaffold(
                        title = "Bridge & Termux Setup",
                        onBack = { navController.popBackStack() },
                    ) {
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

        if (showBridgeSheet) {
            BridgeDiagnosticsSheet(
                healthState = bridgeHealth,
                currentBaseUrl = settings.bridgeBaseUrl,
                onSaveUrl = viewModel::saveBridgeBaseUrl,
                onRetryHealth = { viewModel.checkBridgeHealth(autoRenderIfOnline = archive != null) },
                onOpenFullSetup = { navController.navigate(StudioRoutes.BRIDGE) },
                onDismiss = { showBridgeSheet = false },
            )
        }
    }
}

@Composable
private fun ToolScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("tool_screen_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}
