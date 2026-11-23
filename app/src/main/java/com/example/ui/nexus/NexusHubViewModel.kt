package com.example.ui.nexus

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.nexus.GitHubCommitResult
import com.example.data.nexus.HubHandshakeResult
import com.example.data.nexus.NexusHubBridgeService
import com.example.data.nexus.NexusHubRepository
import com.example.domain.nexus.CardCodeValidator
import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.CardValidationReport
import com.example.domain.nexus.MutualLinkStatus
import com.example.domain.nexus.NexusCardPackExport
import com.example.domain.nexus.NexusClientCodestringIndex
import com.example.domain.nexus.NexusClientDefinition
import com.example.domain.nexus.NexusCodedCard
import com.example.domain.nexus.NexusHubChangelogDiff
import com.example.domain.nexus.NexusRepoLinkManifest
import com.example.domain.nexus.NexusSchemaValidator
import com.example.domain.nexus.SchemaValidationResult
import com.example.domain.nexus.TwentyColorPieCatalog
import com.example.domain.nexus.UltimateTcgManualFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class NexusHubUiState(
    val manifest: NexusRepoLinkManifest = NexusRepoLinkManifest(),
    val appliedIndex: NexusClientCodestringIndex = NexusHubRepository.buildSeedClientIndex(),
    val linkStatus: MutualLinkStatus = MutualLinkStatus.UNLINKED,
    val hasCheckedHubLink: Boolean = false,
    val lastHandshake: HubHandshakeResult? = null,
    val pendingPulledIndex: NexusClientCodestringIndex? = null,
    val pendingDiff: NexusHubChangelogDiff? = null,
    val isCheckingHub: Boolean = false,
    val isCommitting: Boolean = false,
    val lastCommitSummary: String? = null,
    // Fine-Grained PAT: session-only by default, opt-in persistent storage
    val fineGrainedPat: String = "",
    val rememberPatOptIn: Boolean = false,
    // Card Coder state — strictly ZERO default 20-Color Pie colors selected on load
    val selectedClientId: String = "ultimate-tcg-wip",
    val editorMode: CardEditorMode = CardEditorMode.MATCH_GAMEPLAY,
    val cardId: String = "",
    val register21Digit: String = "",
    val cardName: String = "",
    val cardType: String = "",
    val rulesText: String = "",
    val selectedColorPiePips: List<String> = emptyList(),
    val selectedFlavorFilters: Set<Int> = emptySet(),
    val selectedTemporalEffects: Set<String> = emptySet(),
    val codingVariables: Map<String, String> = emptyMap(),
    val ultimateTcgManual: UltimateTcgManualFields = UltimateTcgManualFields(),
    val packName: String = "Custom Nexus Pack",
    val savedCodedCards: List<NexusCodedCard> = emptyList(),
    val validationReport: CardValidationReport? = null,
    val generatedLuaPreview: String? = null,
) {
    val selectedClient: NexusClientDefinition?
        get() = appliedIndex.clients.firstOrNull { it.id == selectedClientId }

    val isMutualLinkVerified: Boolean
        get() = linkStatus == MutualLinkStatus.LINKED_AND_CURRENT ||
            linkStatus == MutualLinkStatus.LINKED_OUT_OF_SYNC
}

class NexusHubViewModel(
    private val repository: NexusHubRepository,
    private val bridgeService: NexusHubBridgeService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NexusHubUiState())
    val uiState: StateFlow<NexusHubUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val manifest = repository.loadManifest()
            val index = repository.loadAppliedIndex()
            val savedCards = repository.loadSavedCodedCards()
            val (rememberPat, storedPat) = repository.loadOptInPatPreference()
            val firstWritableClientId = index.clients.firstOrNull { !it.upstreamSourceLock }?.id
                ?: "ultimate-tcg-wip"

            _uiState.update {
                it.copy(
                    manifest = manifest,
                    appliedIndex = index,
                    selectedClientId = firstWritableClientId,
                    savedCodedCards = savedCards,
                    rememberPatOptIn = rememberPat,
                    fineGrainedPat = storedPat,
                )
            }
            recomputeValidation()
            // Automatically run ETag-cached mutual link check on app open (offline-safe)
            verifyMutualLinkAndCheckHub(silent = true)
        }
    }

    fun updateManifest(updated: NexusRepoLinkManifest) {
        _uiState.update { it.copy(manifest = updated) }
        viewModelScope.launch {
            when (val res = NexusSchemaValidator.validateRepoLinkManifest(updated)) {
                is SchemaValidationResult.Valid -> {
                    repository.saveManifest(updated)
                    _messages.tryEmit("Saved nexus-repo-link.json configuration.")
                }
                is SchemaValidationResult.Invalid -> {
                    _messages.tryEmit("Manifest warning: ${res.errors.first()}")
                }
            }
        }
    }

    fun updatePat(pat: String, rememberOptIn: Boolean) {
        _uiState.update { it.copy(fineGrainedPat = pat, rememberPatOptIn = rememberOptIn) }
        viewModelScope.launch {
            repository.updateOptInPatPreference(rememberOptIn, pat)
        }
    }

    fun verifyMutualLinkAndCheckHub(silent: Boolean = false) {
        val current = _uiState.value
        if (current.isCheckingHub) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingHub = true) }
            val (cachedEtags, cachedBodies) = repository.loadHubEtagCache()
            val result = bridgeService.verifyMutualLinkAndPullIndex(
                localManifest = current.manifest,
                currentAppliedIndex = current.appliedIndex,
                fineGrainedPat = current.fineGrainedPat.ifBlank { null },
                cachedEtags = cachedEtags,
                cachedBodies = cachedBodies,
            )
            repository.saveHubEtagCache(result.updatedEtags, result.updatedBodies)
            _uiState.update { state ->
                state.copy(
                    isCheckingHub = false,
                    hasCheckedHubLink = true,
                    linkStatus = result.status,
                    lastHandshake = result,
                    pendingPulledIndex = result.pulledIndex?.takeIf { result.changelogDiff?.hasChanges == true },
                    pendingDiff = result.changelogDiff?.takeIf { it.hasChanges },
                )
            }
            if (!silent) {
                _messages.tryEmit("Hub Link Status: ${result.status.wireValue}")
            }
        }
    }

    fun applyPendingHubIndexUpdate() {
        val incoming = _uiState.value.pendingPulledIndex ?: return
        viewModelScope.launch {
            repository.saveAppliedIndex(incoming)
            _uiState.update { state ->
                val validSelected = incoming.clients.any { it.id == state.selectedClientId && !it.upstreamSourceLock }
                val nextClientId = if (validSelected) {
                    state.selectedClientId
                } else {
                    incoming.clients.firstOrNull { !it.upstreamSourceLock }?.id ?: state.selectedClientId
                }
                val nextStatus = if (state.linkStatus == MutualLinkStatus.LINKED_OUT_OF_SYNC) {
                    MutualLinkStatus.LINKED_AND_CURRENT
                } else {
                    state.linkStatus
                }
                state.copy(
                    appliedIndex = incoming,
                    selectedClientId = nextClientId,
                    pendingPulledIndex = null,
                    pendingDiff = null,
                    linkStatus = nextStatus,
                )
            }
            recomputeValidation()
            _messages.tryEmit("Applied Nexus Hub client index rev ${incoming.indexRevision}.")
        }
    }

    fun dismissPendingHubDiff() {
        _uiState.update { it.copy(pendingPulledIndex = null, pendingDiff = null) }
    }

    fun selectClient(clientId: String) {
        val client = _uiState.value.appliedIndex.clients.firstOrNull { it.id == clientId } ?: return
        if (client.upstreamSourceLock) {
            _messages.tryEmit(
                "Locked-Client Firewall: '${client.name}' is read-only. Tap 'Target Non-Locked 21-Digit Fork Instead'."
            )
            return
        }
        _uiState.update { it.copy(selectedClientId = client.id) }
        recomputeValidation()
    }

    fun redirectFromLockedClientToFork(lockedClient: NexusClientDefinition) {
        val fork = CardCodeValidator.resolveNonLockedFork(
            lockedClient = lockedClient,
            allClients = _uiState.value.appliedIndex.clients,
        )
        if (fork == null) {
            _messages.tryEmit("No non-locked 21-digit fork client is registered in the active index.")
            return
        }
        _uiState.update { it.copy(selectedClientId = fork.id) }
        recomputeValidation()
        _messages.tryEmit("Switched target to non-locked fork: ${fork.name}")
    }

    fun setEditorMode(mode: CardEditorMode) {
        _uiState.update { state ->
            val cleanedFilters = if (mode == CardEditorMode.MATCH_GAMEPLAY) {
                state.selectedFlavorFilters - CardCodeValidator.TIME_TRAVEL_FORBIDDEN_FILTERS
            } else {
                state.selectedFlavorFilters
            }
            state.copy(
                editorMode = mode,
                selectedFlavorFilters = cleanedFilters,
            )
        }
        recomputeValidation()
    }

    fun updateCardFields(
        cardId: String = _uiState.value.cardId,
        register21Digit: String = _uiState.value.register21Digit,
        cardName: String = _uiState.value.cardName,
        cardType: String = _uiState.value.cardType,
        rulesText: String = _uiState.value.rulesText,
        packName: String = _uiState.value.packName,
    ) {
        _uiState.update {
            it.copy(
                cardId = cardId,
                register21Digit = register21Digit,
                cardName = cardName,
                cardType = cardType,
                rulesText = rulesText,
                packName = packName,
            )
        }
        recomputeValidation()
    }

    fun prefillFromActiveMseCard(name: String, type: String, rulesText: String) {
        _uiState.update {
            it.copy(
                cardName = name,
                cardType = type,
                rulesText = rulesText,
            )
        }
        recomputeValidation()
        _messages.tryEmit("Loaded active set card text into Card Coder (art remains null).")
    }

    fun assignUuidCardId() {
        updateCardFields(cardId = UUID.randomUUID().toString())
    }

    fun incrementRegister21(offset: BigInteger) {
        val currentBase = _uiState.value.register21Digit.trim()
            .ifBlank { _uiState.value.cardId.trim().takeIf { CardCodeValidator.NUMERIC_21_DIGIT_REGEX.matches(it) } ?: "100000000000000000000" }
        val next = CardCodeValidator.computeBigIntRegisterOffset(currentBase, offset)
        if (next == null) {
            _messages.tryEmit("21-digit register overflow or non-numeric base (max 999999999999999999999).")
            return
        }
        updateCardFields(
            cardId = if (_uiState.value.cardId.isBlank()) next else _uiState.value.cardId,
            register21Digit = next,
        )
    }

    fun appendColorPiePip(pipCode: String) {
        if (!CardCodeValidator.isValidColorPiePip(pipCode)) {
            _messages.tryEmit("Rejected invalid 20-Color Pie pip: $pipCode")
            return
        }
        _uiState.update { it.copy(selectedColorPiePips = it.selectedColorPiePips + pipCode) }
        recomputeValidation()
    }

    fun removeColorPiePipAt(index: Int) {
        _uiState.update { state ->
            if (index !in state.selectedColorPiePips.indices) return@update state
            state.copy(selectedColorPiePips = state.selectedColorPiePips.filterIndexed { i, _ -> i != index })
        }
        recomputeValidation()
    }

    fun clearAllColorPiePips() {
        _uiState.update { it.copy(selectedColorPiePips = emptyList()) }
        recomputeValidation()
    }

    fun applyValueStateToLastPip(stateSuffix: String) {
        _uiState.update { state ->
            val current = state.selectedColorPiePips
            if (current.isEmpty()) return@update state
            val last = current.last()
            if (last == TwentyColorPieCatalog.colorlessPip.code || last in TwentyColorPieCatalog.omegaCodes) {
                return@update state
            }
            val updated = TwentyColorPieCatalog.applyValueState(last, stateSuffix)
            if (!CardCodeValidator.isValidColorPiePip(updated)) return@update state
            state.copy(selectedColorPiePips = current.dropLast(1) + updated)
        }
        recomputeValidation()
    }

    fun toggleFlavorFilter(filterId: Int) {
        val state = _uiState.value
        if (state.editorMode == CardEditorMode.MATCH_GAMEPLAY && filterId in CardCodeValidator.TIME_TRAVEL_FORBIDDEN_FILTERS) {
            _messages.tryEmit(
                "Blocked: Filter $filterId is a Time-Travel flavor flag forbidden on Match Gameplay cards. Switch to Avatar Profile / Memorial Forge Dossier mode."
            )
            return
        }
        _uiState.update { s ->
            val next = if (filterId in s.selectedFlavorFilters) {
                s.selectedFlavorFilters - filterId
            } else {
                s.selectedFlavorFilters + filterId
            }
            s.copy(selectedFlavorFilters = next)
        }
        recomputeValidation()
    }

    fun toggleTemporalEffect(effectName: String) {
        _uiState.update { s ->
            val next = if (effectName in s.selectedTemporalEffects) {
                s.selectedTemporalEffects - effectName
            } else {
                s.selectedTemporalEffects + effectName
            }
            s.copy(selectedTemporalEffects = next)
        }
        recomputeValidation()
    }

    fun putCodingVariable(key: String, value: String) {
        val cleanKey = key.trim()
        val cleanVal = value.trim()
        if (cleanKey.isEmpty()) return
        if (!CardCodeValidator.isValid21DigitCodingVariable(cleanKey) ||
            !CardCodeValidator.isValid21DigitCodingVariable(cleanVal)
        ) {
            _messages.tryEmit("Rejected: 22+ consecutive digits violate 21-digit register coding rules.")
            return
        }
        _uiState.update { it.copy(codingVariables = it.codingVariables + (cleanKey to cleanVal)) }
        recomputeValidation()
    }

    fun removeCodingVariable(key: String) {
        _uiState.update { it.copy(codingVariables = it.codingVariables - key) }
        recomputeValidation()
    }

    fun updateUltimateTcgManual(manual: UltimateTcgManualFields) {
        _uiState.update { it.copy(ultimateTcgManual = manual) }
    }

    fun stageCurrentCardIntoPack() {
        val report = recomputeValidation()
        if (!report.isValid) {
            _messages.tryEmit("Fix blocking validation errors before staging card.")
            return
        }
        val card = buildCurrentCodedCard()
        viewModelScope.launch {
            val nextList = _uiState.value.savedCodedCards.filterNot { it.cardId == card.cardId } + card
            repository.saveCodedCards(nextList)
            _uiState.update { it.copy(savedCodedCards = nextList) }
            _messages.tryEmit("Staged coded card '${card.cardName}' (${nextList.size} card(s) in pack).")
        }
    }

    fun removeStagedCard(cardId: String) {
        viewModelScope.launch {
            val nextList = _uiState.value.savedCodedCards.filterNot { it.cardId == cardId }
            repository.saveCodedCards(nextList)
            _uiState.update { it.copy(savedCodedCards = nextList) }
        }
    }

    fun buildCurrentExportPackJson(): String? {
        val state = _uiState.value
        val client = state.selectedClient ?: return null
        if (client.upstreamSourceLock) {
            _messages.tryEmit("Locked-Client Firewall: cannot export pack for locked upstream client.")
            return null
        }
        val cardsForPack = state.savedCodedCards.filter { it.targetClientId == client.id }.ifEmpty {
            val report = recomputeValidation()
            if (!report.isValid) return null
            listOf(buildCurrentCodedCard())
        }
        val packSlug = sanitizeFileSlug(state.packName.ifBlank { client.id })
        val pack = NexusCardPackExport(
            bridgeSchemaVersion = state.manifest.bridgeSchemaVersion,
            sourceRepo = state.manifest.thisRepo,
            targetHubRepo = state.manifest.linkedHubRepo,
            targetClientId = client.id,
            upstreamSourceLock = false,
            packId = packSlug,
            packName = state.packName.ifBlank { "${client.name} Pack" },
            exportedAt = currentUtcIso(),
            cards = cardsForPack,
        )
        return repository.serializeCardPackJson(pack)
    }

    fun buildManifestJson(): String = repository.serializeManifestJson(_uiState.value.manifest)

    fun buildWorkflowYaml(): String = bridgeService.generateWorkflowYaml(_uiState.value.manifest)

    fun writeUtf8ToUri(context: Context, uri: Uri, content: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            withContext(Dispatchers.Main) {
                _messages.tryEmit(if (ok) "Saved $label successfully." else "Failed to write $label.")
            }
        }
    }

    /**
     * Option A — Direct Commit to linked Nexus Master Hub (strictly gated on verified mutual link).
     */
    fun commitCardPackToLinkedHub() {
        val state = _uiState.value
        if (!state.isMutualLinkVerified) {
            _messages.tryEmit("Direct Commit requires a verified mutual repo link (LINKED_AND_CURRENT or LINKED_OUT_OF_SYNC).")
            return
        }
        val client = state.selectedClient
        if (client == null || client.upstreamSourceLock) {
            _messages.tryEmit("Locked-Client Firewall: cannot commit packs for locked upstream clients.")
            return
        }
        val packJson = buildCurrentExportPackJson()
        if (packJson == null) {
            _messages.tryEmit("Fix card validation issues before committing pack.")
            return
        }
        val pat = state.fineGrainedPat.trim()
        if (pat.isEmpty()) {
            _messages.tryEmit("Enter a Fine-Grained GitHub PAT (Contents: Read & Write) or use Option B zero-token export.")
            return
        }

        val packSlug = sanitizeFileSlug(state.packName.ifBlank { client.id })
        val packsDir = state.manifest.exportedPacksDir.trim().trimEnd('/')
        val targetPath = "$packsDir/$packSlug.json"

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true, lastCommitSummary = null) }
            val result = bridgeService.commitFileToGitHub(
                repoSlug = state.manifest.linkedHubRepo,
                branch = state.manifest.linkedHubBranch,
                repoFilePath = targetPath,
                contentUtf8 = packJson,
                commitMessage = "feat(nexus-bridge): export card pack $packSlug for ${client.id}",
                fineGrainedPat = pat,
            )
            handleCommitResult(result, targetPath)
        }
    }

    fun commitRepoLinkManifestToThisRepo() {
        val state = _uiState.value
        val pat = state.fineGrainedPat.trim()
        if (pat.isEmpty()) {
            _messages.tryEmit("Enter a Fine-Grained GitHub PAT (Contents: Read & Write) to commit nexus-repo-link.json.")
            return
        }
        val manifestJson = buildManifestJson()
        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true, lastCommitSummary = null) }
            val result = bridgeService.commitFileToGitHub(
                repoSlug = state.manifest.thisRepo,
                branch = state.manifest.thisBranch,
                repoFilePath = "nexus-repo-link.json",
                contentUtf8 = manifestJson,
                commitMessage = "chore(nexus-bridge): update nexus-repo-link.json",
                fineGrainedPat = pat,
            )
            handleCommitResult(result, "${state.manifest.thisRepo}/nexus-repo-link.json")
        }
    }

    private fun handleCommitResult(result: GitHubCommitResult, targetLabel: String) {
        when (result) {
            is GitHubCommitResult.Success -> {
                val recoveryNote = if (result.recoveredFromConflict) " (auto-recovered 409/422 SHA conflict)" else ""
                val msg = "Committed $targetLabel (${result.commitSha.take(8)})$recoveryNote"
                _uiState.update { it.copy(isCommitting = false, lastCommitSummary = msg) }
                _messages.tryEmit(msg)
            }
            is GitHubCommitResult.Failure -> {
                _uiState.update { it.copy(isCommitting = false, lastCommitSummary = result.message) }
                _messages.tryEmit(result.message)
            }
        }
    }

    private fun buildCurrentCodedCard(): NexusCodedCard {
        val state = _uiState.value
        val client = state.selectedClient
        val baseCard = NexusCodedCard(
            cardId = state.cardId.trim(),
            register21Digit = state.register21Digit.trim().ifBlank { null },
            targetClientId = state.selectedClientId,
            cardName = state.cardName.trim(),
            cardType = state.cardType.trim(),
            rulesText = state.rulesText.trim(),
            colorPiePips = state.selectedColorPiePips,
            editorMode = state.editorMode.wireValue,
            allowTimeTravelFlagsOnMatchCards = false,
            flavorFilters = state.selectedFlavorFilters.sorted(),
            temporalEffects = state.selectedTemporalEffects.sorted(),
            codingVariables = state.codingVariables,
            ultimateTcgManual = state.ultimateTcgManual,
            image = null, // STRICTLY NO DEFAULT CARD ART
        )
        val lua = if (client != null && !client.upstreamSourceLock) {
            CardCodeValidator.generateLuaScript(baseCard, client)
        } else {
            null
        }
        return baseCard.copy(generatedLua = lua)
    }

    private fun recomputeValidation(): CardValidationReport {
        val state = _uiState.value
        val client = state.selectedClient
        val card = buildCurrentCodedCard()
        val report = CardCodeValidator.validateCard(card, client)
        _uiState.update {
            it.copy(
                validationReport = report,
                generatedLuaPreview = card.generatedLua,
            )
        }
        return report
    }

    private fun sanitizeFileSlug(raw: String): String =
        raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .ifBlank { "custom-pack" }

    private fun currentUtcIso(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date())
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NexusHubViewModel(
                        repository = container.nexusHubRepository,
                        bridgeService = container.nexusHubBridgeService,
                    ) as T
                }
            }
    }
}
