package com.example.data.settings

enum class EngineSplit(val apiValue: String, val label: String) {
    MSE_MODERN("mse_modern", "MSE Modern (2.5+/2.6+)"),
    MSE_LEGACY("mse_legacy", "MSE Legacy (twanvl)"),
    ;

    companion object {
        fun fromApiValue(value: String?): EngineSplit =
            entries.firstOrNull { it.apiValue == value } ?: MSE_MODERN
    }
}

enum class PhysicalCardProfile(
    val id: String,
    val label: String,
    val widthMm: Float,
    val heightMm: Float,
    val description: String,
) {
    STANDARD_63X88(
        id = "standard_63x88",
        label = "Standard 63×88 mm",
        widthMm = 63.0f,
        heightMm = 88.0f,
        description = "MTG / Pokémon / Lorcana / One Piece physical size",
    ),
    JAPANESE_59X86(
        id = "japanese_59x86",
        label = "Japanese 59×86 mm",
        widthMm = 59.0f,
        heightMm = 86.0f,
        description = "Yu-Gi-Oh! / Cardfight!! Vanguard physical size",
    ),
    TEMPLATE_DPI(
        id = "template_dpi",
        label = "Template DPI",
        widthMm = 63.0f,
        heightMm = 88.0f,
        description = "Compute inches strictly from .mse-style card width/height ÷ card dpi",
    ),
    ;

    companion object {
        fun fromId(id: String?): PhysicalCardProfile =
            entries.firstOrNull { it.id == id } ?: STANDARD_63X88
    }
}

data class StudioSettings(
    val bridgeBaseUrl: String = DEFAULT_BRIDGE_URL,
    val calibrationScale: Float = 1.0f,
    val calibrationLocked: Boolean = false,
    val physicalProfile: PhysicalCardProfile = PhysicalCardProfile.STANDARD_63X88,
    val activeEngineSplit: EngineSplit = EngineSplit.MSE_MODERN,
    val selectedFontFamily: String? = null,
    val darkTheme: Boolean = true,
) {
    companion object {
        const val DEFAULT_BRIDGE_URL = "http://127.0.0.1:8765"
        const val MIN_CALIBRATION = 0.60f
        const val MAX_CALIBRATION = 1.40f
    }
}
