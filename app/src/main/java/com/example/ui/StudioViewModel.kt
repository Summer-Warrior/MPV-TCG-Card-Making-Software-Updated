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
import com.example.data.mse.MseHitbox
import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseSetInfo
import com.example.data.mse.MseStyleOption
import com.example.data.mse.MseStyleSpec
import com.example.data.mse.RecentSetRecord
import com.example.data.settings.EngineSplit
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.data.upstream.GitHubCommitCheckResult
import com.example.data.upstream.UpstreamManifest
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val activeCardIndex: StateFlow<Int> = container.mseWorkspaceRepository.activeCardIndex
    val styleSpec: StateFlow<MseStyleSpec> = container.mseWorkspaceRepository.styleSpec
    val recentSets: StateFlow<List<RecentSetRecord>> = container.mseWorkspaceRepository.recentSets
    val hasUnsavedChanges: StateFlow<Boolean> = container.mseWorkspaceRepository.hasUnsavedChanges

    val fonts: StateFlow<List<FontEntry>> = container.fontCatalogRepository.unifiedFontsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val manifest: StateFlow<UpstreamManifest> = container.upstreamManifestRepository.manifest

    private val _bridgeHealth = MutableStateFlow<BridgeHealthState>(BridgeHealthState.Checking)
    val bridgeHealth: StateFlow<BridgeHealthState> = _bridgeHealth.asStateFlow()

    private val _renderState = MutableStateFlow<CardRenderState>(CardRenderState.Idle)
    val renderState: StateFlow<CardRenderState> = _renderState.asStateFlow()

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
        checkBridgeHealth(autoRenderIfOnline = false)
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
        styleOption: MseStyleOption,
        onOpened: () -> Unit,
    ) {
        viewModelScope.launch {
            container.settingsRepository.updateEngineSplit(styleOption.engineSplit)
            container.settingsRepository.updatePhysicalProfile(styleOption.physicalProfile)
            container.mseWorkspaceRepository.createNewBlankSet(
                title = title,
                game = gameId,
                stylesheet = styleOption.stylesheetId,
                styleOption = styleOption,
            )
            _renderState.value = CardRenderState.Idle
            onOpened()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun openMseSetUri(uri: Uri, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            val result = container.mseWorkspaceRepository.openSetFromUri(uri)
            result.onSuccess { loaded ->
                _renderState.value = CardRenderState.Idle
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
                .onSuccess {
                    _renderState.value = CardRenderState.Idle
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

    fun switchStylesheet(option: MseStyleOption) {
        viewModelScope.launch {
            container.settingsRepository.updateEngineSplit(option.engineSplit)
            container.settingsRepository.updatePhysicalProfile(option.physicalProfile)
            container.mseWorkspaceRepository.switchStylesheet(option)
            _messages.tryEmit("Style set to ${option.displayName}.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    // ---- Cards, Set Info & Keywords Editing ----

    fun selectCard(index: Int) {
        container.mseWorkspaceRepository.selectCard(index)
        if (_bridgeHealth.value is BridgeHealthState.Online) {
            requestBridgeRender()
        }
    }

    fun addBlankCard() {
        viewModelScope.launch {
            container.mseWorkspaceRepository.addBlankCard()
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun duplicateCard(cardIndex: Int) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.duplicateCard(cardIndex)
            _messages.tryEmit("Duplicated card.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun deleteCard(cardIndex: Int) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.deleteCard(cardIndex)
            _messages.tryEmit("Deleted card.")
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun updateActiveCardField(fieldKey: String, newValue: String?) {
        viewModelScope.launch {
            container.mseWorkspaceRepository.updateCardField(
                cardIndex = activeCardIndex.value,
                fieldKey = fieldKey,
                newValue = newValue,
            )
            if (_bridgeHealth.value is BridgeHealthState.Online) {
                requestBridgeRender()
            }
        }
    }

    fun updateActiveCardFields(updates: Map<String, String?>) {
        viewModelScope.launch {
            updates.forEach { (k, v) ->
                container.mseWorkspaceRepository.updateCardField(
                    cardIndex = activeCardIndex.value,
                    fieldKey = k,
                    newValue = v,
                )
            }
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

    fun rawSetText(): String = container.mseWorkspaceRepository.rawSetTextPreview()

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
