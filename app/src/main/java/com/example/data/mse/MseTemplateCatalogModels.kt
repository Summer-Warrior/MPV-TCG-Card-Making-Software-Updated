package com.example.data.mse

import com.example.data.settings.EngineSplit
import com.example.data.settings.PhysicalCardProfile
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class CatalogPackFilter(val label: String) {
    ALL("All Packs"),
    MAGIC("Magic"),
    NON_MAGIC("Non-Magic"),
    BETA("Beta"),
    OUTDATED("Outdated"),
}

data class CatalogPackConfig(
    val repo: String,
    val branch: String,
    val label: String,
    val filterCategory: CatalogPackFilter,
    val defaultEnabled: Boolean,
    val fontFolder: String? = null,
) {
    companion object {
        const val ORG = "MagicSetEditorPacks"

        val ALL_PACKS = listOf(
            CatalogPackConfig(
                repo = "Full-Magic-Pack",
                branch = "main",
                label = "Full-Magic-Pack",
                filterCategory = CatalogPackFilter.MAGIC,
                defaultEnabled = true,
                fontFolder = "Magic - Fonts",
            ),
            CatalogPackConfig(
                repo = "Full-Non-Magic-Pack",
                branch = "main",
                label = "Full-Non-Magic-Pack",
                filterCategory = CatalogPackFilter.NON_MAGIC,
                defaultEnabled = true,
                fontFolder = "Other - Fonts",
            ),
            CatalogPackConfig(
                repo = "Beta-Update-Pack",
                branch = "main",
                label = "Beta-Update-Pack",
                filterCategory = CatalogPackFilter.BETA,
                defaultEnabled = false,
                fontFolder = null,
            ),
            CatalogPackConfig(
                repo = "Outdated-Templates",
                branch = "main",
                label = "Outdated-Templates",
                filterCategory = CatalogPackFilter.OUTDATED,
                defaultEnabled = false,
                fontFolder = null,
            ),
        )
    }
}

@JsonClass(generateAdapter = true)
data class MseHitboxRecord(
    @Json(name = "field") val field: String,
    @Json(name = "left") val left: Float,
    @Json(name = "top") val top: Float,
    @Json(name = "width") val width: Float,
    @Json(name = "height") val height: Float,
    @Json(name = "z_index") val zIndex: Int = 0,
    @Json(name = "font_family") val fontFamily: String? = null,
    @Json(name = "font_size") val fontSize: Float? = null,
    @Json(name = "font_color_hex") val fontColorHex: String? = null,
    @Json(name = "alignment") val alignment: String? = null,
    @Json(name = "is_hidden_or_conditional") val isHiddenOrConditional: Boolean = false,
) {
    fun toDomain(): MseHitbox = MseHitbox(
        field = field,
        left = left,
        top = top,
        width = width,
        height = height,
        zIndex = zIndex,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontColorHex = fontColorHex,
        alignment = alignment,
        isHiddenOrConditional = isHiddenOrConditional,
    )

    companion object {
        fun fromDomain(h: MseHitbox): MseHitboxRecord = MseHitboxRecord(
            field = h.field,
            left = h.left,
            top = h.top,
            width = h.width,
            height = h.height,
            zIndex = h.zIndex,
            fontFamily = h.fontFamily,
            fontSize = h.fontSize,
            fontColorHex = h.fontColorHex,
            alignment = h.alignment,
            isHiddenOrConditional = h.isHiddenOrConditional,
        )
    }
}

@JsonClass(generateAdapter = true)
data class DiscoveredGame(
    @Json(name = "game_id") val gameId: String,
    @Json(name = "folder_name") val folderName: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "short_name") val shortName: String,
    @Json(name = "icon_file") val iconFile: String? = null,
    @Json(name = "icon_url") val iconUrl: String? = null,
    @Json(name = "position_hint") val positionHint: Int = 999,
    @Json(name = "pack_repo") val packRepo: String,
    @Json(name = "pack_branch") val packBranch: String = "main",
    @Json(name = "pack_label") val packLabel: String,
    @Json(name = "is_from_bridge") val isFromBridge: Boolean = false,
) {
    val displayName: String get() = fullName.ifBlank { shortName.ifBlank { gameId } }

    val defaultEngineSplit: EngineSplit
        get() = MseTemplateCatalog.engineSplitForGame(gameId, packRepo)

    val defaultPhysicalProfile: PhysicalCardProfile
        get() = MseTemplateCatalog.physicalProfileForGame(gameId)
}

@JsonClass(generateAdapter = true)
data class DiscoveredStyle(
    @Json(name = "stylesheet_id") val stylesheetId: String,
    @Json(name = "folder_name") val folderName: String,
    @Json(name = "game_id") val gameId: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "short_name") val shortName: String,
    @Json(name = "icon_file") val iconFile: String? = null,
    @Json(name = "icon_url") val iconUrl: String? = null,
    @Json(name = "position_hint") val positionHint: Int = 999,
    @Json(name = "pack_repo") val packRepo: String,
    @Json(name = "pack_branch") val packBranch: String = "main",
    @Json(name = "pack_label") val packLabel: String,
    @Json(name = "card_width_px") val cardWidthPx: Int = 375,
    @Json(name = "card_height_px") val cardHeightPx: Int = 523,
    @Json(name = "card_dpi") val cardDpi: Int = 150,
    @Json(name = "depends_on") val dependsOn: List<String> = emptyList(),
    @Json(name = "referenced_fonts") val referencedFonts: List<String> = emptyList(),
    @Json(name = "raw_style_url") val rawStyleUrl: String? = null,
    @Json(name = "base_frame_choices") val baseFrameChoices: Map<String, String> = emptyMap(),
    @Json(name = "default_frame_image") val defaultFrameImage: String? = null,
    @Json(name = "uses_scripted_background") val usesScriptedBackground: Boolean = false,
    @Json(name = "hitboxes") val hitboxes: List<MseHitboxRecord> = emptyList(),
    @Json(name = "is_from_bridge") val isFromBridge: Boolean = false,
) {
    val displayName: String get() = shortName.ifBlank { fullName.ifBlank { stylesheetId } }

    val dpiLabel: String get() = "${cardWidthPx}×${cardHeightPx} • ${cardDpi} DPI"

    val engineSplit: EngineSplit
        get() = MseTemplateCatalog.engineSplitForGame(gameId, packRepo)

    val physicalProfile: PhysicalCardProfile
        get() = MseTemplateCatalog.physicalProfileForGame(gameId)

    fun toStyleSpec(): MseStyleSpec = MseStyleSpec(
        stylesheetId = stylesheetId,
        game = gameId,
        shortName = displayName,
        fullName = fullName.ifBlank { displayName },
        cardWidthPx = cardWidthPx,
        cardHeightPx = cardHeightPx,
        cardDpi = cardDpi,
        hitboxes = hitboxes.map { it.toDomain() },
        referencedFonts = referencedFonts,
        sourceLabel = packLabel,
        iconFile = iconFile,
        positionHint = positionHint,
        dependsOn = dependsOn,
        baseFrameChoices = baseFrameChoices,
        defaultFrameImage = defaultFrameImage,
        usesScriptedBackground = usesScriptedBackground,
    )
}

@JsonClass(generateAdapter = true)
data class DiscoveredInclude(
    @Json(name = "folder_name") val folderName: String,
    @Json(name = "pack_repo") val packRepo: String,
    @Json(name = "pack_branch") val packBranch: String = "main",
)

@JsonClass(generateAdapter = true)
data class DiscoveredPackFont(
    @Json(name = "family_name") val familyName: String,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "download_url") val downloadUrl: String,
    @Json(name = "pack_repo") val packRepo: String,
    @Json(name = "font_folder") val fontFolder: String,
)

@JsonClass(generateAdapter = true)
data class FolderAssetCacheEntry(
    @Json(name = "repo") val repo: String,
    @Json(name = "branch") val branch: String,
    @Json(name = "folder_name") val folderName: String,
    @Json(name = "etag") val etag: String? = null,
    @Json(name = "image_files") val imageFiles: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class FrameImageValidationEntry(
    @Json(name = "valid") val valid: Boolean,
    @Json(name = "width") val width: Int = 0,
    @Json(name = "height") val height: Int = 0,
    @Json(name = "avg_luminance") val avgLuminance: Float = 0.72f,
)

@JsonClass(generateAdapter = true)
data class CatalogDiskCache(
    @Json(name = "version") val version: Int = 2,
    @Json(name = "games") val games: List<DiscoveredGame> = emptyList(),
    @Json(name = "styles") val styles: List<DiscoveredStyle> = emptyList(),
    @Json(name = "includes") val includes: List<DiscoveredInclude> = emptyList(),
    @Json(name = "pack_fonts") val packFonts: List<DiscoveredPackFont> = emptyList(),
    @Json(name = "repo_etags") val repoEtags: Map<String, String> = emptyMap(),
    @Json(name = "folder_asset_cache") val folderAssetCache: Map<String, FolderAssetCacheEntry> = emptyMap(),
    @Json(name = "frame_validation_cache") val frameValidationCache: Map<String, FrameImageValidationEntry> = emptyMap(),
    @Json(name = "last_synced_epoch_ms") val lastSyncedEpochMs: Long = 0L,
    @Json(name = "beta_enabled") val betaEnabled: Boolean = false,
    @Json(name = "outdated_enabled") val outdatedEnabled: Boolean = false,
)

data class CatalogSyncState(
    val isSyncing: Boolean = false,
    val progressFraction: Float = 0f,
    val statusMessage: String = "",
    val lastError: String? = null,
    val syncedGamesCount: Int = 0,
    val syncedStylesCount: Int = 0,
    val syncedFontsCount: Int = 0,
    val betaEnabled: Boolean = false,
    val outdatedEnabled: Boolean = false,
)

data class FrameVariantOption(
    val key: String,
    val label: String,
    val imageUrl: String,
    val swatchColorHex: Long? = null,
    val avgLuminance: Float = 0.72f,
) {
    val isDarkFrame: Boolean get() = avgLuminance < 0.44f
}

data class ResolvedOfflineFrame(
    val styleKey: String,
    val variants: List<FrameVariantOption>,
    val activeVariantKey: String?,
    val isLoading: Boolean = false,
) {
    val activeVariant: FrameVariantOption?
        get() = variants.firstOrNull { it.key == activeVariantKey } ?: variants.firstOrNull()
}
