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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.example.ui.coder.DataEditorXWorkbenchScreen
import com.example.ui.diagnostics.BridgeDiagnosticsScreen
import com.example.ui.diagnostics.BridgeDiagnosticsSheet
import com.example.ui.fonts.FontsScreen
import com.example.ui.nexus.AlignmentWarningDialog
import com.example.ui.nexus.EngineSyncViewModel
import com.example.ui.nexus.NEXUS_TAB_ENGINE_SYNC
import com.example.ui.nexus.NexusHubSyncScreen
import com.example.ui.nexus.NexusHubViewModel
import com.example.ui.theme.TcgStudioTheme
import com.example.ui.upstream.UpstreamScreen
import com.example.ui.welcome.ModeChooserScreen
import com.example.ui.welcome.WelcomeScreen
import com.example.ui.workspace.SetWorkspaceScreen
import kotlinx.coroutines.launch

data class HubDeepLinkRequest(
    val hubSync: Boolean = false,
    val exportClientInfo: Boolean = false,
    val fromApp: String? = null,
)

object StudioRoutes {
    const val MODE_CHOOSER = "studio_mode_chooser"
    const val WELCOME = "mse_welcome"
    const val WORKSPACE = "mse_workspace"
    const val CODING_WORKBENCH = "dataeditorx_coder"
    const val FONTS = "mse_fonts"
    const val UPSTREAM = "mse_upstream"
    const val BRIDGE = "mse_bridge"
    const val NEXUS_HUB = "mse_nexus_hub"
}

/**
 * Root Compose Navigation Shell — Offline-First Dual Workbench:
 * Asks on launch whether the user is Designing Cards (Magic Set Editor UI)
 * or Coding Cards (DataEditorX UI), with online-only upstream comparison gating.
 */
@Composable
fun TcgStudioApp(
    viewModel: StudioViewModel,
    nexusHubViewModel: NexusHubViewModel,
    engineSyncViewModel: EngineSyncViewModel,
    pendingHubDeepLink: HubDeepLinkRequest? = null,
    onHubDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val archive by viewModel.archive.collectAsStateWithLifecycle()
    val activeCardIndex by viewModel.activeCardIndex.collectAsStateWithLifecycle()
    val recentSets by viewModel.recentSets.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val styleSpec by viewModel.styleSpec.collectAsStateWithLifecycle()
    val bridgeHealth by viewModel.bridgeHealth.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    val offlineFrame by viewModel.offlineFrame.collectAsStateWithLifecycle()
    val bridgeTemplates by viewModel.bridgeTemplates.collectAsStateWithLifecycle()
    val discoveredGames by viewModel.discoveredGames.collectAsStateWithLifecycle()
    val discoveredStyles by viewModel.discoveredStyles.collectAsStateWithLifecycle()
    val catalogSyncState by viewModel.catalogSyncState.collectAsStateWithLifecycle()
    val fonts by viewModel.fonts.collectAsStateWithLifecycle()
    val manifest by viewModel.manifest.collectAsStateWithLifecycle()
    val isDeviceOnline by viewModel.isDeviceOnline.collectAsStateWithLifecycle()
    val commitChecks by viewModel.commitChecks.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val isSyncingBridge by viewModel.isSyncingBridge.collectAsStateWithLifecycle()
    val isFontBusy by viewModel.isFontBusy.collectAsStateWithLifecycle()
    val templateValidation by viewModel.templateValidation.collectAsStateWithLifecycle()
    val isValidatingTemplate by viewModel.isValidatingTemplate.collectAsStateWithLifecycle()
    val nexusHubState by nexusHubViewModel.uiState.collectAsStateWithLifecycle()
    val engineSyncState by engineSyncViewModel.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()
    var showBridgeSheet by remember { mutableStateOf(false) }
    var requestedHubTabIndex by remember { mutableStateOf<Int?>(null) }
    var pendingAutoSync by remember { mutableStateOf(false) }

    fun copyPlainText(label: String, text: String, snackbarMessage: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        uiScope.launch { snackbarHostState.showSnackbar(snackbarMessage) }
    }

    fun launchNexusTcgHandshake() {
        val intent = engineSyncViewModel.buildNexusTcgHandshakeIntent()
        runCatching { context.startActivity(intent) }
            .onFailure {
                uiScope.launch {
                    snackbarHostState.showSnackbar("NexusTCG app (com.aistudio.nexustcg.vxqrmk) is not installed on this device.")
                }
            }
    }

    LaunchedEffect(Unit) {
        launch {
            viewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
        launch {
            nexusHubViewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
        launch {
            engineSyncViewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCompletedFirstResume by remember { mutableStateOf(false) }

    // Re-run lightweight ETag-cached cross-repo checks whenever the user reopens the installed app while online
    DisposableEffect(lifecycleOwner, isDeviceOnline) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasCompletedFirstResume) {
                    hasCompletedFirstResume = true
                } else {
                    engineSyncViewModel.refreshCompanionAppStatus()
                    if (isDeviceOnline) {
                        engineSyncViewModel.syncLiveGameEngine(fineGrainedPat = null, isManualRefresh = false)
                        nexusHubViewModel.verifyMutualLinkAndCheckHub(silent = true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Automatic on-open cross-repository alignment evaluation (Engine + Master Hub + Dual-Engine Consent)
    LaunchedEffect(
        nexusHubState.linkStatus,
        nexusHubState.hasCheckedHubLink,
        engineSyncState.healthStatus,
        engineSyncState.contract,
        engineSyncState.stagedSnapshot,
        engineSyncState.hasCompletedStartupSync,
        engineSyncState.adminLocalState.dismissedAlignmentKeys,
    ) {
        engineSyncViewModel.reevaluateAlignmentWarning(
            mutualLinkStatus = nexusHubState.linkStatus,
            hasCheckedHubLink = nexusHubState.hasCheckedHubLink,
        )
    }

    // Handle tcgstudio://nexus-hub?hubSync=1&exportClientInfo=1&from=nexustcg
    LaunchedEffect(pendingHubDeepLink) {
        val req = pendingHubDeepLink ?: return@LaunchedEffect
        if (req.hubSync) {
            pendingAutoSync = true
            requestedHubTabIndex = NEXUS_TAB_ENGINE_SYNC
        }
        if (req.exportClientInfo) {
            val exportPayload = buildString {
                appendLine("{")
                appendLine("  \"nexusApp\": ${engineSyncViewModel.buildAppManifestJson()},")
                appendLine("  \"nexusRepoLink\": ${nexusHubViewModel.buildManifestJson()}")
                append("}")
            }
            copyPlainText(
                label = "tcgstudio-client-info.json",
                text = exportPayload,
                snackbarMessage = "Exported client info (nexus-app.json + nexus-repo-link.json) to clipboard.",
            )
        } else if (req.fromApp != null) {
            snackbarHostState.showSnackbar("Cross-app handshake opened from ${req.fromApp}.")
        }
        navController.navigate(StudioRoutes.NEXUS_HUB) {
            launchSingleTop = true
        }
        onHubDeepLinkConsumed()
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
                startDestination = StudioRoutes.MODE_CHOOSER,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(StudioRoutes.MODE_CHOOSER) {
                    ModeChooserScreen(
                        darkTheme = settings.darkTheme,
                        isDeviceOnline = isDeviceOnline,
                        bridgeHealth = bridgeHealth,
                        recentSetsCount = recentSets.size,
                        hasActiveSet = archive != null,
                        activeSetTitle = archive?.document?.setTitle,
                        stagedCodedCardsCount = nexusHubState.savedCodedCards.size,
                        activeCoderClientName = nexusHubState.selectedClient?.name ?: "Ultimate TCG (WIP)",
                        nexusLinkStatus = nexusHubState.linkStatus,
                        onToggleDarkTheme = viewModel::toggleDarkTheme,
                        onSelectDesignCards = {
                            navController.navigate(StudioRoutes.WELCOME) {
                                launchSingleTop = true
                            }
                        },
                        onResumeActiveSet = { openWorkspace() },
                        onSelectCodeCards = {
                            navController.navigate(StudioRoutes.CODING_WORKBENCH) {
                                launchSingleTop = true
                            }
                        },
                        onOpenUpstreamManager = { navController.navigate(StudioRoutes.UPSTREAM) },
                        onOpenBridgeSheet = { showBridgeSheet = true },
                        onOpenBridgeSetup = { navController.navigate(StudioRoutes.BRIDGE) },
                        onOpenNexusHub = { navController.navigate(StudioRoutes.NEXUS_HUB) },
                    )
                }

                composable(StudioRoutes.WELCOME) {
                    WelcomeScreen(
                        darkTheme = settings.darkTheme,
                        isDeviceOnline = isDeviceOnline,
                        bridgeHealth = bridgeHealth,
                        discoveredGames = discoveredGames,
                        discoveredStyles = discoveredStyles,
                        catalogSyncState = catalogSyncState,
                        recentSets = recentSets,
                        onToggleDarkTheme = viewModel::toggleDarkTheme,
                        onBackToModeChooser = {
                            navController.navigate(StudioRoutes.MODE_CHOOSER) {
                                popUpTo(StudioRoutes.MODE_CHOOSER) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onSwitchToCardCoder = {
                            navController.navigate(StudioRoutes.CODING_WORKBENCH) {
                                launchSingleTop = true
                            }
                        },
                        onRefreshCatalog = viewModel::refreshTemplateCatalog,
                        onToggleOptInPack = viewModel::toggleOptInPackChannel,
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
                        nexusLinkStatus = nexusHubState.linkStatus,
                        onOpenNexusHub = { navController.navigate(StudioRoutes.NEXUS_HUB) },
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
                            canUndo = canUndo,
                            canRedo = canRedo,
                            styleSpec = styleSpec,
                            bridgeHealth = bridgeHealth,
                            renderState = renderState,
                            offlineFrame = offlineFrame,
                            discoveredGames = discoveredGames,
                            discoveredStyles = discoveredStyles,
                            catalogSyncState = catalogSyncState,
                            fonts = fonts,
                            isFontBusy = isFontBusy,
                            resolveLocalFontFile = viewModel::resolveLocalFontFile,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onRefreshCatalog = viewModel::refreshTemplateCatalog,
                            onSelectOfflineFrameVariant = viewModel::selectOfflineFrameVariant,
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
                            onOpenNexusHub = { navController.navigate(StudioRoutes.NEXUS_HUB) },
                            onSwitchToCardCoder = {
                                navController.navigate(StudioRoutes.CODING_WORKBENCH) {
                                    launchSingleTop = true
                                }
                            },
                            onToggleDarkTheme = viewModel::toggleDarkTheme,
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

                composable(StudioRoutes.CODING_WORKBENCH) {
                    val activeCard = archive?.document?.cards?.getOrNull(activeCardIndex)
                    DataEditorXWorkbenchScreen(
                        darkTheme = settings.darkTheme,
                        isDeviceOnline = isDeviceOnline,
                        uiState = nexusHubState,
                        viewModel = nexusHubViewModel,
                        activeMseCardName = activeCard?.name,
                        activeMseCardType = activeCard?.fullTypeLine,
                        activeMseCardRules = activeCard?.ruleText,
                        onToggleDarkTheme = viewModel::toggleDarkTheme,
                        onBackToModeChooser = {
                            navController.navigate(StudioRoutes.MODE_CHOOSER) {
                                popUpTo(StudioRoutes.MODE_CHOOSER) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onSwitchToDesigner = {
                            val target = if (archive != null) StudioRoutes.WORKSPACE else StudioRoutes.WELCOME
                            navController.navigate(target) {
                                launchSingleTop = true
                            }
                        },
                        onOpenUpstreamCompare = { navController.navigate(StudioRoutes.UPSTREAM) },
                        onOpenNexusHubSync = { navController.navigate(StudioRoutes.NEXUS_HUB) },
                        onNotify = { msg ->
                            uiScope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                    )
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
                        title = "MSE & DataEditorX Updates",
                        onBack = { navController.popBackStack() },
                    ) {
                        UpstreamScreen(
                            manifest = manifest,
                            isDeviceOnline = isDeviceOnline,
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

                composable(StudioRoutes.NEXUS_HUB) {
                    val activeCard = archive?.document?.cards?.getOrNull(activeCardIndex)
                    ToolScreenScaffold(
                        title = "Nexus Master Hub & Engine Sync",
                        onBack = { navController.popBackStack() },
                    ) {
                        NexusHubSyncScreen(
                            uiState = nexusHubState,
                            engineSyncState = engineSyncState,
                            engineSyncViewModel = engineSyncViewModel,
                            autoTriggerSync = pendingAutoSync,
                            requestedTabIndex = requestedHubTabIndex,
                            activeMseCardName = activeCard?.name,
                            activeMseCardType = activeCard?.fullTypeLine,
                            activeMseCardRules = activeCard?.ruleText,
                            activeFrameWidthPx = styleSpec.cardWidthPx,
                            activeFrameHeightPx = styleSpec.cardHeightPx,
                            onAutoSyncConsumed = { pendingAutoSync = false },
                            onTabRequestConsumed = { requestedHubTabIndex = null },
                            onLaunchNexusTcgApp = ::launchNexusTcgHandshake,
                            onSaveManifest = nexusHubViewModel::updateManifest,
                            onVerifyAndPullHub = { nexusHubViewModel.verifyMutualLinkAndCheckHub(silent = false) },
                            onApplyPendingDiff = nexusHubViewModel::applyPendingHubIndexUpdate,
                            onDismissPendingDiff = nexusHubViewModel::dismissPendingHubDiff,
                            onSelectClient = nexusHubViewModel::selectClient,
                            onRedirectLockedToFork = nexusHubViewModel::redirectFromLockedClientToFork,
                            onUpdatePat = nexusHubViewModel::updatePat,
                            onCommitPackToHub = nexusHubViewModel::commitCardPackToLinkedHub,
                            onCommitManifestToThisRepo = nexusHubViewModel::commitRepoLinkManifestToThisRepo,
                            onBuildPackJson = nexusHubViewModel::buildCurrentExportPackJson,
                            onBuildManifestJson = nexusHubViewModel::buildManifestJson,
                            onBuildWorkflowYaml = nexusHubViewModel::buildWorkflowYaml,
                            onWriteFileToUri = nexusHubViewModel::writeUtf8ToUri,
                            viewModel = nexusHubViewModel,
                            onNotify = { msg ->
                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                            },
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

        // Automatic On-Open Alignment & Update Warning Pop-Up (visible to all users; Admin actions strictly gated)
        val alignmentWarning = engineSyncState.alignmentWarning
        if (alignmentWarning != null && alignmentWarning.shouldShowPopup) {
            AlignmentWarningDialog(
                evaluation = alignmentWarning,
                healthStatus = engineSyncState.healthStatus,
                contract = engineSyncState.contract,
                missingEngineFiles = engineSyncState.missingEngineFiles,
                stagedDiff = engineSyncState.stagedDiff,
                companionEngineApp = engineSyncState.companionEngineApp,
                isCurrentUserAdmin = engineSyncState.isCurrentUserAdmin,
                onOpenHandshakeArea = {
                    engineSyncViewModel.hideAlignmentWarningTemporarily()
                    requestedHubTabIndex = NEXUS_TAB_ENGINE_SYNC
                    navController.navigate(StudioRoutes.NEXUS_HUB) { launchSingleTop = true }
                },
                onLaunchNexusTcgApp = {
                    engineSyncViewModel.hideAlignmentWarningTemporarily()
                    launchNexusTcgHandshake()
                },
                onExportDiagnosticReport = {
                    copyPlainText(
                        label = "engine-health-status.json",
                        text = engineSyncViewModel.buildDiagnosticReportJson(),
                        snackbarMessage = "Copied signed engine-health-status.json diagnostic report.",
                    )
                },
                onDismissForSha = engineSyncViewModel::dismissAlignmentWarningForCurrentSha,
                onHideTemporarily = engineSyncViewModel::hideAlignmentWarningTemporarily,
                onAdminPauseSync = { engineSyncViewModel.pauseOrResumeSync(true) },
                onAdminSendDiscordWebhook = engineSyncViewModel::sendDiscordWebhookOnly,
                onAdminSendEmailRelay = engineSyncViewModel::sendAdminEmailRelayOnly,
                onAdminLaunchMailto = {
                    val mailIntent = Intent(Intent.ACTION_SENDTO, engineSyncViewModel.buildMailtoEscalationUri())
                    runCatching { context.startActivity(mailIntent) }
                },
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
