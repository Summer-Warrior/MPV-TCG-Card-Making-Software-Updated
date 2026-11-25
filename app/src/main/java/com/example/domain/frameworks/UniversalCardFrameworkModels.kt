package com.example.domain.frameworks

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Pure Kotlin domain models for the Universal 110+ Physical & Digital TCG Card Framework Registry.
 * Strictly clean-room metadata schemas — zero proprietary assets, zero fake Canvas frames,
 * and strictly null default card artwork (`image` omitted/null).
 */

enum class FrameworkCategory(val wireValue: String, val chipLabel: String) {
    ULTIMATE_NOTEBOOK("ULTIMATE_NOTEBOOK", "Ultimate/Notebook"),
    PHYSICAL_ACTIVE("PHYSICAL_ACTIVE", "Physical"),
    DIGITAL_ACTIVE("DIGITAL_ACTIVE", "Digital"),
    VIDEO_GAME_TCG("VIDEO_GAME_TCG", "Video Games"),
    VIDEO_GAME_MINIGAME("VIDEO_GAME_MINIGAME", "Mini-Games"),
    NICHE_INDIE_LCG("NICHE_INDIE_LCG", "Niche/LCG"),
    DEFUNCT_PHYSICAL("DEFUNCT_PHYSICAL", "Defunct Physical"),
    DEFUNCT_DIGITAL("DEFUNCT_DIGITAL", "Defunct Digital"),
    ;

    companion object {
        fun fromWire(raw: String?): FrameworkCategory =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
                ?: PHYSICAL_ACTIVE
    }
}

@JsonClass(generateAdapter = true)
data class FrameworkFieldSpec(
    @Json(name = "key") val key: String,
    @Json(name = "label") val label: String,
    @Json(name = "fieldType") val fieldType: String = "text", // text | multiline | number | symbol | choice | grid
    @Json(name = "section") val section: String = "body", // header | art | type | body | stats | footer
    @Json(name = "placeholder") val placeholder: String = "",
    @Json(name = "required") val required: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UniversalCardFramework(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "shortName") val shortName: String = "",
    @Json(name = "category") val category: String,
    @Json(name = "cardWidthMm") val cardWidthMm: Double = 63.0,
    @Json(name = "cardHeightMm") val cardHeightMm: Double = 88.0,
    @Json(name = "cardWidthPx") val cardWidthPx: Int = 375,
    @Json(name = "cardHeightPx") val cardHeightPx: Int = 523,
    @Json(name = "dpi") val dpi: Int = 150,
    @Json(name = "upstreamMseGameId") val upstreamMseGameId: String? = null,
    @Json(name = "upstreamMseStyleId") val upstreamMseStyleId: String? = null,
    @Json(name = "resourceSymbols") val resourceSymbols: List<String> = emptyList(),
    @Json(name = "statBoxes") val statBoxes: List<String> = emptyList(),
    @Json(name = "mechanicsSummary") val mechanicsSummary: String = "",
    @Json(name = "fields") val fields: List<FrameworkFieldSpec> = emptyList(),
    @Json(name = "defaultSelectedColors") val defaultSelectedColors: List<String> = emptyList(),
    @Json(name = "timeTravelMatchGuardrail") val timeTravelMatchGuardrail: Boolean = false,
) {
    val categoryEnum: FrameworkCategory get() = FrameworkCategory.fromWire(category)

    val effectiveShortName: String get() = shortName.ifBlank { name }

    /**
     * Resolves complete structured fields for the Offline Editor (combining framework-specific fields
     * with standard core card fields if not already listed, strictly excluding any default image).
     */
    fun resolvedFieldSpecs(): List<FrameworkFieldSpec> {
        if (fields.isNotEmpty()) return fields
        return listOf(
            FrameworkFieldSpec("name", "Card Name", "text", "header", required = true),
            FrameworkFieldSpec("casting cost", "Cost / Resource", "symbol", "header"),
            FrameworkFieldSpec("super type", "Card Type", "text", "type"),
            FrameworkFieldSpec("sub type", "Subtype / Trait", "text", "type"),
            FrameworkFieldSpec("rule text", "Rules / Ability Text", "multiline", "body"),
            FrameworkFieldSpec("power", statBoxes.getOrNull(0) ?: "Power / ATK", "number", "stats"),
            FrameworkFieldSpec("toughness", statBoxes.getOrNull(1) ?: "Defense / HP", "number", "stats"),
            FrameworkFieldSpec("flavor text", "Flavor / Lore Text", "multiline", "footer"),
        )
    }
}

@JsonClass(generateAdapter = true)
data class UniversalFrameworkCatalog(
    @Json(name = "schemaVersion") val schemaVersion: String = "1.0.0",
    @Json(name = "frameworks") val frameworks: List<UniversalCardFramework> = emptyList(),
)
