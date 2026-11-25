package com.example.ui.comparison

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.AppContainer
import com.example.data.mse.MseSetArchive
import com.example.data.official.OfficialSetSourcesRepository
import com.example.domain.comparison.ComparableSet
import com.example.domain.comparison.OfficialSetSummary
import com.example.domain.comparison.SetComparisonEngine
import com.example.domain.comparison.SetComparisonResult
import com.example.domain.comparison.SplitOffSetManifest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OfficialSourceTab(val label: String) {
    SCRYFALL("MTG (Scryfall)"),
    YGOPRODECK("Yu-Gi-Oh! (YGOPRODeck)"),
    POKEMON_TCG("Pokémon TCG"),
    LOCAL_FILES("Local Files / URL"),
}

data class SetComparisonUiState(
    val activeSourceTab: OfficialSourceTab = OfficialSourceTab.SCRYFALL,
    val searchQuery: String = "",
    val setsBySource: Map<OfficialSourceTab, List<OfficialSetSummary>> = emptyMap(),
    val isLoadingSets: Boolean = false,
    val isLoadingCards: Boolean = false,
    val selectedOfficialSet: ComparableSet? = null,
    val comparisonResult: SetComparisonResult? = null,
    val errorMessage: String? = null,
    val customOpenUrl: String = "",
)

/**
 * ViewModel for Official Set Pulling (Scryfall, YGOPRODeck, Pokémon TCG, Open URLs,
 * and Local SAF `.mse-set`, `.cdb`, Forge `.txt`, Nexus JSON) and deterministic
 * side-by-side comparison via [SetComparisonEngine].
 */
class SetComparisonViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetComparisonUiState())
    val uiState: StateFlow<SetComparisonUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun selectSourceTab(tab: OfficialSourceTab) {
        _uiState.update { it.copy(activeSourceTab = tab, errorMessage = null) }
        if (tab != OfficialSourceTab.LOCAL_FILES && _uiState.value.setsBySource[tab].isNullOrEmpty()) {
            loadSetsForTab(tab)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateCustomOpenUrl(url: String) {
        _uiState.update { it.copy(customOpenUrl = url) }
    }

    fun loadSetsForTab(tab: OfficialSourceTab = _uiState.value.activeSourceTab) {
        if (tab == OfficialSourceTab.LOCAL_FILES) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSets = true, errorMessage = null) }
            val result = when (tab) {
                OfficialSourceTab.SCRYFALL -> container.officialSetSourcesRepository.fetchScryfallSets()
                OfficialSourceTab.YGOPRODECK -> container.officialSetSourcesRepository.fetchYgoProDeckSets()
                OfficialSourceTab.POKEMON_TCG -> container.officialSetSourcesRepository.fetchPokemonTcgSets()
                OfficialSourceTab.LOCAL_FILES -> Result.success(emptyList())
            }
            result.onSuccess { list ->
                _uiState.update { state ->
                    state.copy(
                        isLoadingSets = false,
                        setsBySource = state.setsBySource + (tab to list),
                        errorMessage = null,
                    )
                }
            }.onFailure { err ->
                val msg = err.message ?: "Unable to fetch official sets."
                _uiState.update { it.copy(isLoadingSets = false, errorMessage = msg) }
                _messages.tryEmit(msg)
            }
        }
    }

    fun loadOfficialSetCards(
        summary: OfficialSetSummary,
        activeArchive: MseSetArchive?,
        splitOffManifest: SplitOffSetManifest?,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCards = true, errorMessage = null) }
            val result = when (summary.source) {
                OfficialSetSourcesRepository.SOURCE_SCRYFALL ->
                    container.officialSetSourcesRepository.fetchScryfallSetCards(summary)
                OfficialSetSourcesRepository.SOURCE_YGOPRODECK ->
                    container.officialSetSourcesRepository.fetchYgoProDeckSetCards(summary)
                OfficialSetSourcesRepository.SOURCE_POKEMON_TCG ->
                    container.officialSetSourcesRepository.fetchPokemonTcgSetCards(summary)
                else -> Result.failure(IllegalArgumentException("Unsupported official source: ${summary.source}"))
            }
            result.onSuccess { officialSet ->
                val comparison = activeArchive?.let { arc ->
                    val leftSet = SetComparisonEngine.fromMseSetArchive(arc, splitOffManifest)
                    SetComparisonEngine.compareSets(leftSet, officialSet)
                }
                _uiState.update {
                    it.copy(
                        isLoadingCards = false,
                        selectedOfficialSet = officialSet,
                        comparisonResult = comparison,
                        errorMessage = null,
                    )
                }
                _messages.tryEmit("Loaded ${officialSet.cards.size} real cards from ${officialSet.setName}.")
            }.onFailure { err ->
                val msg = err.message ?: "Failed to pull official set cards."
                _uiState.update { it.copy(isLoadingCards = false, errorMessage = msg) }
                _messages.tryEmit(msg)
            }
        }
    }

    fun loadLocalReferenceFile(
        uri: Uri,
        activeArchive: MseSetArchive?,
        splitOffManifest: SplitOffSetManifest?,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCards = true, errorMessage = null) }
            container.officialSetSourcesRepository.loadLocalComparableSetFromUri(uri)
                .onSuccess { loadedSet ->
                    val comparison = activeArchive?.let { arc ->
                        val leftSet = SetComparisonEngine.fromMseSetArchive(arc, splitOffManifest)
                        SetComparisonEngine.compareSets(leftSet, loadedSet)
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingCards = false,
                            selectedOfficialSet = loadedSet,
                            comparisonResult = comparison,
                            errorMessage = null,
                        )
                    }
                    _messages.tryEmit("Loaded ${loadedSet.cards.size} card(s) from ${loadedSet.setName}.")
                }
                .onFailure { err ->
                    val msg = "File parse failed: ${err.message}"
                    _uiState.update { it.copy(isLoadingCards = false, errorMessage = msg) }
                    _messages.tryEmit(msg)
                }
        }
    }

    fun loadOpenResourceUrl(
        url: String,
        activeArchive: MseSetArchive?,
        splitOffManifest: SplitOffSetManifest?,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCards = true, errorMessage = null) }
            container.officialSetSourcesRepository.fetchOpenResourceSetFromUrl(url)
                .onSuccess { loadedSet ->
                    val comparison = activeArchive?.let { arc ->
                        val leftSet = SetComparisonEngine.fromMseSetArchive(arc, splitOffManifest)
                        SetComparisonEngine.compareSets(leftSet, loadedSet)
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingCards = false,
                            selectedOfficialSet = loadedSet,
                            comparisonResult = comparison,
                            errorMessage = null,
                        )
                    }
                    _messages.tryEmit("Loaded ${loadedSet.cards.size} card(s) from Open-Resource URL.")
                }
                .onFailure { err ->
                    val msg = "Open-Resource fetch failed: ${err.message}"
                    _uiState.update { it.copy(isLoadingCards = false, errorMessage = msg) }
                    _messages.tryEmit(msg)
                }
        }
    }

    fun recomputeComparison(
        activeArchive: MseSetArchive?,
        splitOffManifest: SplitOffSetManifest?,
    ) {
        val official = _uiState.value.selectedOfficialSet ?: return
        val comparison = activeArchive?.let { arc ->
            val leftSet = SetComparisonEngine.fromMseSetArchive(arc, splitOffManifest)
            SetComparisonEngine.compareSets(leftSet, official)
        }
        _uiState.update { it.copy(comparisonResult = comparison) }
    }

    /**
     * 1-tap "Compare vs. Parent Official Set" from a Split-Off Set workspace banner.
     */
    fun compareWithParentOfficialSet(
        manifest: SplitOffSetManifest,
        activeArchive: MseSetArchive,
    ) {
        val currentOfficial = _uiState.value.selectedOfficialSet
        if (currentOfficial != null &&
            currentOfficial.setId.equals(manifest.parentOfficialSetId, ignoreCase = true)
        ) {
            recomputeComparison(activeArchive, manifest)
            return
        }
        val summary = OfficialSetSummary(
            setId = manifest.parentOfficialSetId,
            setName = manifest.parentOfficialSetName,
            source = manifest.parentOfficialSource,
            gameId = manifest.parentGameId,
            queryParam = manifest.parentOfficialSetName.substringBefore(" (").trim().ifBlank { manifest.parentOfficialSetId },
        )
        if (manifest.parentOfficialSource in setOf(
                OfficialSetSourcesRepository.SOURCE_SCRYFALL,
                OfficialSetSourcesRepository.SOURCE_YGOPRODECK,
                OfficialSetSourcesRepository.SOURCE_POKEMON_TCG,
            )
        ) {
            loadOfficialSetCards(summary, activeArchive, manifest)
        } else {
            _messages.tryEmit(
                "Parent set '${manifest.parentOfficialSetName}' came from a local file. Re-select it under Local Files to diff."
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SetComparisonViewModel(container) as T
                }
            }
    }
}
