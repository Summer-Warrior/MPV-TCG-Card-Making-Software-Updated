package com.example.domain.nexus

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * Runtime-validated schemas for the Bidirectional GitHub Sync Bridge
 * connecting MPV TCG Card Making Software <-> Nexus Master Hub.
 *
 * Pure Kotlin domain layer — zero Android UI / Compose imports.
 */

enum class MutualLinkStatus(val wireValue: String, val displayLabel: String) {
    UNLINKED("UNLINKED", "Unlinked"),
    ONE_WAY_ONLY("ONE_WAY_ONLY", "One-Way Only"),
    LINKED_OUT_OF_SYNC("LINKED_OUT_OF_SYNC", "Linked (Out of Sync)"),
    LINKED_AND_CURRENT("LINKED_AND_CURRENT", "Linked & Current"),
}

enum class CardEditorMode(val wireValue: String, val displayLabel: String) {
    MATCH_GAMEPLAY("MATCH_GAMEPLAY", "Match Gameplay Card"),
    AVATAR_PROFILE_MEMORIAL_DOSSIER(
        "AVATAR_PROFILE_MEMORIAL_DOSSIER",
        "Avatar Profile / Memorial Forge Dossier",
    ),
}

enum class ClientCategory(val wireValue: String, val displayLabel: String) {
    ULTIMATE_TCG_WIP("ULTIMATE_TCG_WIP", "Ultimate TCG (WIP)"),
    CUSTOM_21_DIGIT_CLONE("CUSTOM_21_DIGIT_CLONE", "21-Digit Custom Clone"),
    WEB_TABLETOP("WEB_TABLETOP", "Built-In Web Tabletop"),
    NOTEBOOK_REALM("NOTEBOOK_REALM", "Notebook Realm"),
    TCG_VIDEO_GAME("TCG_VIDEO_GAME", "TCG Video Game"),
    MINI_GAME("MINI_GAME", "Mini-Game"),
    NICHE_DEFUNCT("NICHE_DEFUNCT", "Niche / Defunct Blueprint"),
    AI_USER_COMBO("AI_USER_COMBO", "AI / User Combo"),
    LOCKED_UPSTREAM_REFERENCE("LOCKED_UPSTREAM_REFERENCE", "Locked Upstream Reference"),
}

@JsonClass(generateAdapter = true)
data class NexusRepoLinkManifest(
    @Json(name = "thisRepo") val thisRepo: String = "Summer-Warrior/MPV-TCG-Card-Making-Software-Updated",
    @Json(name = "thisBranch") val thisBranch: String = "main",
    @Json(name = "linkedHubRepo") val linkedHubRepo: String = "Summer-Warrior/Nexus-Master-Hub",
    @Json(name = "linkedHubBranch") val linkedHubBranch: String = "main",
    @Json(name = "bridgeSchemaVersion") val bridgeSchemaVersion: String = "1.0.0",
    @Json(name = "hubIndexPath") val hubIndexPath: String = "nexus-bridge/client-codestring-index.json",
    @Json(name = "exportedPacksDir") val exportedPacksDir: String = "nexus-bridge/card-packs/",
)

@JsonClass(generateAdapter = true)
data class RemoteHubLinkManifest(
    @Json(name = "thisRepo") val thisRepo: String? = null,
    @Json(name = "thisBranch") val thisBranch: String? = null,
    @Json(name = "linkedCardMakerRepo") val linkedCardMakerRepo: String? = null,
    @Json(name = "linkedRepo") val linkedRepo: String? = null,
    @Json(name = "linkedRepos") val linkedRepos: List<String> = emptyList(),
    @Json(name = "bridgeSchemaVersion") val bridgeSchemaVersion: String? = null,
    @Json(name = "hubIndexPath") val hubIndexPath: String? = null,
    @Json(name = "indexRevision") val indexRevision: String? = null,
)

@JsonClass(generateAdapter = true)
data class NexusClientDefinition(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String = ClientCategory.CUSTOM_21_DIGIT_CLONE.wireValue,
    @Json(name = "upstreamSourceLock") val upstreamSourceLock: Boolean = false,
    @Json(name = "upstreamRepo") val upstreamRepo: String? = null,
    @Json(name = "nonLockedForkId") val nonLockedForkId: String? = null,
    @Json(name = "idFormat") val idFormat: String = "21-digit-numeric",
    @Json(name = "zones") val zones: List<String> = emptyList(),
    @Json(name = "resourcePools") val resourcePools: List<String> = emptyList(),
    @Json(name = "attributes") val attributes: List<String> = emptyList(),
    @Json(name = "codeStringTemplate") val codeStringTemplate: String = "",
    @Json(name = "scriptLanguage") val scriptLanguage: String = "lua",
    @Json(name = "version") val version: String = "1.0.0",
    @Json(name = "notes") val notes: String = "",
)

@JsonClass(generateAdapter = true)
data class NotebookFormatEntry(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "realm") val realm: String,
    @Json(name = "rulesSummary") val rulesSummary: String = "",
    @Json(name = "version") val version: String = "1.0.0",
)

@JsonClass(generateAdapter = true)
data class AiUserComboEntry(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "sourceClients") val sourceClients: List<String> = emptyList(),
    @Json(name = "version") val version: String = "1.0.0",
)

@JsonClass(generateAdapter = true)
data class NexusClientCodestringIndex(
    @Json(name = "schemaVersion") val schemaVersion: String = "1.0.0",
    @Json(name = "indexRevision") val indexRevision: String = "local-seed-1",
    @Json(name = "updatedAt") val updatedAt: String = "2026-11-22T00:00:00Z",
    @Json(name = "hubRepo") val hubRepo: String = "Summer-Warrior/Nexus-Master-Hub",
    @Json(name = "colorPieGrammarVersion") val colorPieGrammarVersion: String = "20-color-canonical-v1",
    @Json(name = "clients") val clients: List<NexusClientDefinition> = emptyList(),
    @Json(name = "notebookFormats") val notebookFormats: List<NotebookFormatEntry> = emptyList(),
    @Json(name = "aiUserCombos") val aiUserCombos: List<AiUserComboEntry> = emptyList(),
)

data class NexusHubChangelogDiff(
    val fromRevision: String,
    val toRevision: String,
    val addedClients: List<NexusClientDefinition>,
    val updatedClients: List<NexusClientDefinition>,
    val removedClientIds: List<String>,
    val addedNotebookFormats: List<NotebookFormatEntry>,
    val updatedNotebookFormats: List<NotebookFormatEntry>,
    val addedCombos: List<AiUserComboEntry>,
    val updatedCombos: List<AiUserComboEntry>,
) {
    val hasChanges: Boolean
        get() = addedClients.isNotEmpty() ||
            updatedClients.isNotEmpty() ||
            removedClientIds.isNotEmpty() ||
            addedNotebookFormats.isNotEmpty() ||
            updatedNotebookFormats.isNotEmpty() ||
            addedCombos.isNotEmpty() ||
            updatedCombos.isNotEmpty()

    val totalChangeCount: Int
        get() = addedClients.size +
            updatedClients.size +
            removedClientIds.size +
            addedNotebookFormats.size +
            updatedNotebookFormats.size +
            addedCombos.size +
            updatedCombos.size
}

@JsonClass(generateAdapter = true)
data class UltimateTcgManualFields(
    @Json(name = "batch2ResourceNote") val batch2ResourceNote: String = "",
    @Json(name = "batch3ResourceNote") val batch3ResourceNote: String = "",
    @Json(name = "subDeckDragNote") val subDeckDragNote: String = "",
    @Json(name = "manaNumberSplitNote") val manaNumberSplitNote: String = "",
)

@JsonClass(generateAdapter = true)
data class NexusCodedCard(
    @Json(name = "cardId") val cardId: String,
    @Json(name = "register21Digit") val register21Digit: String? = null,
    @Json(name = "targetClientId") val targetClientId: String,
    @Json(name = "cardName") val cardName: String,
    @Json(name = "cardType") val cardType: String = "",
    @Json(name = "rulesText") val rulesText: String = "",
    @Json(name = "colorPiePips") val colorPiePips: List<String> = emptyList(),
    @Json(name = "editorMode") val editorMode: String = CardEditorMode.MATCH_GAMEPLAY.wireValue,
    @Json(name = "allowTimeTravelFlagsOnMatchCards") val allowTimeTravelFlagsOnMatchCards: Boolean = false,
    @Json(name = "flavorFilters") val flavorFilters: List<Int> = emptyList(),
    @Json(name = "temporalEffects") val temporalEffects: List<String> = emptyList(),
    @Json(name = "codingVariables") val codingVariables: Map<String, String> = emptyMap(),
    @Json(name = "ultimateTcgManual") val ultimateTcgManual: UltimateTcgManualFields = UltimateTcgManualFields(),
    @Json(name = "image") val image: String? = null,
    @Json(name = "generatedLua") val generatedLua: String? = null,
)

@JsonClass(generateAdapter = true)
data class NexusCardPackExport(
    @Json(name = "bridgeSchemaVersion") val bridgeSchemaVersion: String = "1.0.0",
    @Json(name = "sourceRepo") val sourceRepo: String,
    @Json(name = "targetHubRepo") val targetHubRepo: String,
    @Json(name = "targetClientId") val targetClientId: String,
    @Json(name = "upstreamSourceLock") val upstreamSourceLock: Boolean = false,
    @Json(name = "packId") val packId: String,
    @Json(name = "packName") val packName: String,
    @Json(name = "exportedAt") val exportedAt: String,
    @Json(name = "cards") val cards: List<NexusCodedCard>,
)

/**
 * Exact canonical 20-Color Pie pip catalog.
 * Strictly zero invented philosophies, emotions, or moral alignments.
 */
data class ColorPiePipSpec(
    val code: String,
    val label: String,
    val layer: Int,
)

object TwentyColorPieCatalog {
    val colorlessPip = ColorPiePipSpec(code = "{C}", label = "Colorless", layer = 0)

    val baseTwentyColors: List<ColorPiePipSpec> = listOf(
        ColorPiePipSpec("{W}", "White", 1),
        ColorPiePipSpec("{U}", "Blue", 1),
        ColorPiePipSpec("{B}", "Black", 1),
        ColorPiePipSpec("{R}", "Red", 1),
        ColorPiePipSpec("{G}", "Green", 1),
        ColorPiePipSpec("{P}", "Purple", 1),
        ColorPiePipSpec("{Y}", "Yellow", 1),
        ColorPiePipSpec("{O}", "Orange", 1),
        ColorPiePipSpec("{Cy}", "Cyan", 1),
        ColorPiePipSpec("{Pk}", "Pink", 1),
        ColorPiePipSpec("{Br}", "Brown", 1),
        ColorPiePipSpec("{Te}", "Teal", 1),
        ColorPiePipSpec("{In}", "Indigo", 1),
        ColorPiePipSpec("{Mg}", "Magenta", 1),
        ColorPiePipSpec("{Li}", "Lime", 1),
        ColorPiePipSpec("{Sl}", "Silver", 1),
        ColorPiePipSpec("{Gd}", "Gold", 1),
        ColorPiePipSpec("{Cr}", "Crimson", 1),
        ColorPiePipSpec("{Ob}", "Obsidian", 1),
        ColorPiePipSpec("{Aq}", "Aqua", 1),
    )

    val valueStates: List<Pair<String, String>> = listOf(
        ":T" to "Tint (:T)",
        ":S" to "Shade (:S)",
        ":M" to "Muted (:M)",
    )

    val layer20OmegaPips: List<ColorPiePipSpec> = listOf(
        ColorPiePipSpec("{Ω·M}", "Omega M", 20),
        ColorPiePipSpec("{Ω·C}", "Omega C", 20),
        ColorPiePipSpec("{Ω·S}", "Omega S", 20),
        ColorPiePipSpec("{Ω·V}", "Omega V", 20),
        ColorPiePipSpec("{Ω·E}", "Omega E", 20),
    )

    val baseSymbols: Set<String> = baseTwentyColors.map { it.code.removeSurrounding("{", "}") }.toSet()
    val omegaCodes: Set<String> = layer20OmegaPips.map { it.code }.toSet()

    fun buildFusedPair(firstCode: String, secondCode: String): String {
        val a = firstCode.removeSurrounding("{", "}")
        val b = secondCode.removeSurrounding("{", "}")
        return "{$a·$b}"
    }

    fun buildChoiceSplit(firstCode: String, secondCode: String): String {
        val a = firstCode.removeSurrounding("{", "}")
        val b = secondCode.removeSurrounding("{", "}")
        return "{$a/$b}"
    }

    fun buildWeightedLead(leadCode: String, leadWeight: Int, secondaryCode: String): String {
        val safeWeight = leadWeight.coerceIn(2, 9)
        val a = leadCode.removeSurrounding("{", "}")
        val b = secondaryCode.removeSurrounding("{", "}")
        return "{$safeWeight$a/$b}"
    }

    fun applyValueState(pipCode: String, stateSuffix: String): String {
        val inner = pipCode.removeSurrounding("{", "}").substringBefore(":")
        return "{$inner$stateSuffix}"
    }
}

sealed class SchemaValidationResult<out T> {
    data class Valid<T>(val value: T) : SchemaValidationResult<T>()
    data class Invalid(val errors: List<String>) : SchemaValidationResult<Nothing>()
}

object NexusSchemaValidator {
    private val repoSlugRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private val semverRegex = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
    private val safeRelPathRegex = Regex("^[A-Za-z0-9_./-]+$")

    fun validateRepoSlug(slug: String): Boolean = repoSlugRegex.matches(slug.trim())

    fun validateRepoLinkManifest(manifest: NexusRepoLinkManifest): SchemaValidationResult<NexusRepoLinkManifest> {
        val errors = mutableListOf<String>()
        if (!validateRepoSlug(manifest.thisRepo)) {
            errors += "thisRepo must be formatted as 'owner/repo'."
        }
        if (manifest.thisBranch.isBlank() || manifest.thisBranch.contains("..")) {
            errors += "thisBranch must be a non-empty branch name."
        }
        if (!validateRepoSlug(manifest.linkedHubRepo)) {
            errors += "linkedHubRepo must be formatted as 'owner/repo'."
        }
        if (manifest.linkedHubBranch.isBlank() || manifest.linkedHubBranch.contains("..")) {
            errors += "linkedHubBranch must be a non-empty branch name."
        }
        if (!semverRegex.matches(manifest.bridgeSchemaVersion.trim())) {
            errors += "bridgeSchemaVersion must follow semantic versioning (e.g., 1.0.0)."
        }
        if (!isSafeRepoPath(manifest.hubIndexPath)) {
            errors += "hubIndexPath contains invalid path segments."
        }
        if (!isSafeRepoPath(manifest.exportedPacksDir)) {
            errors += "exportedPacksDir contains invalid path segments."
        }
        return if (errors.isEmpty()) {
            SchemaValidationResult.Valid(manifest)
        } else {
            SchemaValidationResult.Invalid(errors)
        }
    }

    fun parseAndValidateIndex(
        moshi: Moshi,
        rawJson: String,
    ): SchemaValidationResult<NexusClientCodestringIndex> {
        val adapter = moshi.adapter(NexusClientCodestringIndex::class.java)
        val parsed = try {
            adapter.fromJson(rawJson)
        } catch (ex: Exception) {
            return SchemaValidationResult.Invalid(
                listOf("JSON schema parse failure: ${ex.message ?: ex::class.java.simpleName}")
            )
        } ?: return SchemaValidationResult.Invalid(listOf("Empty client-codestring-index.json payload."))

        val errors = mutableListOf<String>()
        if (!semverRegex.matches(parsed.schemaVersion.trim())) {
            errors += "schemaVersion '${parsed.schemaVersion}' is not valid semver."
        }
        if (parsed.clients.isEmpty()) {
            errors += "Index must declare at least one client definition."
        }
        val seenIds = mutableSetOf<String>()
        parsed.clients.forEachIndexed { idx, client ->
            if (client.id.isBlank()) {
                errors += "clients[$idx].id cannot be blank."
            } else if (!seenIds.add(client.id)) {
                errors += "Duplicate client id '${client.id}'."
            }
            if (client.name.isBlank()) {
                errors += "clients[$idx].name cannot be blank."
            }
        }
        return if (errors.isEmpty()) {
            SchemaValidationResult.Valid(parsed)
        } else {
            SchemaValidationResult.Invalid(errors)
        }
    }

    fun computeChangelogDiff(
        current: NexusClientCodestringIndex,
        incoming: NexusClientCodestringIndex,
    ): NexusHubChangelogDiff {
        val currentClients = current.clients.associateBy { it.id }
        val incomingClients = incoming.clients.associateBy { it.id }

        val addedClients = incoming.clients.filter { !it.upstreamSourceLock && it.id !in currentClients }
        val updatedClients = incoming.clients.filter {
            !it.upstreamSourceLock && currentClients[it.id] != null && currentClients[it.id] != it
        }
        val removedClientIds = current.clients
            .filter { !it.upstreamSourceLock && it.id !in incomingClients }
            .map { it.id }

        val currentFormats = current.notebookFormats.associateBy { it.id }
        val addedFormats = incoming.notebookFormats.filter { it.id !in currentFormats }
        val updatedFormats = incoming.notebookFormats.filter {
            currentFormats[it.id] != null && currentFormats[it.id] != it
        }

        val currentCombos = current.aiUserCombos.associateBy { it.id }
        val addedCombos = incoming.aiUserCombos.filter { it.id !in currentCombos }
        val updatedCombos = incoming.aiUserCombos.filter {
            currentCombos[it.id] != null && currentCombos[it.id] != it
        }

        return NexusHubChangelogDiff(
            fromRevision = current.indexRevision,
            toRevision = incoming.indexRevision,
            addedClients = addedClients,
            updatedClients = updatedClients,
            removedClientIds = removedClientIds,
            addedNotebookFormats = addedFormats,
            updatedNotebookFormats = updatedFormats,
            addedCombos = addedCombos,
            updatedCombos = updatedCombos,
        )
    }

    fun isSafeRepoPath(path: String): Boolean {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.contains("..") || trimmed.contains("\\")) {
            return false
        }
        return safeRelPathRegex.matches(trimmed)
    }
}
