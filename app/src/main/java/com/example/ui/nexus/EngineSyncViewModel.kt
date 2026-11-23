package com.example.ui.nexus

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.nexus.AdminAlertLocalState
import com.example.data.nexus.EngineSyncIngestionService
import com.example.data.nexus.EngineSyncRepository
import com.example.data.nexus.NotificationChannelResult
import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.CardFrameReviewAuditResult
import com.example.domain.nexus.ConsentStatus
import com.example.domain.nexus.DualConsentRecord
import com.example.domain.nexus.EngineCommitDiff
import com.example.domain.nexus.EngineCompatibilityAuditor
import com.example.domain.nexus.EngineHealthSeverity
import com.example.domain.nexus.EngineHealthStatusPayload
import com.example.domain.nexus.EngineSyncSnapshot
import com.example.domain.nexus.HandshakeContract
import com.example.domain.nexus.MasterHubAdminGate
import com.example.domain.nexus.NexusAppManifest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class EngineSyncUiState(
    val appManifest: NexusAppManifest = NexusAppManifest(),
    val contract: HandshakeContract = HandshakeContract(),
    val baselineSnapshot: EngineSyncSnapshot = EngineSyncSnapshot(),
    val stagedSnapshot: EngineSyncSnapshot? = null,
    val stagedDiff: EngineCommitDiff? = null,
    val healthStatus: EngineHealthStatusPayload = EngineHealthStatusPayload(
        alertId = "baseline-healthy",
        severity = EngineHealthSeverity.HEALTHY.wireValue,
    ),
    val adminLocalState: AdminAlertLocalState = AdminAlertLocalState(),
    val isSyncingEngine: Boolean = false,
    val lastSyncMessage: String = "Baseline loaded. Tap Sync to read live Tcg-game-engine-test.",
    val showAdminBlockingModal: Boolean = false,
    val lastNotificationResults: List<NotificationChannelResult> = emptyList(),
    // Card Frame Reviewer state — strictly ZERO default 20-Color Pie colors selected
    val reviewBaseVariantId: String = "EDOPRO_CUSTOM_FORK",
    val reviewCardName: String = "",
    val reviewCardType: String = "",
    val reviewRulesText: String = "",
    val reviewCodeString: String = "",
    val reviewStatText: String = "",
    val reviewEffectType: String = "ACTIVATED",
    val reviewEditorMode: CardEditorMode = CardEditorMode.MATCH_GAMEPLAY,
    val reviewFlavorFilters: Set<Int> = emptySet(),
    val reviewSelectedPips: List<String> = emptyList(),
    val reviewPipFilterQuery: String = "",
    val reviewFrameWidthPx: Int = 375,
    val reviewFrameHeightPx: Int = 523,
    val reviewAuditResult: CardFrameReviewAuditResult? = null,
) {
    val activeSnapshotForReview: EngineSyncSnapshot
        get() = stagedSnapshot ?: baselineSnapshot

    val canFinalizeStagedUpdate: Boolean
        get() {
            val stagedSha = stagedSnapshot?.commitSha ?: return false
            return EngineCompatibilityAuditor.canFinalizeEngineSync(contract, stagedSha)
        }

    val isCurrentUserAdmin: Boolean
        get() = EngineCompatibilityAuditor.isMasterHubAdmin(
            gate = appManifest.masterHubAdminGate,
            userRoleIds = adminLocalState.currentUserRoleIds.toSet(),
            userId = adminLocalState.currentUserId,
            userEmail = adminLocalState.currentUserEmail,
        )
}

class EngineSyncViewModel(
    private val repository: EngineSyncRepository,
    private val ingestionService: EngineSyncIngestionService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EngineSyncUiState())
    val uiState: StateFlow<EngineSyncUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val appManifest = repository.loadAppManifest()
            val contract = repository.loadContract()
            val baseline = repository.loadBaselineSnapshot()
            val lastHealth = repository.loadLastHealthStatus()
            val adminState = repository.loadAdminAlertState()

            _uiState.update { state ->
                state.copy(
                    appManifest = appManifest,
                    contract = contract,
                    baselineSnapshot = baseline,
                    healthStatus = lastHealth ?: state.healthStatus,
                    adminLocalState = adminState,
                    reviewBaseVariantId = baseline.baseVariants.firstOrNull()?.baseVariantId ?: "EDOPRO_CUSTOM_FORK",
                )
            }
            recomputeFrameReview()
            // Run live engine compatibility check automatically on launch unless paused
            if (!adminState.syncPaused) {
                syncLiveGameEngine(fineGrainedPat = null)
            }
        }
    }

    fun syncLiveGameEngine(fineGrainedPat: String?) {
        val current = _uiState.value
        if (current.isSyncingEngine) return
        if (current.adminLocalState.syncPaused) {
            _messages.tryEmit("Engine sync is paused by Administrator. Resume sync to poll Tcg-game-engine-test.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingEngine = true) }
            val result = ingestionService.ingestLiveEngineAndAudit(
                contract = current.contract,
                baselineSnapshot = current.baselineSnapshot,
                engineBranch = current.baselineSnapshot.engineBranch,
                cachedEtags = current.adminLocalState.etags,
                fineGrainedPat = fineGrainedPat,
            )

            // Merge remote gameEngineConsent if published in Tcg-game-engine-test
            val mergedContract = if (result.remoteEngineContract != null) {
                current.contract.copy(gameEngineConsent = result.remoteEngineContract.gameEngineConsent)
            } else {
                current.contract
            }
            repository.saveContract(mergedContract)
            repository.saveHealthStatus(result.healthStatus)

            val updatedAdminState = current.adminLocalState.copy(etags = result.updatedEtags)
            repository.saveAdminAlertState(updatedAdminState)

            val hasStagedChanges = result.diff.hasDiff && !result.usedOfflineFallback
            _uiState.update { state ->
                state.copy(
                    isSyncingEngine = false,
                    contract = mergedContract,
                    stagedSnapshot = if (hasStagedChanges) result.stagedSnapshot else state.stagedSnapshot,
                    stagedDiff = if (hasStagedChanges) result.diff else state.stagedDiff,
                    healthStatus = result.healthStatus,
                    adminLocalState = updatedAdminState,
                    lastSyncMessage = result.statusMessage,
                )
            }
            recomputeFrameReview()

            // Trigger deduplicated Tri-Channel Admin Notifications + Master Hub Handshake on DEGRADED/BREAKING
            val severity = result.healthStatus.severity
            if (severity == EngineHealthSeverity.DEGRADED.wireValue ||
                severity == EngineHealthSeverity.BREAKING_INCOMPATIBLE.wireValue
            ) {
                triggerDeduplicatedAdminAlertPipeline(
                    healthStatus = result.healthStatus,
                    fineGrainedPat = fineGrainedPat,
                )
            }
        }
    }

    private suspend fun triggerDeduplicatedAdminAlertPipeline(
        healthStatus: EngineHealthStatusPayload,
        fineGrainedPat: String?,
    ) {
        val state = _uiState.value
        val failingSha = healthStatus.failingCommitSha
        val alreadyAlerted = failingSha.isNotBlank() && failingSha in state.adminLocalState.alertedCommitShas
        if (alreadyAlerted) {
            return
        }

        val results = mutableListOf<NotificationChannelResult>()

        // Channel 1: Master Hub Notification Handshake (repository_dispatch) when PAT is provided
        if (!fineGrainedPat.isNullOrBlank()) {
            results += ingestionService.dispatchMasterHubBreakingAlert(
                masterHubRepo = state.appManifest.linkedMasterHub,
                healthStatus = healthStatus,
                dispatchPat = fineGrainedPat,
            )
        }

        // Channel 3: Discord Admin Role Webhook (never hardcoded, zero Discord bot tokens)
        if (state.adminLocalState.discordAdminWebhookUrl.isNotBlank()) {
            results += ingestionService.sendDiscordAdminWebhook(
                webhookUrl = state.adminLocalState.discordAdminWebhookUrl,
                adminGate = state.appManifest.masterHubAdminGate,
                healthStatus = healthStatus,
            )
        }

        // Channel 4: Optional Admin Email Relay Webhook
        if (state.adminLocalState.emailRelayWebhookUrl.isNotBlank()) {
            results += ingestionService.sendAdminEmailRelay(
                relayUrl = state.adminLocalState.emailRelayWebhookUrl,
                adminEmails = state.appManifest.masterHubAdminGate.adminEmails,
                healthStatus = healthStatus,
            )
        }

        // Record deduplicated commit SHA
        val nextAlerted = if (failingSha.isNotBlank()) {
            (state.adminLocalState.alertedCommitShas + failingSha).distinct().takeLast(50)
        } else {
            state.adminLocalState.alertedCommitShas
        }
        val nextAdminState = state.adminLocalState.copy(alertedCommitShas = nextAlerted)
        repository.saveAdminAlertState(nextAdminState)

        // Channel 2: High-priority Blocking Pop-Up strictly gated to Master Hub Admin Roles/IDs/Emails
        val showModal = state.isCurrentUserAdmin
        _uiState.update {
            it.copy(
                adminLocalState = nextAdminState,
                lastNotificationResults = results,
                showAdminBlockingModal = showModal,
            )
        }
    }

    /**
     * Records Card Maker side consent for the staged commit SHA.
     * Finalization remains blocked until BOTH cardMakerConsent AND gameEngineConsent are CONSENTED.
     */
    fun recordCardMakerConsent(status: ConsentStatus, approverLabel: String) {
        val targetSha = _uiState.value.stagedSnapshot?.commitSha
            ?: _uiState.value.baselineSnapshot.commitSha
        val updatedContract = _uiState.value.contract.copy(
            cardMakerConsent = DualConsentRecord(
                status = status.wireValue,
                commitSha = targetSha,
                approvedBy = approverLabel.ifBlank { "card-maker-admin" },
                timestamp = currentUtcIso(),
            )
        )
        viewModelScope.launch {
            repository.saveContract(updatedContract)
            _uiState.update { it.copy(contract = updatedContract) }
            val ready = EngineCompatibilityAuditor.canFinalizeEngineSync(updatedContract, targetSha)
            _messages.tryEmit(
                if (ready) {
                    "Both engines consented for ${targetSha.take(10)} — ready to finalize sync."
                } else {
                    "Recorded Card Maker consent (${status.wireValue}). Staged preview locked until Game Engine also consents."
                }
            )
        }
    }

    /**
     * Finalizes staged engine snapshot into `engine-sync-snapshot.json` ONLY if Dual-Engine Consent passes.
     */
    fun finalizeStagedEngineSnapshot() {
        val state = _uiState.value
        val staged = state.stagedSnapshot ?: return
        if (!EngineCompatibilityAuditor.canFinalizeEngineSync(state.contract, staged.commitSha)) {
            _messages.tryEmit(
                "Dual-Engine Consent Rule: Both cardMakerConsent and gameEngineConsent must be CONSENTED for ${staged.commitSha.take(10)}."
            )
            return
        }
        viewModelScope.launch {
            repository.saveBaselineSnapshot(staged)
            _uiState.update {
                it.copy(
                    baselineSnapshot = staged,
                    stagedSnapshot = null,
                    stagedDiff = null,
                    lastSyncMessage = "Finalized verified baseline snapshot for commit ${staged.commitSha.take(10)}.",
                )
            }
            recomputeFrameReview()
            _messages.tryEmit("Finalized engine-sync-snapshot.json to commit ${staged.commitSha.take(10)}.")
        }
    }

    fun pauseOrResumeSync(paused: Boolean) {
        viewModelScope.launch {
            val next = _uiState.value.adminLocalState.copy(syncPaused = paused)
            repository.saveAdminAlertState(next)
            _uiState.update {
                it.copy(
                    adminLocalState = next,
                    showAdminBlockingModal = if (paused) false else it.showAdminBlockingModal,
                )
            }
            _messages.tryEmit(if (paused) "Engine sync paused by Admin." else "Engine sync resumed.")
        }
    }

    fun dismissAdminBlockingModal() {
        _uiState.update { it.copy(showAdminBlockingModal = false) }
    }

    fun updateAdminGateAndRelays(
        adminRoleIdsCsv: String,
        adminUserIdsCsv: String,
        adminEmailsCsv: String,
        currentUserRoleIdsCsv: String,
        currentUserId: String,
        currentUserEmail: String,
        discordWebhookUrl: String,
        emailRelayUrl: String,
    ) {
        val gate = MasterHubAdminGate(
            adminRoleIds = splitCsv(adminRoleIdsCsv),
            adminUserIds = splitCsv(adminUserIdsCsv),
            adminEmails = splitCsv(adminEmailsCsv),
            inheritFromMasterHubManifest = true,
        )
        val nextManifest = _uiState.value.appManifest.copy(masterHubAdminGate = gate)
        val nextLocal = _uiState.value.adminLocalState.copy(
            currentUserRoleIds = splitCsv(currentUserRoleIdsCsv),
            currentUserId = currentUserId.trim(),
            currentUserEmail = currentUserEmail.trim(),
            discordAdminWebhookUrl = discordWebhookUrl.trim(),
            emailRelayWebhookUrl = emailRelayUrl.trim(),
        )
        viewModelScope.launch {
            repository.saveAppManifest(nextManifest)
            repository.saveAdminAlertState(nextLocal)
            _uiState.update {
                it.copy(
                    appManifest = nextManifest,
                    adminLocalState = nextLocal,
                )
            }
            _messages.tryEmit("Saved Master Hub Admin Gate & notification relay settings.")
        }
    }

    fun dispatchManualAdminNotifications(fineGrainedPat: String?) {
        val state = _uiState.value
        viewModelScope.launch {
            val results = mutableListOf<NotificationChannelResult>()
            if (!fineGrainedPat.isNullOrBlank()) {
                results += ingestionService.dispatchMasterHubBreakingAlert(
                    masterHubRepo = state.appManifest.linkedMasterHub,
                    healthStatus = state.healthStatus,
                    dispatchPat = fineGrainedPat,
                )
            }
            if (state.adminLocalState.discordAdminWebhookUrl.isNotBlank()) {
                results += ingestionService.sendDiscordAdminWebhook(
                    webhookUrl = state.adminLocalState.discordAdminWebhookUrl,
                    adminGate = state.appManifest.masterHubAdminGate,
                    healthStatus = state.healthStatus,
                )
            }
            if (state.adminLocalState.emailRelayWebhookUrl.isNotBlank()) {
                results += ingestionService.sendAdminEmailRelay(
                    relayUrl = state.adminLocalState.emailRelayWebhookUrl,
                    adminEmails = state.appManifest.masterHubAdminGate.adminEmails,
                    healthStatus = state.healthStatus,
                )
            }
            _uiState.update { it.copy(lastNotificationResults = results) }
            val summary = results.joinToString(" | ") { "${it.channel}: ${it.detail}" }
                .ifBlank { "Configure Discord Webhook, Email Relay, or PAT (or use Mailto Escalation)." }
            _messages.tryEmit(summary)
        }
    }

    fun buildMailtoEscalationUri(): Uri {
        val state = _uiState.value
        val diffText = state.stagedDiff?.let { d ->
            "From ${d.fromCommitSha} -> ${d.toCommitSha}, addedVariants=${d.addedVariantIds}, removedVariants=${d.removedVariantIds}, boundsChanged=${d.boundsChanged}"
        }.orEmpty()
        return ingestionService.buildPrefilledMailtoUri(
            adminEmails = state.appManifest.masterHubAdminGate.adminEmails,
            healthStatus = state.healthStatus,
            diffSummary = diffText,
        )
    }

    fun buildDiagnosticReportJson(): String =
        ingestionService.serializeHealthStatusJson(_uiState.value.healthStatus)

    fun buildHandshakeContractJson(): String =
        repository.serializeContractJson(_uiState.value.contract)

    // ---- Card Frame Reviewer Controls ----

    fun updateReviewerFields(
        baseVariantId: String = _uiState.value.reviewBaseVariantId,
        cardName: String = _uiState.value.reviewCardName,
        cardType: String = _uiState.value.reviewCardType,
        rulesText: String = _uiState.value.reviewRulesText,
        codeString: String = _uiState.value.reviewCodeString,
        statText: String = _uiState.value.reviewStatText,
        effectType: String = _uiState.value.reviewEffectType,
        editorMode: CardEditorMode = _uiState.value.reviewEditorMode,
        pipFilterQuery: String = _uiState.value.reviewPipFilterQuery,
        frameWidthPx: Int = _uiState.value.reviewFrameWidthPx,
        frameHeightPx: Int = _uiState.value.reviewFrameHeightPx,
    ) {
        _uiState.update {
            it.copy(
                reviewBaseVariantId = baseVariantId,
                reviewCardName = cardName,
                reviewCardType = cardType,
                reviewRulesText = rulesText,
                reviewCodeString = codeString,
                reviewStatText = statText,
                reviewEffectType = effectType,
                reviewEditorMode = editorMode,
                reviewPipFilterQuery = pipFilterQuery,
                reviewFrameWidthPx = frameWidthPx,
                reviewFrameHeightPx = frameHeightPx,
            )
        }
        recomputeFrameReview()
    }

    fun appendReviewPip(pipCode: String) {
        _uiState.update { it.copy(reviewSelectedPips = it.reviewSelectedPips + pipCode) }
        recomputeFrameReview()
    }

    fun removeReviewPipAt(index: Int) {
        _uiState.update { s ->
            if (index !in s.reviewSelectedPips.indices) return@update s
            s.copy(reviewSelectedPips = s.reviewSelectedPips.filterIndexed { i, _ -> i != index })
        }
        recomputeFrameReview()
    }

    fun clearReviewPips() {
        _uiState.update { it.copy(reviewSelectedPips = emptyList()) }
        recomputeFrameReview()
    }

    fun toggleReviewFlavorFilter(filterId: Int) {
        _uiState.update { s ->
            val next = if (filterId in s.reviewFlavorFilters) {
                s.reviewFlavorFilters - filterId
            } else {
                s.reviewFlavorFilters + filterId
            }
            s.copy(reviewFlavorFilters = next)
        }
        recomputeFrameReview()
    }

    private fun recomputeFrameReview() {
        val s = _uiState.value
        val result = EngineCompatibilityAuditor.reviewCardFrame(
            cardName = s.reviewCardName,
            cardType = s.reviewCardType,
            rulesText = s.reviewRulesText,
            codeString = s.reviewCodeString,
            statText = s.reviewStatText,
            selectedPips = s.reviewSelectedPips,
            baseVariantId = s.reviewBaseVariantId,
            effectType = s.reviewEffectType,
            editorMode = s.reviewEditorMode,
            flavorFilters = s.reviewFlavorFilters,
            frameWidthPx = s.reviewFrameWidthPx,
            frameHeightPx = s.reviewFrameHeightPx,
            snapshot = s.activeSnapshotForReview,
        )
        _uiState.update { it.copy(reviewAuditResult = result) }
    }

    private fun splitCsv(raw: String): List<String> =
        raw.split(",", ";", "\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

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
                    return EngineSyncViewModel(
                        repository = container.engineSyncRepository,
                        ingestionService = container.engineSyncIngestionService,
                    ) as T
                }
            }
    }
}
