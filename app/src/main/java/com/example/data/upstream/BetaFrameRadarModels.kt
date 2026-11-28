package com.example.data.upstream

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Pure Kotlin models for the Foreground-Active Automatic Multi-Site Beta Card Frame Radar.
 *
 * Permanent BETA Policy:
 * - Only canonical Magic Set Editor repositories (`G-e-n-e-v-e-n-s-i-S/MagicSetEditor2` and
 *   `MagicSetEditorPacks/Full-Magic-Pack`) map to [FrameOriginTrust.CANONICAL_OFFICIAL_MSE].
 * - ALL non-MSE official packs, forked repos, fan YGO customs, and fan-art styles are
 *   permanently flagged `isAlwaysBeta = true`.
 * - Fan-made source trails are stored strictly inside code comments and the downloadable
 *   `.mse-style-beta.txt` package—never rendered on the card preview UI.
 */

enum class BetaFrameCategory(val label: String) {
    CURRENT_GAME("Current Games"),
    NEW_GAME("New Games"),
    FAN_YGO_CUSTOM("Fan YGO Customs"),
    FAN_STYLE("Fan & Art Styles"),
}

enum class FrameOriginTrust(
    val alwaysBeta: Boolean,
    val betaBadgeLabel: String,
) {
    CANONICAL_OFFICIAL_MSE(false, "OFFICIAL MSE"),
    NON_MSE_OFFICIAL(true, "BETA • NON-MSE"),
    FORKED_REPO(true, "BETA • FORK"),
    FAN_YGO_CUSTOM(true, "BETA • FAN YGO"),
    FAN_ART_STYLE(true, "BETA • FAN"),
}

enum class BetaFeedProtocol {
    GITHUB_MSE_PACK,
    GITHUB_TOPIC_SEARCH,
    DEVIANTART_RSS,
    GENERIC_RSS,
}

enum class UniqueFrameUseKind(
    val label: String,
    val targetGameId: String,
) {
    NONE_OR_GENERIC_ART("No Unique Frame Mechanic", ""),
    YGO_DARK_SYNCHRO("YGO Dark Synchro Monster", "yugioh"),
    YGO_LINK_PENDULUM("YGO Link-Pendulum Hybrid Monster", "yugioh"),
    YGO_HYBRID_PENDULUM("YGO Extra-Deck Pendulum Hybrid", "yugioh"),
    YGO_MAXIMUM_MONSTER("YGO Rush Duel Maximum Monster", "yugioh"),
    YGO_RUSH_DUEL_UNIQUE("YGO Rush / Over-Rush Monster", "yugioh"),
    YGO_NOVEL_MONSTER_MECHANIC("YGO New / Custom Monster Mechanic", "yugioh"),
    MTG_UNIQUE_MECHANIC("MTG Unique Frame Mechanic", "magic"),
    POKEMON_UNIQUE_MECHANIC("Pokémon Unique Frame Mechanic", "pokemon"),
    UNIVERSAL_UNIQUE_TCG_FRAME("New / Unique Custom TCG Frame", "custom-tcg"),
}

@JsonClass(generateAdapter = true)
data class UniqueFrameUseAssessment(
    @Json(name = "has_clear_unique_use") val hasClearUniqueUse: Boolean = false,
    @Json(name = "kind") val kind: UniqueFrameUseKind = UniqueFrameUseKind.NONE_OR_GENERIC_ART,
    @Json(name = "mechanic_summary") val mechanicSummary: String = "",
    @Json(name = "unique_fields_required") val uniqueFieldsRequired: List<String> = emptyList(),
    @Json(name = "matched_signals") val matchedSignals: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BetaFrameSourceFeed(
    @Json(name = "id") val id: String,
    @Json(name = "label") val label: String,
    @Json(name = "protocol") val protocol: BetaFeedProtocol,
    @Json(name = "category") val category: BetaFrameCategory,
    @Json(name = "origin_trust") val originTrust: FrameOriginTrust,
    @Json(name = "owner") val owner: String = "",
    @Json(name = "repo") val repo: String = "",
    @Json(name = "branch") val branch: String = "main",
    @Json(name = "data_sub_path") val dataSubPath: String = "data",
    @Json(name = "feed_url") val feedUrl: String = "",
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "is_built_in") val isBuiltIn: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class DiscoveredBetaFrameItem(
    @Json(name = "id") val id: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "folder_name") val folderName: String,
    @Json(name = "game_id") val gameId: String,
    @Json(name = "category") val category: BetaFrameCategory,
    @Json(name = "origin_trust") val originTrust: FrameOriginTrust,
    @Json(name = "is_always_beta") val isAlwaysBeta: Boolean = originTrust.alwaysBeta,
    @Json(name = "source_feed_label") val sourceFeedLabel: String,
    @Json(name = "source_feed_url") val sourceFeedUrl: String = "",
    @Json(name = "author_credit") val authorCredit: String = "",
    @Json(name = "source_web_url") val sourceWebUrl: String = "",
    @Json(name = "style_file_raw_url") val styleFileRawUrl: String = "",
    @Json(name = "game_file_raw_url") val gameFileRawUrl: String? = null,
    @Json(name = "preview_thumbnail_url") val previewThumbnailUrl: String? = null,
    @Json(name = "description_snippet") val descriptionSnippet: String = "",
    @Json(name = "latest_sha_or_guid") val latestShaOrGuid: String = "",
    @Json(name = "is_new_since_last_seen") val isNewSinceLastSeen: Boolean = false,
    @Json(name = "has_upstream_template_file") val hasUpstreamTemplateFile: Boolean = false,
    @Json(name = "unique_use_assessment") val uniqueUseAssessment: UniqueFrameUseAssessment? = null,
    @Json(name = "cached_code_file_path") val cachedCodeFilePath: String? = null,
) {
    /**
     * Permanently keeps "BETA" visible for every non-MSE official, forked, or fan frame.
     */
    fun effectiveStatusBadge(): String {
        val mustBeBeta = isAlwaysBeta || originTrust.alwaysBeta
        return if (mustBeBeta) {
            val label = originTrust.betaBadgeLabel
            if (label.contains("BETA", ignoreCase = true)) label else "BETA • $label"
        } else {
            originTrust.betaBadgeLabel
        }
    }

    fun isFanMade(): Boolean =
        originTrust in setOf(
            FrameOriginTrust.FAN_YGO_CUSTOM,
            FrameOriginTrust.FAN_ART_STYLE,
            FrameOriginTrust.FORKED_REPO,
        ) || category in setOf(
            BetaFrameCategory.FAN_YGO_CUSTOM,
            BetaFrameCategory.FAN_STYLE,
        )

    fun collectedSourceUrls(): List<String> =
        listOfNotNull(
            sourceWebUrl,
            styleFileRawUrl,
            gameFileRawUrl,
            previewThumbnailUrl,
            sourceFeedUrl,
        )
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()

    fun hasRequiredFanSources(): Boolean = !isFanMade() || collectedSourceUrls().isNotEmpty()

    fun isEligibleForUniqueNoTemplateSynthesis(): Boolean =
        !hasUpstreamTemplateFile && (uniqueUseAssessment?.hasClearUniqueUse == true)
}

data class BetaFrameCodePackage(
    val item: DiscoveredBetaFrameItem,
    val fetchedAtIso: String,
    val wasAutoSynthesizedForUniqueUse: Boolean,
    val uniqueUseAssessment: UniqueFrameUseAssessment,
    val linkedSourceUrls: List<String>,
    val rawStyleCode: String,
    val rawGameCode: String? = null,
    val parsedGeometrySummary: String,
    val formattedDownloadableText: String,
)

@JsonClass(generateAdapter = true)
data class BetaRadarPersistedState(
    @Json(name = "auto_check_when_online") val autoCheckWhenOnline: Boolean = true,
    @Json(name = "last_checked_epoch_ms") val lastCheckedEpochMs: Long = 0L,
    @Json(name = "seen_item_signatures") val seenItemSignatures: Map<String, String> = emptyMap(),
    @Json(name = "known_game_ids") val knownGameIds: Set<String> = emptySet(),
    @Json(name = "etags_by_feed_id") val etagsByFeedId: Map<String, String> = emptyMap(),
    @Json(name = "custom_feeds") val customFeeds: List<BetaFrameSourceFeed> = emptyList(),
    @Json(name = "cached_items") val cachedItems: List<DiscoveredBetaFrameItem> = emptyList(),
)

data class BetaRadarUiState(
    val items: List<DiscoveredBetaFrameItem> = emptyList(),
    val feeds: List<BetaFrameSourceFeed> = emptyList(),
    val autoCheckWhenOnline: Boolean = true,
    val isAppInForeground: Boolean = false,
    val isScanning: Boolean = false,
    val lastCheckedEpochMs: Long = 0L,
    val lastStatusMessage: String = "Cached Beta Frame Radar ready (foreground-only auto-check).",
    val activeCodePackage: BetaFrameCodePackage? = null,
    val isLoadingCodePackage: Boolean = false,
    val codePackageError: String? = null,
) {
    val newCount: Int
        get() = items.count { it.isNewSinceLastSeen }

    val uniqueNoTemplateCount: Int
        get() = items.count { it.isEligibleForUniqueNoTemplateSynthesis() }
}
