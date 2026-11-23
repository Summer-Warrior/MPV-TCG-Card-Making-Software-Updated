package com.example.data.mse

import com.example.data.settings.EngineSplit
import com.example.data.settings.PhysicalCardProfile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Pure helper utilities for MSE template pack routing, URL building, and display naming.
 * Zero hardcoded lists of games or styles — all games and styles are dynamically discovered
 * by [MseTemplateCatalogRepository] from MagicSetEditorPacks and the local MSE Bridge.
 */
object MseTemplateCatalog {

    fun displayGameName(
        gameId: String,
        discoveredGames: List<DiscoveredGame> = emptyList(),
    ): String {
        val norm = gameId.trim().lowercase()
        discoveredGames.firstOrNull { it.gameId.equals(norm, ignoreCase = true) }?.let {
            return it.displayName
        }
        return when (norm) {
            "magic" -> "Magic: The Gathering"
            "yugioh" -> "Yu-Gi-Oh!"
            "pokemon" -> "Pokémon"
            else -> norm.split('-', '_')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { ch -> ch.uppercase() }
                }
                .ifBlank { "Card Game" }
        }
    }

    fun engineSplitForGame(gameId: String, packRepo: String = ""): EngineSplit {
        return if (
            gameId.equals("magic", ignoreCase = true) ||
            packRepo.equals("Full-Magic-Pack", ignoreCase = true) ||
            packRepo.equals("Beta-Update-Pack", ignoreCase = true)
        ) {
            EngineSplit.MSE_MODERN
        } else {
            EngineSplit.MSE_LEGACY
        }
    }

    fun physicalProfileForGame(gameId: String): PhysicalCardProfile {
        val norm = gameId.lowercase()
        return if (norm.contains("yugioh") || norm.contains("vanguard") || norm.contains("battle-spirits")) {
            PhysicalCardProfile.JAPANESE_59X86
        } else {
            PhysicalCardProfile.STANDARD_63X88
        }
    }

    fun encodePathSegments(rawPath: String): String {
        return rawPath.trimStart('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
            }
    }

    fun buildRawDataUrl(
        repo: String,
        branch: String,
        folderName: String,
        relativeFile: String,
    ): String {
        val cleanFile = relativeFile.trim().trim('"')
        return if (cleanFile.startsWith("/")) {
            // Cross-package reference like `/magic-m15-altered.mse-style/wcard.jpg`
            val encoded = encodePathSegments(cleanFile.trimStart('/'))
            "https://raw.githubusercontent.com/${CatalogPackConfig.ORG}/$repo/$branch/data/$encoded"
        } else {
            val encodedFolder = encodePathSegments(folderName)
            val encodedFile = encodePathSegments(cleanFile)
            "https://raw.githubusercontent.com/${CatalogPackConfig.ORG}/$repo/$branch/data/$encodedFolder/$encodedFile"
        }
    }

    fun buildRawRepoFileUrl(
        repo: String,
        branch: String,
        repoRelativePath: String,
    ): String {
        val encoded = encodePathSegments(repoRelativePath)
        return "https://raw.githubusercontent.com/${CatalogPackConfig.ORG}/$repo/$branch/$encoded"
    }
}
