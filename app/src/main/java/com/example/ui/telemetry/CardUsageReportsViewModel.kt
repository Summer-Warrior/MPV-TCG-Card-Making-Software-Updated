package com.example.ui.telemetry

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.telemetry.UsagePullSummary
import com.example.domain.comparison.CardOrigin
import com.example.domain.telemetry.CardUsageAnalysisResult
import com.example.domain.telemetry.CardUsageAnalyzer
import com.example.domain.telemetry.CustomCardUsageReport
import com.example.domain.telemetry.TelemetryInteractionType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardUsageReportsUiState(
    val reports: List<CustomCardUsageReport> = emptyList(),
    val selectedOriginFilter: CardOrigin? = null,
    val selectedInteractionFilter: TelemetryInteractionType? = null,
    val searchQuery: String = "",
    val isPullingFromGitHub: Boolean = false,
    val lastPullSummary: UsagePullSummary? = null,
    val dashboard: CardUsageAnalysisResult = CardUsageAnalyzer.analyze(emptyList()),
)

/**
 * ViewModel for Custom Card Usage & Interaction Telemetry Reports received from
 * `Summer-Warrior/Tcg-game-engine-test` (`com.aistudio.nexustcg.vxqrmk`) via:
 * 1. Direct Android Private Play Store Intent / Deep-Link handoff
 * 2. Bidirectional GitHub Bridge (`nexus-bridge/usage-reports/`)
 * 3. Local Android SAF JSON import
 */
class CardUsageReportsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _selectedOrigin = MutableStateFlow<CardOrigin?>(null)
    private val _selectedInteraction = MutableStateFlow<TelemetryInteractionType?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isPulling = MutableStateFlow(false)
    private val _lastPullSummary = MutableStateFlow<UsagePullSummary?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<CardUsageReportsUiState> = combine(
        container.cardUsageReportRepository.reports,
        _selectedOrigin,
        _selectedInteraction,
        _searchQuery,
        combine(_isPulling, _lastPullSummary) { pulling, summary -> pulling to summary },
    ) { reports, originFilter, interactionFilter, query, (isPulling, lastPull) ->
        val rawDashboard = CardUsageAnalyzer.analyze(
            reports = reports,
            originFilter = originFilter,
            interactionTypeFilter = interactionFilter,
        )
        val trimmed = query.trim().lowercase()
        val filteredDashboard = if (trimmed.isEmpty()) {
            rawDashboard
        } else {
            rawDashboard.copy(
                cardRows = rawDashboard.cardRows.filter { row ->
                    row.cardName.lowercase().contains(trimmed) ||
                        row.cardId.lowercase().contains(trimmed) ||
                        row.targetClientId.lowercase().contains(trimmed)
                },
                interactions = rawDashboard.interactions.filter { i ->
                    i.sourceCardName.lowercase().contains(trimmed) ||
                        i.targetCardName.lowercase().contains(trimmed) ||
                        i.sampleLogLine.lowercase().contains(trimmed)
                },
            )
        }
        CardUsageReportsUiState(
            reports = reports,
            selectedOriginFilter = originFilter,
            selectedInteractionFilter = interactionFilter,
            searchQuery = query,
            isPullingFromGitHub = isPulling,
            lastPullSummary = lastPull,
            dashboard = filteredDashboard,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardUsageReportsUiState(),
    )

    fun setOriginFilter(origin: CardOrigin?) {
        _selectedOrigin.value = origin
    }

    fun setInteractionFilter(type: TelemetryInteractionType?) {
        _selectedInteraction.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun pullReportsFromGitHub(githubPat: String = "") {
        if (!container.networkConnectivityMonitor.checkIsOnlineNow()) {
            _messages.tryEmit("Offline mode — connect to the internet to pull usage reports from GitHub.")
            return
        }
        viewModelScope.launch {
            _isPulling.value = true
            val manifest = container.nexusHubRepository.loadManifest()
            val repos = listOf(
                manifest.linkedGameEngineRepo to manifest.linkedGameEngineBranch,
                manifest.linkedHubRepo to manifest.linkedHubBranch,
            )
            container.cardUsageReportRepository.pullReportsFromGitHub(
                repoBranches = repos,
                githubPat = githubPat,
            ).onSuccess { summary ->
                _lastPullSummary.value = summary
                _messages.tryEmit(summary.detail)
            }.onFailure { err ->
                _messages.tryEmit("GitHub telemetry pull failed: ${err.message}")
            }
            _isPulling.value = false
        }
    }

    fun importReportFromUri(uri: Uri) {
        viewModelScope.launch {
            container.cardUsageReportRepository.importReportFromUri(uri)
                .onSuccess { count ->
                    _messages.tryEmit("Imported $count card usage telemetry report(s).")
                }
                .onFailure { err ->
                    _messages.tryEmit("Telemetry import failed: ${err.message}")
                }
        }
    }

    fun importReportJson(rawJson: String, sourceLabel: String = "TCG Client") {
        viewModelScope.launch {
            container.cardUsageReportRepository.importReportJson(rawJson)
                .onSuccess { count ->
                    _messages.tryEmit("Ingested $count telemetry report(s) from $sourceLabel.")
                }
                .onFailure { err ->
                    _messages.tryEmit("Invalid telemetry JSON from $sourceLabel: ${err.message}")
                }
        }
    }

    fun clearAllReports() {
        viewModelScope.launch {
            container.cardUsageReportRepository.clearAllReports()
            _messages.tryEmit("Cleared stored telemetry reports.")
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CardUsageReportsViewModel(container) as T
                }
            }
    }
}
