package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.bridge.BridgeHealthResponse
import com.example.data.bridge.BridgeResult
import com.example.data.bridge.BridgeTemplateDto
import com.example.data.bridge.TemplateValidateRequest
import com.example.data.bridge.TemplateValidateResponse
import com.example.data.fonts.FontEntry
import com.example.data.mse.CatalogPackFilter
import com.example.data.mse.CatalogSyncState
import com.example.data.mse.DiscoveredGame
import com.example.data.mse.DiscoveredStyle
import com.example.data.mse.FrameVariantOption
import com.example.data.mse.MseHitbox
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseSetInfo
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.RecentSetRecord
import com.example.data.mse.ResolvedOfflineFrame
import com.example.data.mse.MseWorkspaceRepository
import com.example.data.settings.EngineSplit
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.data.upstream.GitHubCommitCheckResult
import com.example.data.upstream.UpstreamManifest
import com.example.domain.comparison.CardOrigin
import com.example.domain.comparison.ComparableCardRecord
import com.example.domain.comparison.ComparableSet
import com.example.domain.comparison.SetComparisonEngine
import com.example.domain.comparison.SplitOffKind
import com.example.domain.comparison.SplitOffSetManifest
import com.example.domain.frameworks.UniversalCardFramework
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class BridgeHealthState {
    data object Checking : BridgeHealthState()
    data class Online(
        val response: BridgeHealthResponse,
        val baseUrl: String,
        val latencyMs: Long,
    ) : BridgeHealthState()

    data class Offline(
        val baseUrl: String,
        val detail: String,
        val latencyMs: Long,
    ) : BridgeHealthState()
}

sealed class CardRenderState {
    data object Idle : CardRenderState()
    data object Rendering : CardRenderState()
    data class Rendered(
        val bitmap: Bitmap,
        val widthPx: Int,
        val heightPx: Int,
        val dpi: Int,
        val missingFonts: List<String>,
        val latencyMs: Long,
    ) : CardRenderState()

    data class BridgeOffline(
        val baseUrl: String,
        val detail: String,
    ) : CardRenderState()

    data class Error(
        val message: String,
    ) : CardRenderState()
}

class StudioViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<StudioSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioSettings())

    val archive: StateFlow<MseSetArchive?> = container.mseWorkspaceRepository.archive
    val splitOffManifest: StateFlow<SplitOffSetManifest?> = container.mseWorkspaceRepository.splitOffManifest
    val activeCardIndex: StateFlow<Int> = container.mseWorkspaceRepository.activeCardIndex
    val styleSpec: StateFlow<MseStyleSpec> = container.mseWorkspaceRepository.styleSpec
    val recentSets: StateFlow<List<RecentSetRecord>> = container.mseWorkspaceRepository.recentSets
    val hasUnsavedChanges: StateFlow<Boolean> = container.mseWorkspaceRepository.hasUnsavedChanges
    val canUndo: StateFlow<Boolean> = container.mseWorkspaceRepository.canUndo
    val canRedo: StateFlow<Boolean> = container.mseWorkspaceRepository.canRedo

    val universalFrameworks: StateFlow<List<UniversalCardFramework>> =
        container.universalFrameworkRepository.frameworks

    val activeFramework: StateFlow<UniversalCardFramework?> = combine(
        archive,
        splitOffManifest,
        universalFrameworks,
    ) { arc, manifest, _ ->
        val byManifest = container.universalFrameworkRepository.findById(manifest?.frameworkId)
        byManifest ?: container.universalFrameworkRepository.findBestMatchForGame(arc?.document?.game)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val discoveredGames: StateFlow<List<DiscoveredGame>> = container.mseTemplateCatalogRepository.games
    val discoveredStyles: StateFlow<List<DiscoveredStyle>> = container.mseTemplateCatalogRepository.styles
    val catalogSyncState: StateFlow<CatalogSyncState> = container.mseTemplateCatalogRepository.syncState

    val fonts: StateFlow<List<FontEntry>> = container.fontCatalogRepository.unifiedFontsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val manifest: StateFlow<UpstreamManifest> = container.upstreamManifestRepository.manifest

    val isDeviceOnline: StateFlow<Boolean> = container.networkConnectivityMonitor.isOnline

    private val _bridgeHealth = MutableStateFlow<BridgeHealthState>(BridgeHealthState.Checking)
    val bridgeHealth: StateFlow<BridgeHealthState> = _bridgeHealth.asStateFlow()

    private val _renderState = MutableStateFlow<CardRenderState>(CardRenderState.Idle)
    val renderState: StateFlow<CardRenderState> = _renderState.asStateFlow()

    private val _offlineFrame = MutableStateFlow<ResolvedOfflineFrame?>(null)
    val offlineFrame: StateFlow<ResolvedOfflineFrame?> = _offlineFrame.asStateFlow()

    private val _bridgeTemplates = MutableStateFlow<List<BridgeTemplateDto>>(emptyList())
    val bridgeTemplates: StateFlow<List<BridgeTemplateDto>> = _bridgeTemplates.asStateFlow()

    private val _commitChecks = MutableStateFlow<Map<String, GitHubCommitCheckResult>>(emptyMap())
    val commitChecks: StateFlow<Map<String, GitHubCommitCheckResult>> = _commitChecks.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _isSyncingBridge = MutableStateFlow(false)
    val isSyncingBridge: StateFlow<Boolean> = _isSyncingBridge.asStateFlow()

    private val _isFontBusy = MutableStateFlow(false)
    val isFontBusy: StateFlow<Boolean> = _isFontBusy.asStateFlow()

    private val _templateValidation = MutableStateFlow<TemplateValidateResponse?>(null)
    val templateValidation: StateFlow<TemplateValidateResponse?> = _templateValidation.asStateFlow()

    private val _isValidatingTemplate = MutableStateFlow(false)
    val isValidatingTemplate: StateFlow<Boolean> = _isValidatingTemplate.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            styleSpec.collect { spec ->
                container.fontCatalogRepository.updateStyleReferencedFonts(spec.referencedFonts)
            }
        }
        viewModelScope.launch {
            container.mseTemplateCatalogRepository.packFonts.collect { packFonts ->
                container.fontCatalogRepository.updateDiscoveredPackFonts(packFonts)
            }
        }
        // Re-resolve active style spec & offline frame once dynamic catalog finishes indexing
        viewModelScope.launch {
            discoveredStyles.collect {
                val active = archive.value ?: return@collect
                val matched = container.mseTemplateCatalogRepository.findStyle(
                    gameId = active.document.game,
                    stylesheetId = active.document.stylesheet,
                )
                if (matched != null) {
                    val currentKey = "${matched.packRepo}:${matched.folderName}"
                    if (_offlineFrame.value?.styleKey != currentKey) {
                        resolveOfflineBaseFrameForStyle(matched)
                    }
                }
            }
        }
        viewModelScope.launch {
            if (container.networkConnectivityMonitor.checkIsOnlineNow()) {
                container.mseTemplateCatalogRepository.syncCatalog(forceRefresh = false)
            }
        }
        checkBridgeHealth(autoRenderIfOnline = false)
    }

    // ---- Dynamic Template Catalog & Real Upstream Offline Base-Frame Resolution ----

    fun refreshTemplateCatalog() {
        if (!container.networkConnectivityMonitor.checkIsOnlineNow()) {
            _messages.tryEmit("Offline mode — connect to the internet to sync upstream Magic Set Editor template packs.")
            return
        }
        viewModelScope.launch {
            container.mseTemplateCatalogRepository.syncCatalog(forceRefresh = true)
            val state = catalogSyncState.value
            _messages.tryEmit(
                "Catalog synced: ${state.syncedGamesCount} games • ${state.syncedStylesCount} styles"
            )
        }
    }

    fun toggleOptInPackChannel(filter: CatalogPackFilter, enabled: Boolean) {
        viewModelScope.launch {
            container.mseTemplateCatalogRepository.setOptInPackEnabled(filter, enabled)
        }
    }

    fun selectOfflineFrameVariant(variantKey: String) {
        val current = _offlineFrame.value ?: return
        _offlineFrame.value = current.copy(activeVariantKey = variantKey)
        // Persist chosen card color cleanly into the `.mse-set` card record
        val activeIdx = activeCardIndex.value
        val activeArchive = archive.value ?: return
        if (activeArchive.document.game.equals("magic", ignoreCase = true)) {
            viewModelScope.launch {
                container.mseWorkspaceRepository.updateCardField(activeIdx, "card color", variantKey)
            }
        }
    }

    fun resolveLocalFontFile(familyName: String?): File? =
        container.fontCatalogRepository.resolveLocalFontFile(familyName)

    private fun resolveOfflineBaseFrameForStyle(style: DiscoveredStyle?) {
        if (style == null) {
            _offlineFrame.value = null
            return
        }
        val styleKey = "${style.packRepo}:${style.folderName}"
        viewModelScope.launch {
            _offlineFrame.value = ResolvedOfflineFrame(
                styleKey = styleKey,
                variants = emptyList(),
                activeVariantKey = null,
                isLoading = true,
            )
            val variants = container.mseTemplateCatalogRepository.resolveOfflineFrameVariants(style)
            val inferredKey = inferActiveVariantKeyForCurrentCard(variants)
            _offlineFrame.value = ResolvedOfflineFrame(
                styleKey = styleKey,
                variants = variants,
                activeVariantKey = inferredKey,
                isLoading = false,
            )
        }
    }

    private fun inferActiveVariantKeyForCurrentCard(variants: List<FrameVariantOption>): String? {
        if (variants.isEmpty()) return null
        val card = archive.value?.document?.cards?.getOrNull(activeCardIndex.value)
        val explicitColor = card?.field("card color")?.trim()?.lowercase().orEmpty()
        if (explicitColor.isNotEmpty()) {
            variants.firstOrNull {
                explicitColor.contains(it.key.lowercase()) || it.key.equals(explicitColor, ignoreCase = true)
            }?.let { return it.key }
        }

        // Infer Magic frame color from casting cost or super type if not explicitly set
        val cost = card?.castingCost.orEmpty().uppercase()
        val superType = card?.superType.orEmpty().lowercase()
        val detectedKey = when {
            superType.contains("land") -> "land"
            superType.contains("artifact") -> "artifact"
            else -> {
                val present = buildList {
                    if (cost.contains('W')) add("white")
                    if (cost.contains('U')) add("blue")
                    if (cost.contains('B')) add("black")
                    if (cost.contains('R')) add("red")
                    if (cost.contains('G')) add("green")
                }
                when {
                    present.size >= 2 -> "multicolor"
                    present.size == 1 -> present.first()
                    else -> "colorless"
                }
            }
        }
        return variants.firstOrNull { it.key.equals(detectedKey, ignoreCase = true) }?.key
            ?: variants.firstOrNull()?.key
    }

    private fun syncVariantSelectionToCurrentCard() {
        val current = _offlineFrame.value ?: return
        if (current.variants.isEmpty()) return
        val inferred = inferActiveVariantKeyForCurrentCard(current.variants)
        if (inferred != null && inferred != current.activeVariantKey) {
            _offlineFrame.value = current.copy(activeVariantKey = inferred)
        }
    }

    // ---- Bridge Diagnostics & Rendering ----

    fun checkBridgeHealth(autoRenderIfOnline: Boolean = true) {
        viewModelScope.launch {
            _bridgeHealth.value = BridgeHealthState.Checking
            val baseUrl = container.settingsRepository.currentBridgeBaseUrl()
            when (val res = container.mseBridgeClient.getHealth()) {
                is BridgeResult.Success -> {
                    _bridgeHealth.value = BridgeHealthState.Online(
                        response = res.data,
                        baseUrl = baseUrl,
                        latencyMs = res.latencyMs,
                    )
                    refreshBridgeTemplatesAndFonts()
                    if (autoRenderIfOnline && archive.value != null) {
                        requestBridgeRender()
                    }
                }

                is BridgeResult.Offline -> {
                    _bridgeHealth.value = BridgeHealthState.Offline(
                        baseUrl = res.baseUrl,
                        detail = res.detail,
                        latencyMs = res.latencyMs,
                    )
                    _renderState.value = CardRenderState.BridgeOffline(
                        baseUrl = res.baseUrl,
                        detail = res.detail,
                    )
                }

                is BridgeResult.HttpError -> {
                    _bridgeHealth.value = BridgeHealthState.Offline(
                        baseUrl = baseUrl,
                        detail = "HTTP ${res.code}: ${res.body.ifBlank { "Bridge rejected /health" }}",
                        latencyMs = res.latencyMs,
                    )
                    _renderState.value = CardRenderState.Error("Bridge HTTP ${res.code}")
                }

                is BridgeResult.ParseError -> {
                    _bridgeHealth.value = BridgeHealthState.Offline(
                        baseUrl = baseUrl,
                        detail = res.detail,
                        latencyMs = res.latencyMs,
                    )
                    _renderState.value = CardRenderState.Error(res.detail)
                }
            }
        }
    }

    private fun refreshBridgeTemplatesAndFonts() {
        viewModelScope.launch {
            val templatesRes = container.mseBridgeClient.getTemplates()
            if (templatesRes is BridgeResult.Success) {
                _bridgeTemplates.value = templatesRes.data.templates
                container.mseTemplateCatalogRepository.mergeBridgeTemplates(templatesRes.data.templates)
            }
            container.fontCatalogRepository.refreshFromBridge()
        }
    }

    fun requestBridgeRender() {
        viewModelScope.launch {
            val currentArchive = archive.value ?: return@launch
            val zipBytes = container.mseWorkspaceRepository.serializeCurrentArchiveBytes() ?: return@launch
            _renderState.value = CardRenderState.Rendering
            val cardIdx = activeCardIndex.value
            val currentSettings = settings.value
            val sheetId = currentArchive.document.stylesheet

            when (
                val res = container.mseBridgeClient.renderCard(
                    mseSetBytes = zipBytes,
                    setFileName = currentArchive.displayName,
                    cardIndex = cardIdx,
                    engineSplit = currentSettings.activeEngineSplit,
                    stylesheetId = sheetId,
                )
            ) {
                is BridgeResult.Success -> {
                    val data = res.data
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(data.pngBytes, 0, data.pngBytes.size)
                    }
                    if (bitmap == null) {
                        _renderState.value = CardRenderState.Error("Bridge returned unreadable PNG bytes.")
                        return@launch
                    }

                    val mappedHitboxes = data.hitboxes.map { dto ->
                        MseHitbox(
                            field = dto.field,
                            left = dto.left,
                            top = dto.top,
                            width = dto.width,
                            height = dto.height,
                            zIndex = dto.zIndex,
                        )
                    }
                    container.mseWorkspaceRepository.updateStyleSpecFromBridge(
                        stylesheetId = sheetId,
                        cardWidthPx = data.cardWidthPx,
                        cardHeightPx = data.cardHeightPx,
                        cardDpi = data.cardDpi,
                        hitboxes = mappedHitboxes,
                        missingFonts = data.missingFonts,
                    )
                    container.fontCatalogRepository.updateBridgeMissingFonts(data.missingFonts)

                    _renderState.value = CardRenderState.Rendered(
                        bitmap = bitmap,
                        widthPx = data.cardWidthPx,
                        heightPx = data.cardHeightPx,
                        dpi = data.cardDpi,
                        missingFonts = data.missingFonts,
                        latencyMs = res.latencyMs,
                    )
                }

                is BridgeResult.Offline -> {
                    _renderState.value = CardRenderState.BridgeOffline(
                        baseUrl = res.baseUrl,
                        detail = res.detail,
                    )
                    _bridgeHealth.value = BridgeHealthState.Offline(
                        baseUrl = res.baseUrl,
                        detail = res.detail,
                        latencyMs = res.latencyMs,
                    )
                }

                is BridgeResult.HttpError -> {
                    _renderState.value = CardRenderState.Error("Render HTTP ${res.code}: ${res.body}")
                }

                is BridgeResult.ParseError -> {
                    _renderState.value = CardRenderState.Error("Render error: ${res.detail}")
                }
            }
        }
    }

    fun validateTemplateOnBridge(templateId: String, styleSource: String) {
        viewModelScope.launch {
            _isValidatingTemplate.value = true
            val res = container.mseBridgeClient.validateTemplate(
                TemplateValidateRequest(
                    templateId = templateId,
                    engineSplit = settings.value.activeEngineSplit.apiValue,
                    styleSource = styleSource,
                )
            )
            _isValidatingTemplate.value = false
            when (res) {
                is BridgeResult.Success -> {
                    _templateValidation.value = res.data
                    _messages.tryEmit(
                        if (res.data.valid) "MSE CLI validation passed."
                        else "MSE CLI reported ${res.data.errors.size} error(s)."
                    )
                }

                is BridgeResult.Offline -> _messages.tryEmit("Bridge offline — start Termux MSE Bridge first.")
                is BridgeResult.HttpError -> _messages.tryEmit("Validation HTTP ${res.code}")
                is BridgeResult.ParseError -> _messages.tryEmit("Validation error: ${res.detail}")
            }
        }
    }

    // ---- Settings ----

    fun saveBridgeBaseUrl(rawUrl: String) {
        viewModelScope.launch {
            container.settingsRepository.updateBridgeBaseUrl(rawUrl)
            _messages.tryEmit("Bridge URL saved.")
            checkBridgeHealth(autoRenderIfOnline = archive.value != null)
        }
    }

    fun updateCalibrationScale(scale: Float) {
        viewModelScope.launch {
            container.settingsRepository.updateCalibrationScale(scale)
        }
    }

    fun toggleCalibrationLock(locked: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.updateCalibrationLocked(locked)
        }
    }

    fun updatePhysicalProfile(profile: PhysicalCardProfile) {
        viewModelScope.launch {
            container.settingsRepository.updatePhysicalProfile(profile)
        }
    }

    fun updateEngineSplit(split: EngineSplit) {
        viewModelScope.launch {
            container.settingsRepository.updateEngineSplit(split)
            if (_bridgeHealth.value is BridgeHealthState.Online && archive.value != null) {
                requestBridgeRender()
            }
        }
    }

    fun toggleDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.updateDarkTheme(dark)
        }
    }

    // ---- Welcome Window & Set Lifecycle ----

    fun createNewSetFromWizard(
        title: String,
        gameId: String,
        styleOption: DiscoveredStyle,
        onOpened: () -> Unit,
    ) {
        viewModelScope.launch {
            container.settingsRepository.updateEngineSplit(styleOption.engineSplit)
            container.settingsRepository.updatePhysicalProfile(styleOption.physicalProfile)
            container.mseWorkspaceRepository.createNewBlankSet(
                title = title,
                game = gameId,
                stylesheet = styleOption.stylesheetId,
                discoveredStyle = styleOption,
            )
            _renderState.value = CardRenderState.Idle
            resolveOfflineBaseFrameForStyle(styleOption)
            onOpened()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun createNewSetFromFramework(
        title: String,
        framework: UniversalCardFramework,
        onOpened: () -> Unit,
    ) {
        viewModelScope.launch {
            val matchedStyle = framework.upstreamMseGameId?.let { gameId ->
                val styleId = framework.upstreamMseStyleId
                if (!styleId.isNullOrBlank()) {
                    container.mseTemplateCatalogRepository.findStyle(gameId, styleId)
                } else {
                    discoveredStyles.value.firstOrNull { it.gameId.equals(gameId, ignoreCase = true) }
                }
            }
            if (matchedStyle != null) {
                container.settingsRepository.updateEngineSplit(matchedStyle.engineSplit)
                container.settingsRepository.updatePhysicalProfile(matchedStyle.physicalProfile)
            } else {
                val profile = if (framework.cardWidthMm <= 60.0) {
                    PhysicalCardProfile.JAPANESE_59X86
                } else {
                    PhysicalCardProfile.STANDARD_63X88
                }
                container.settingsRepository.updatePhysicalProfile(profile)
            }
            container.mseWorkspaceRepository.createNewSetFromFramework(
                title = title,
                framework = framework,
                matchedDiscoveredStyle = matchedStyle,
            )
            _renderState.value = CardRenderState.Idle
            resolveOfflineBaseFrameForStyle(matchedStyle)
            _messages.tryEmit("Created '${title.ifBlank { framework.effectiveShortName }}' using ${framework.name} framework.")
            onOpened()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun createFanFictionSplitOffSet(
        officialSet: ComparableSet,
        splitOffTitle: String,
        splitOffKind: SplitOffKind,
        divergenceNotes: String,
        targetFramework: UniversalCardFramework? = null,
        onOpened: () -> Unit,
    ) {
        viewModelScope.launch {
            val resolvedFramework = targetFramework
                ?: container.universalFrameworkRepository.findBestMatchForGame(officialSet.gameId)
            val matchedStyle = resolvedFramework?.upstreamMseGameId?.let { gameId ->
                val styleId = resolvedFramework.upstreamMseStyleId
                if (!styleId.isNullOrBlank()) {
                    container.mseTemplateCatalogRepository.findStyle(gameId, styleId)
                } else {
                    discoveredStyles.value.firstOrNull { it.gameId.equals(gameId, ignoreCase = true) }
                }
            }
            container.mseWorkspaceRepository.createFanFictionSplitOffSet(
                officialSet = officialSet,
                splitOffTitle = splitOffTitle,
                splitOffKind = splitOffKind,
                divergenceNotes = divergenceNotes,
                targetFramework = resolvedFramework,
                discoveredStyle = matchedStyle,
            )
            _renderState.value = CardRenderState.Idle
            resolveOfflineBaseFrameForStyle(matchedStyle)
            _messages.tryEmit(
                "Created Split-Off Set '${splitOffTitle.ifBlank { officialSet.setName }}' with ${officialSet.cards.size}/${officialSet.cards.size} Canon Original cards preserved (art strictly blank)."
            )
            onOpened()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun generateAndValidateFrameworkScaffold(framework: UniversalCardFramework) {
        viewModelScope.launch {
            container.universalFrameworkRepository.generateCustomTemplateScaffold(framework)
                .onSuccess { generated ->
                    _messages.tryEmit(
                        "Generated custom_templates/${generated.scaffold.gameFolderName} & ${generated.scaffold.styleFolderName}."
                    )
                    if (_bridgeHealth.value is BridgeHealthState.Online) {
                        validateTemplateOnBridge(
                            templateId = generated.scaffold.styleFolderName,
                            styleSource = generated.scaffold.styleFileContent,
                        )
                    }
                }
                .onFailure { err ->
                    _messages.tryEmit("Scaffold generation failed: ${err.message}")
                }
        }
    }

    fun applyFrameworkToActiveSet(framework: UniversalCardFramework) {
        viewModelScope.launch {
            val matchedStyle = framework.upstreamMseGameId?.let { gameId ->
                val styleId = framework.upstreamMseStyleId
                if (!styleId.isNullOrBlank()) {
                    container.mseTemplateCatalogRepository.findStyle(gameId, styleId)
                } else {
                    discoveredStyles.value.firstOrNull { it.gameId.equals(gameId, ignoreCase = true) }
                }
            }
            container.mseWorkspaceRepository.applyDynamicFieldMapping(
                fieldMapping = emptyMap(),
                targetFramework = framework,
                discoveredStyle = matchedStyle,
            )
            resolveOfflineBaseFrameForStyle(matchedStyle)
            _messages.tryEmit("Switched workspace framework to ${framework.name}.")
        }
    }

    fun applyDynamicFieldMapping(
        fieldMapping: Map<String, String>,
        targetFramework: UniversalCardFramework?,
        discoveredStyle: DiscoveredStyle?,
    ) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.applyDynamicFieldMapping(
                fieldMapping = fieldMapping,
                targetFramework = targetFramework,
                discoveredStyle = discoveredStyle,
            )
            if (discoveredStyle != null) {
                resolveOfflineBaseFrameForStyle(discoveredStyle)
            }
            _messages.tryEmit("Applied field mapping (${fieldMapping.size} field(s) mapped; Canon Originals preserved).")
        }
    }

    fun mergeExternalMseSetUri(uri: Uri, importOrigin: CardOrigin = CardOrigin.ORIGINAL_OC_CARD) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.mergeExternalMseSetIntoActive(uri, importOrigin)
                .onSuccess { count ->
                    _messages.tryEmit("Merged $count card(s) as ${importOrigin.displayLabel} (Canon Originals untouched).")
                }
                .onFailure { err ->
                    _messages.tryEmit("Merge failed: ${err.message}")
                }
        }
    }

    fun copyOfficialReferenceBaseline(
        officialCard: ComparableCardRecord,
        origin: CardOrigin = CardOrigin.FAN_ALTERNATE_VARIANT,
    ) {
        viewModelScope.launch {
            val idx = container.mseWorkspaceRepository.copyOfficialReferenceBaselineCard(officialCard, origin)
            if (idx >= 0) {
                _messages.tryEmit("Copied '${officialCard.name}' metadata baseline to active set (art remains blank).")
            } else {
                _messages.tryEmit("Open or create a set first to copy a reference baseline card.")
            }
        }
    }

    fun branchAlternateLoreVariant(canonCardIndex: Int) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.branchAlternateLoreVariant(canonCardIndex)
            syncVariantSelectionToCurrentCard()
            _messages.tryEmit("Branched editable Fan Alternate Lore Variant alongside preserved Canon Original.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    /**
     * Jumps directly to a card in the active `.mse-set` from Telemetry Reports ("Edit Card in 1:1 Studio").
     * Returns true if found and selected.
     */
    fun selectCardByTelemetryMatch(cardId: String, cardName: String): Boolean {
        val cards = archive.value?.document?.cards ?: return false
        val normName = SetComparisonEngine.normalizeName(cardName)
        val idx = cards.indexOfFirst { card ->
            card.canonSourceId.equals(cardId, ignoreCase = true) ||
                card.field("passcode").equals(cardId, ignoreCase = true) ||
                card.field("register 21 digit").equals(cardId, ignoreCase = true) ||
                SetComparisonEngine.normalizeName(card.name) == normName
        }
        return if (idx >= 0) {
            selectCard(idx)
            true
        } else {
            false
        }
    }

    fun openMseSetUri(uri: Uri, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            val result = container.mseWorkspaceRepository.openSetFromUri(uri)
            result.onSuccess { loaded ->
                _renderState.value = CardRenderState.Idle
                val matched = container.mseTemplateCatalogRepository.findStyle(
                    gameId = loaded.document.game,
                    stylesheetId = loaded.document.stylesheet,
                )
                if (matched != null) {
                    container.mseWorkspaceRepository.applyDiscoveredStyleSpec(matched)
                }
                resolveOfflineBaseFrameForStyle(matched)
                _messages.tryEmit("Opened ${loaded.document.setTitle} (${loaded.document.cards.size} cards)")
                onOpened()
                if (_bridgeHealth.value is BridgeHealthState.Online) {
                    requestBridgeRender()
                }
            }.onFailure { err ->
                _messages.tryEmit("Could not open set: ${err.message}")
            }
        }
    }

    fun openRecentSet(record: RecentSetRecord, onOpened: () -> Unit) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.openRecentSet(record)
                .onSuccess { loaded ->
                    _renderState.value = CardRenderState.Idle
                    val matched = container.mseTemplateCatalogRepository.findStyle(
                        gameId = loaded.document.game,
                        stylesheetId = loaded.document.stylesheet,
                    )
                    if (matched != null) {
                        container.mseWorkspaceRepository.applyDiscoveredStyleSpec(matched)
                    }
                    resolveOfflineBaseFrameForStyle(matched)
                    onOpened()
                    if (_bridgeHealth.value is BridgeHealthState.Online) {
                        requestBridgeRender()
                    }
                }
                .onFailure { err ->
                    _messages.tryEmit("Could not open recent set: ${err.message}")
                }
        }
    }

    fun saveActiveSet(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.saveActiveSet()
                .onSuccess {
                    _messages.tryEmit("Saved ${it.document.setTitle}.")
                    onSaved()
                }
                .onFailure { err ->
                    _messages.tryEmit("Save failed: ${err.message}")
                }
        }
    }

    fun exportMseSetUri(uri: Uri) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.exportSetToUri(uri)
                .onSuccess { _messages.tryEmit("Saved .mse-set file.") }
                .onFailure { _messages.tryEmit("Save failed: ${it.message}") }
        }
    }

    fun closeActiveSet(onClosed: () -> Unit) {
        container.mseWorkspaceRepository.closeActiveSet()
        _renderState.value = CardRenderState.Idle
        _offlineFrame.value = null
        onClosed()
    }

    fun loadMseStyleUri(uri: Uri) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.loadStyleFromUri(uri)
                .onSuccess { parsed ->
                    _messages.tryEmit("Loaded style '${parsed.shortName}' (${parsed.cardWidthPx}×${parsed.cardHeightPx} @ ${parsed.cardDpi} DPI)")
                }
                .onFailure { _messages.tryEmit("Could not load .mse-style: ${it.message}") }
        }
    }

    fun switchStylesheet(option: DiscoveredStyle) {
        viewModelScope.launch {
            container.settingsRepository.updateEngineSplit(option.engineSplit)
            container.settingsRepository.updatePhysicalProfile(option.physicalProfile)
            container.mseWorkspaceRepository.switchStylesheet(option)
            resolveOfflineBaseFrameForStyle(option)
            _messages.tryEmit("Style set to ${option.displayName}.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    // ---- Undo / Redo & Card / Set Info / Keywords Editing ----

    fun undo() {
        if (container.mseWorkspaceRepository.undo()) {
            syncVariantSelectionToCurrentCard()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun redo() {
        if (container.mseWorkspaceRepository.redo()) {
            syncVariantSelectionToCurrentCard()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun selectCard(index: Int) {
        container.mseWorkspaceRepository.selectCard(index)
        syncVariantSelectionToCurrentCard()
        if (_bridgeHealth.value is BridgeHealthState.Online) {
            requestBridgeRender()
        }
    }

    fun addBlankCard() {
        viewModelScope.launch {
            container.mseWorkspaceRepository.addBlankCard()
            syncVariantSelectionToCurrentCard()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun duplicateCard(cardIndex: Int) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.duplicateCard(cardIndex)
            syncVariantSelectionToCurrentCard()
            _messages.tryEmit("Duplicated card.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun deleteCard(cardIndex: Int) {
        viewModelScope.launch {
            val resultIdx = container.mseWorkspaceRepository.deleteCard(cardIndex)
            if (resultIdx == MseWorkspaceRepository.CANON_PROTECTED_SENTINEL) {
                _messages.tryEmit(
                    "Canon Original cards are protected in Split-Off Sets. Tap 'Branch Alternate Lore Variant' to create an editable variant."
                )
                return@launch
            }
            syncVariantSelectionToCurrentCard()
            _messages.tryEmit("Deleted card.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun updateActiveCardFields(updates: Map<String, String?>) {
        viewModelScope.launch {
            val activeIdx = activeCardIndex.value
            if (container.mseWorkspaceRepository.isCardProtectedCanon(activeIdx)) {
                _messages.tryEmit(
                    "Canon Original card is read-only to preserve official lore. Use 'Branch Alternate Lore Variant' to customize."
                )
                return@launch
            }
            container.mseWorkspaceRepository.updateCardFields(
                cardIndex = activeIdx,
                updates = updates,
            )
            syncVariantSelectionToCurrentCard()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun embedCroppedCardArt(imageBytes: ByteArray) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.embedCardArt(
                cardIndex = activeCardIndex.value,
                imageBytes = imageBytes,
            )
            _messages.tryEmit("Artwork embedded in set.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun clearActiveCardArt() {
        viewModelScope.launch {
            container.mseWorkspaceRepository.clearCardArt(activeCardIndex.value)
            _messages.tryEmit("Artwork cleared.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun updateSetInfo(info: MseSetInfo) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.updateSetInfo(info)
        }
    }

    fun addKeyword(keyword: String, match: String, reminder: String, rules: String) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.addKeyword(keyword, match, reminder, rules)
            _messages.tryEmit("Added keyword '$keyword'.")
        }
    }

    fun updateKeyword(index: Int, keyword: String, match: String, reminder: String, rules: String) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.updateKeyword(index, keyword, match, reminder, rules)
            _messages.tryEmit("Updated keyword '$keyword'.")
        }
    }

    fun deleteKeyword(index: Int) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.deleteKeyword(index)
            _messages.tryEmit("Deleted keyword.")
        }
    }

    // ---- Fonts & Commercial License Flow ----

    fun applyReadyFont(entry: FontEntry) {
        viewModelScope.launch {
            container.settingsRepository.updateSelectedFontFamily(entry.familyName)
            container.mseWorkspaceRepository.applyFontOverride(entry.familyName)
            _messages.tryEmit("Applied '${entry.familyName}'.")
            if (_bridgeHealth.value is BridgeHealthState.Online && archive.value != null) {
                requestBridgeRender()
            }
        }
    }

    fun downloadAndApplyOpenFont(entry: FontEntry) {
        viewModelScope.launch {
            _isFontBusy.value = true
            val res = container.fontCatalogRepository.downloadOpenFont(entry)
            _isFontBusy.value = false
            res.onSuccess { outcome ->
                container.settingsRepository.updateSelectedFontFamily(entry.familyName)
                container.mseWorkspaceRepository.applyFontOverride(entry.familyName)
                _messages.tryEmit(outcome.message)
                if (_bridgeHealth.value is BridgeHealthState.Online && archive.value != null) {
                    requestBridgeRender()
                }
            }.onFailure { err ->
                _messages.tryEmit("Font download failed: ${err.message}")
            }
        }
    }

    fun importPurchasedFontUri(uri: Uri, expectedFamilyName: String?) {
        viewModelScope.launch {
            _isFontBusy.value = true
            val res = container.fontCatalogRepository.importPurchasedFontUri(uri, expectedFamilyName)
            _isFontBusy.value = false
            res.onSuccess { outcome ->
                val targetFamily = expectedFamilyName ?: outcome.importedFamilies.firstOrNull()
                if (!targetFamily.isNullOrBlank()) {
                    container.settingsRepository.updateSelectedFontFamily(targetFamily)
                    container.mseWorkspaceRepository.applyFontOverride(targetFamily)
                }
                _messages.tryEmit(outcome.message)
                if (_bridgeHealth.value is BridgeHealthState.Online && archive.value != null) {
                    requestBridgeRender()
                }
            }.onFailure { err ->
                _messages.tryEmit("Font import failed: ${err.message}")
            }
        }
    }

    // ---- Upstream Splits & GitHub ETag/Atom Watcher ----

    fun checkAllGitHubUpdates() {
        if (!container.networkConnectivityMonitor.checkIsOnlineNow()) {
            _messages.tryEmit(
                "Offline mode — original Magic Set Editor & DataEditorX update comparisons only run when officially online."
            )
            return
        }
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            val current = manifest.value
            val allSplits = current.engineSplits + current.packSplits
            val results = allSplits.map { split ->
                async {
                    val check = container.gitHubUpdateWatcher.checkSplit(
                        splitId = split.id,
                        owner = split.owner,
                        repo = split.repo,
                        branch = split.branch,
                        pinnedSha = split.pinnedSha,
                        previousEtag = split.lastEtag,
                    )
                    container.upstreamManifestRepository.recordCheckMetadata(split.id, check.etag)
                    split.id to check
                }
            } + current.openResourceConnectors.map { conn ->
                async {
                    val check = container.gitHubUpdateWatcher.checkSplit(
                        splitId = conn.id,
                        owner = conn.owner,
                        repo = conn.repo,
                        branch = conn.branch,
                        pinnedSha = conn.pinnedSha,
                        previousEtag = conn.lastEtag,
                    )
                    container.upstreamManifestRepository.recordCheckMetadata(conn.id, check.etag)
                    conn.id to check
                }
            }

            val resolved = results.awaitAll().toMap()
            _commitChecks.value = _commitChecks.value + resolved
            _isCheckingUpdates.value = false
            _messages.tryEmit("Checked ${resolved.size} upstream repositories.")
        }
    }

    fun triggerBridgeDeltaSync() {
        viewModelScope.launch {
            _isSyncingBridge.value = true
            val manifestJson = container.upstreamManifestRepository.exportManifestJson()
            when (val res = container.mseBridgeClient.triggerUpstreamSync(manifestJson)) {
                is BridgeResult.Success -> {
                    _messages.tryEmit("Delta sync complete (${res.data.syncedSplits.size} splits).")
                    refreshBridgeTemplatesAndFonts()
                }

                is BridgeResult.Offline -> _messages.tryEmit("Bridge offline — start Termux MSE Bridge to sync.")
                is BridgeResult.HttpError -> _messages.tryEmit("Bridge sync HTTP ${res.code}")
                is BridgeResult.ParseError -> _messages.tryEmit("Bridge sync error: ${res.detail}")
            }
            _isSyncingBridge.value = false
        }
    }

    fun toggleSplitEnabled(splitId: String, enabled: Boolean) {
        viewModelScope.launch { container.upstreamManifestRepository.toggleSplitEnabled(splitId, enabled) }
    }

    fun updateSplitBranch(splitId: String, branch: String) {
        viewModelScope.launch {
            container.upstreamManifestRepository.updateSplitBranch(splitId, branch)
            _messages.tryEmit("Branch updated to '$branch'.")
        }
    }

    fun pinSplitSha(splitId: String, sha: String) {
        viewModelScope.launch {
            container.upstreamManifestRepository.pinSplitSha(splitId, sha)
            _messages.tryEmit("Pinned ${sha.take(7)}.")
        }
    }

    fun rollbackSplitSha(splitId: String) {
        viewModelScope.launch {
            container.upstreamManifestRepository.rollbackSplitSha(splitId)
            _messages.tryEmit("Rolled back pinned commit.")
        }
    }

    fun updateWebhookRelayUrl(url: String) {
        viewModelScope.launch {
            container.upstreamManifestRepository.updateWebhookRelayUrl(url)
            _messages.tryEmit("Saved webhook relay URL.")
        }
    }

    fun addOpenResourceConnector(
        gameName: String,
        owner: String,
        repo: String,
        branch: String,
        resourceType: String,
    ) {
        viewModelScope.launch {
            container.upstreamManifestRepository.addOpenResourceConnector(
                gameName = gameName,
                owner = owner,
                repo = repo,
                branch = branch,
                resourceType = resourceType,
            )
            _messages.tryEmit("Added connector for $gameName.")
        }
    }

    fun removeOpenResourceConnector(id: String) {
        viewModelScope.launch {
            container.upstreamManifestRepository.removeOpenResourceConnector(id)
            _messages.tryEmit("Removed connector.")
        }
    }

    fun exportManifestJson(): String = container.upstreamManifestRepository.exportManifestJson()

    fun saveRawManifestJson(rawJson: String) {
        viewModelScope.launch {
            container.upstreamManifestRepository.importRawManifestJson(rawJson)
                .onSuccess { _messages.tryEmit("Saved upstream_manifest.json.") }
                .onFailure { _messages.tryEmit("Invalid JSON: ${it.message}") }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StudioViewModel(container) as T
                }
            }
    }
}
