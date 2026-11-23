package com.example.data.mse

import com.example.data.bridge.BridgeTemplateDto
import com.example.data.settings.EngineSplit
import com.example.data.settings.PhysicalCardProfile

data class MseGameOption(
    val gameId: String,
    val displayName: String,
    val subtitle: String,
    val defaultPackSplit: String,
    val defaultEngineSplit: EngineSplit,
    val defaultPhysicalProfile: PhysicalCardProfile,
)

data class MseStyleOption(
    val stylesheetId: String,
    val gameId: String,
    val displayName: String,
    val packSplit: String,
    val dpiLabel: String,
    val cardWidthPx: Int,
    val cardHeightPx: Int,
    val cardDpi: Int,
    val engineSplit: EngineSplit,
    val physicalProfile: PhysicalCardProfile,
)

/**
 * Real MagicSetEditorPacks game & stylesheet directory merged dynamically with any
 * templates discovered from the live MSE Bridge (`GET /templates`).
 */
object MseTemplateCatalog {

    val baseGames: List<MseGameOption> = listOf(
        MseGameOption(
            gameId = "magic",
            displayName = "Magic: The Gathering",
            subtitle = "M15, Mainframe, Planeswalker & Vintage frames",
            defaultPackSplit = "Full-Magic-Pack",
            defaultEngineSplit = EngineSplit.MSE_MODERN,
            defaultPhysicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseGameOption(
            gameId = "yugioh",
            displayName = "Yu-Gi-Oh!",
            subtitle = "Series 10, Rush Duel & Classic frames",
            defaultPackSplit = "Full-Non-Magic-Pack",
            defaultEngineSplit = EngineSplit.MSE_LEGACY,
            defaultPhysicalProfile = PhysicalCardProfile.JAPANESE_59X86,
        ),
        MseGameOption(
            gameId = "pokemon",
            displayName = "Pokémon",
            subtitle = "Scarlet & Violet, Sun & Moon & Classic frames",
            defaultPackSplit = "Full-Non-Magic-Pack",
            defaultEngineSplit = EngineSplit.MSE_LEGACY,
            defaultPhysicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseGameOption(
            gameId = "inscryption",
            displayName = "Inscryption",
            subtitle = "Leshy Woodcut, PO3 Bot & Act I frames",
            defaultPackSplit = "Full-Non-Magic-Pack",
            defaultEngineSplit = EngineSplit.MSE_LEGACY,
            defaultPhysicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseGameOption(
            gameId = "rage",
            displayName = "Rage",
            subtitle = "Classic Werewolf: The Apocalypse CCG frames",
            defaultPackSplit = "Full-Non-Magic-Pack",
            defaultEngineSplit = EngineSplit.MSE_LEGACY,
            defaultPhysicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
    )

    val baseStyles: List<MseStyleOption> = listOf(
        // Magic: The Gathering (Full-Magic-Pack / Beta-Update-Pack)
        MseStyleOption(
            stylesheetId = "m15-mainframe-dfc",
            gameId = "magic",
            displayName = "M15 Mainframe (Hi-Res)",
            packSplit = "Full-Magic-Pack",
            dpiLabel = "Hi-Res 300 DPI • 744×1039",
            cardWidthPx = 744,
            cardHeightPx = 1039,
            cardDpi = 300,
            engineSplit = EngineSplit.MSE_MODERN,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseStyleOption(
            stylesheetId = "m15-altered",
            gameId = "magic",
            displayName = "M15 Altered (Standard)",
            packSplit = "Full-Magic-Pack",
            dpiLabel = "150 DPI • 375×523",
            cardWidthPx = 375,
            cardHeightPx = 523,
            cardDpi = 150,
            engineSplit = EngineSplit.MSE_MODERN,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseStyleOption(
            stylesheetId = "m15-mainframe-planeswalker",
            gameId = "magic",
            displayName = "M15 Mainframe Planeswalker",
            packSplit = "Full-Magic-Pack",
            dpiLabel = "Hi-Res 300 DPI • 744×1039",
            cardWidthPx = 744,
            cardHeightPx = 1039,
            cardDpi = 300,
            engineSplit = EngineSplit.MSE_MODERN,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseStyleOption(
            stylesheetId = "m15-showcase-beta",
            gameId = "magic",
            displayName = "M15 Showcase (Beta Channel)",
            packSplit = "Beta-Update-Pack",
            dpiLabel = "Hi-Res 300 DPI • 744×1039",
            cardWidthPx = 744,
            cardHeightPx = 1039,
            cardDpi = 300,
            engineSplit = EngineSplit.MSE_MODERN,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseStyleOption(
            stylesheetId = "old",
            gameId = "magic",
            displayName = "Classic Old Frame (1993)",
            packSplit = "Full-Magic-Pack",
            dpiLabel = "150 DPI • 375×523",
            cardWidthPx = 375,
            cardHeightPx = 523,
            cardDpi = 150,
            engineSplit = EngineSplit.MSE_MODERN,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        // Yu-Gi-Oh! (Full-Non-Magic-Pack)
        MseStyleOption(
            stylesheetId = "standard",
            gameId = "yugioh",
            displayName = "Series 10 Standard (59×86 mm)",
            packSplit = "Full-Non-Magic-Pack",
            dpiLabel = "Hi-Res 300 DPI • 813×1185",
            cardWidthPx = 813,
            cardHeightPx = 1185,
            cardDpi = 300,
            engineSplit = EngineSplit.MSE_LEGACY,
            physicalProfile = PhysicalCardProfile.JAPANESE_59X86,
        ),
        MseStyleOption(
            stylesheetId = "rush-duel",
            gameId = "yugioh",
            displayName = "Rush Duel",
            packSplit = "Full-Non-Magic-Pack",
            dpiLabel = "150 DPI • 421×614",
            cardWidthPx = 421,
            cardHeightPx = 614,
            cardDpi = 150,
            engineSplit = EngineSplit.MSE_LEGACY,
            physicalProfile = PhysicalCardProfile.JAPANESE_59X86,
        ),
        // Pokémon (Full-Non-Magic-Pack)
        MseStyleOption(
            stylesheetId = "scarlet-violet",
            gameId = "pokemon",
            displayName = "Scarlet & Violet Modern",
            packSplit = "Full-Non-Magic-Pack",
            dpiLabel = "Hi-Res 300 DPI • 747×1038",
            cardWidthPx = 747,
            cardHeightPx = 1038,
            cardDpi = 300,
            engineSplit = EngineSplit.MSE_LEGACY,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseStyleOption(
            stylesheetId = "sun-moon",
            gameId = "pokemon",
            displayName = "Sun & Moon",
            packSplit = "Full-Non-Magic-Pack",
            dpiLabel = "150 DPI • 375×523",
            cardWidthPx = 375,
            cardHeightPx = 523,
            cardDpi = 150,
            engineSplit = EngineSplit.MSE_LEGACY,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        // Inscryption (Full-Non-Magic-Pack)
        MseStyleOption(
            stylesheetId = "act1-leshy",
            gameId = "inscryption",
            displayName = "Act I Leshy Parchment",
            packSplit = "Full-Non-Magic-Pack",
            dpiLabel = "150 DPI • 375×523",
            cardWidthPx = 375,
            cardHeightPx = 523,
            cardDpi = 150,
            engineSplit = EngineSplit.MSE_LEGACY,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        MseStyleOption(
            stylesheetId = "act3-po3",
            gameId = "inscryption",
            displayName = "Act III PO3 Tech Frame",
            packSplit = "Full-Non-Magic-Pack",
            dpiLabel = "150 DPI • 375×523",
            cardWidthPx = 375,
            cardHeightPx = 523,
            cardDpi = 150,
            engineSplit = EngineSplit.MSE_LEGACY,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
        // Rage (Full-Non-Magic-Pack)
        MseStyleOption(
            stylesheetId = "classic",
            gameId = "rage",
            displayName = "Rage Classic",
            packSplit = "Full-Non-Magic-Pack",
            dpiLabel = "150 DPI • 375×523",
            cardWidthPx = 375,
            cardHeightPx = 523,
            cardDpi = 150,
            engineSplit = EngineSplit.MSE_LEGACY,
            physicalProfile = PhysicalCardProfile.STANDARD_63X88,
        ),
    )

    fun resolveGames(bridgeTemplates: List<BridgeTemplateDto>): List<MseGameOption> {
        val byId = linkedMapOf<String, MseGameOption>()
        baseGames.forEach { byId[it.gameId.lowercase()] = it }
        bridgeTemplates.forEach { dto ->
            val key = dto.game.trim().lowercase()
            if (key.isNotEmpty() && !byId.containsKey(key)) {
                byId[key] = MseGameOption(
                    gameId = key,
                    displayName = key.replaceFirstChar { it.uppercase() }.replace('-', ' '),
                    subtitle = "Synced from ${dto.sourceSplit.ifBlank { "MSE Bridge" }}",
                    defaultPackSplit = dto.sourceSplit.ifBlank { "MSE Bridge" },
                    defaultEngineSplit = if (key == "magic") EngineSplit.MSE_MODERN else EngineSplit.MSE_LEGACY,
                    defaultPhysicalProfile = PhysicalCardProfile.STANDARD_63X88,
                )
            }
        }
        return byId.values.toList()
    }

    fun resolveStylesForGame(
        gameId: String,
        bridgeTemplates: List<BridgeTemplateDto>,
    ): List<MseStyleOption> {
        val normGame = gameId.trim().lowercase()
        val result = linkedMapOf<String, MseStyleOption>()
        baseStyles.filter { it.gameId == normGame }.forEach { style ->
            result[style.stylesheetId.lowercase()] = style
        }
        bridgeTemplates
            .filter { it.game.trim().equals(normGame, ignoreCase = true) }
            .forEach { dto ->
                val cleanStyleId = dto.id
                    .removePrefix("$normGame-")
                    .removeSuffix(".mse-style")
                val key = cleanStyleId.lowercase()
                if (!result.containsKey(key)) {
                    result[key] = MseStyleOption(
                        stylesheetId = cleanStyleId,
                        gameId = normGame,
                        displayName = dto.shortName.ifBlank { cleanStyleId },
                        packSplit = if (dto.isCustom) "Custom Template" else dto.sourceSplit.ifBlank { "Synced Pack" },
                        dpiLabel = "Template Native DPI",
                        cardWidthPx = 375,
                        cardHeightPx = 523,
                        cardDpi = 150,
                        engineSplit = if (normGame == "magic") EngineSplit.MSE_MODERN else EngineSplit.MSE_LEGACY,
                        physicalProfile = if (normGame == "yugioh") {
                            PhysicalCardProfile.JAPANESE_59X86
                        } else {
                            PhysicalCardProfile.STANDARD_63X88
                        },
                    )
                }
            }
        return result.values.toList()
    }

    fun displayGameName(gameId: String): String =
        baseGames.firstOrNull { it.gameId.equals(gameId, ignoreCase = true) }?.displayName
            ?: gameId.replaceFirstChar { it.uppercase() }
}
