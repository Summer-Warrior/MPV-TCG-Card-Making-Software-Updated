package com.example.data.nexus

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.nexus.AiUserComboEntry
import com.example.domain.nexus.ClientCategory
import com.example.domain.nexus.NexusCardPackExport
import com.example.domain.nexus.NexusClientCodestringIndex
import com.example.domain.nexus.NexusClientDefinition
import com.example.domain.nexus.NexusCodedCard
import com.example.domain.nexus.NexusRepoLinkManifest
import com.example.domain.nexus.NotebookFormatEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

private val Context.nexusHubDataStore by preferencesDataStore(name = "nexus_hub_bridge_prefs")

/**
 * Local persistence for `nexus-repo-link.json`, applied Nexus Master Hub client-codestring index,
 * exported coded cards, and opt-in Fine-Grained GitHub PAT storage (session-only by default).
 */
class NexusHubRepository(
    private val context: Context,
    private val moshi: Moshi,
) {
    private val storageDir = File(context.filesDir, "nexus_hub").apply { mkdirs() }
    private val linkManifestFile = File(storageDir, "nexus-repo-link.json")
    private val appliedIndexFile = File(storageDir, "client-codestring-index.json")
    private val codedCardsFile = File(storageDir, "coded_cards.json")

    private val linkAdapter = moshi.adapter(NexusRepoLinkManifest::class.java).indent("  ")
    private val indexAdapter = moshi.adapter(NexusClientCodestringIndex::class.java).indent("  ")
    private val packAdapter = moshi.adapter(NexusCardPackExport::class.java).indent("  ")
    private val codedCardsAdapter = moshi.adapter<List<NexusCodedCard>>(
        Types.newParameterizedType(List::class.java, NexusCodedCard::class.java)
    ).indent("  ")

    private object Keys {
        val REMEMBER_PAT = booleanPreferencesKey("remember_pat_opt_in")
        val STORED_PAT = stringPreferencesKey("opt_in_fine_grained_pat")
    }

    suspend fun loadManifest(): NexusRepoLinkManifest = withContext(Dispatchers.IO) {
        if (!linkManifestFile.exists()) {
            val defaultManifest = NexusRepoLinkManifest()
            saveManifest(defaultManifest)
            return@withContext defaultManifest
        }
        runCatching {
            linkAdapter.fromJson(linkManifestFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: NexusRepoLinkManifest()
    }

    suspend fun saveManifest(manifest: NexusRepoLinkManifest) = withContext(Dispatchers.IO) {
        storageDir.mkdirs()
        val tmp = File(storageDir, "nexus-repo-link.json.tmp")
        tmp.writeText(linkAdapter.toJson(manifest) + "\n", Charsets.UTF_8)
        tmp.renameTo(linkManifestFile)
    }

    fun serializeManifestJson(manifest: NexusRepoLinkManifest): String = linkAdapter.toJson(manifest) + "\n"

    fun serializeCardPackJson(pack: NexusCardPackExport): String = packAdapter.toJson(pack) + "\n"

    suspend fun loadAppliedIndex(): NexusClientCodestringIndex = withContext(Dispatchers.IO) {
        if (!appliedIndexFile.exists()) {
            val seed = buildSeedClientIndex()
            saveAppliedIndex(seed)
            return@withContext seed
        }
        runCatching {
            indexAdapter.fromJson(appliedIndexFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: buildSeedClientIndex()
    }

    suspend fun saveAppliedIndex(index: NexusClientCodestringIndex) = withContext(Dispatchers.IO) {
        storageDir.mkdirs()
        val tmp = File(storageDir, "client-codestring-index.json.tmp")
        tmp.writeText(indexAdapter.toJson(index) + "\n", Charsets.UTF_8)
        tmp.renameTo(appliedIndexFile)
    }

    suspend fun loadSavedCodedCards(): List<NexusCodedCard> = withContext(Dispatchers.IO) {
        if (!codedCardsFile.exists()) return@withContext emptyList()
        runCatching {
            codedCardsAdapter.fromJson(codedCardsFile.readText(Charsets.UTF_8))
        }.getOrNull().orEmpty()
    }

    suspend fun saveCodedCards(cards: List<NexusCodedCard>) = withContext(Dispatchers.IO) {
        storageDir.mkdirs()
        val tmp = File(storageDir, "coded_cards.json.tmp")
        tmp.writeText(codedCardsAdapter.toJson(cards) + "\n", Charsets.UTF_8)
        tmp.renameTo(codedCardsFile)
    }

    suspend fun loadOptInPatPreference(): Pair<Boolean, String> {
        val prefs = context.nexusHubDataStore.data.first()
        val remember = prefs[Keys.REMEMBER_PAT] ?: false
        val token = if (remember) prefs[Keys.STORED_PAT].orEmpty() else ""
        return remember to token
    }

    suspend fun updateOptInPatPreference(remember: Boolean, token: String) {
        context.nexusHubDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_PAT] = remember
            if (remember && token.isNotBlank()) {
                prefs[Keys.STORED_PAT] = token.trim()
            } else {
                prefs.remove(Keys.STORED_PAT)
            }
        }
    }

    companion object {
        /**
         * Canonical baseline Nexus Master Hub index seed containing non-locked clients
         * (upstreamSourceLock = false) and read-only locked upstream reference entries
         * (upstreamSourceLock = true) until overwritten by live Hub sync.
         */
        fun buildSeedClientIndex(): NexusClientCodestringIndex = NexusClientCodestringIndex(
            schemaVersion = "1.0.0",
            indexRevision = "local-seed-1.0.0",
            updatedAt = "2026-11-22T00:00:00Z",
            hubRepo = "Summer-Warrior/Nexus-Master-Hub",
            colorPieGrammarVersion = "20-color-canonical-v1",
            clients = listOf(
                NexusClientDefinition(
                    id = "ultimate-tcg-wip",
                    name = "Ultimate TCG (WIP)",
                    category = ClientCategory.ULTIMATE_TCG_WIP.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "21-digit-numeric",
                    zones = listOf("Main Deck", "Sub-Deck", "Field", "Graveyard", "Exile", "Resource Pool"),
                    resourcePools = listOf("20-Color Pie", "Batch 2 Resource (Manual)", "Batch 3 Resource (Manual)"),
                    attributes = listOf("Colorless", "20-Color", "Layer-2 Fused", "Layer-20 Omega"),
                    codeStringTemplate = "c<21-digits>.lua",
                    notes = "Modular WIP systems (Batch 2/3 resources, Sub-Deck dragging, Mana Number splits) remain manual-friendly.",
                ),
                NexusClientDefinition(
                    id = "custom-21digit-duel-clone",
                    name = "21-Digit Custom Duel Project Clone",
                    category = ClientCategory.CUSTOM_21_DIGIT_CLONE.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "21-digit-numeric",
                    zones = listOf("Main Deck", "Extra Deck", "Monster Zone", "Spell/Trap Zone", "GY", "Banished"),
                    resourcePools = listOf("LP", "Counters", "Register-21"),
                    attributes = listOf("DARK", "LIGHT", "EARTH", "WATER", "FIRE", "WIND", "DIVINE"),
                    codeStringTemplate = "c<21-digits>.lua",
                    notes = "Non-locked 21-digit custom fork target for Duel Monsters style coding and Lua script export.",
                ),
                NexusClientDefinition(
                    id = "custom-21digit-planar-clone",
                    name = "21-Digit Custom Planar Tabletop Clone",
                    category = ClientCategory.CUSTOM_21_DIGIT_CLONE.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "21-digit-numeric",
                    zones = listOf("Library", "Hand", "Battlefield", "Graveyard", "Exile", "Command", "Planar Deck"),
                    resourcePools = listOf("20-Color Mana", "Energy", "Life"),
                    attributes = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Plane"),
                    codeStringTemplate = "pack-json",
                    notes = "Non-locked 21-digit fork for planar tabletop card coding and JSON pack export.",
                ),
                NexusClientDefinition(
                    id = "built-in-web-tabletop",
                    name = "Built-In Web Tabletop Blueprint",
                    category = ClientCategory.WEB_TABLETOP.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "uuid-or-21-digit",
                    zones = listOf("Deck", "Hand", "Board", "Discard", "Staging"),
                    resourcePools = listOf("Action Points", "20-Color Pie"),
                    attributes = listOf("Unit", "Action", "Event", "Relic"),
                    codeStringTemplate = "pack-json",
                ),
                NexusClientDefinition(
                    id = "notebook-duel-monsters",
                    name = "Notebook Realm: Duel Monsters Formats",
                    category = ClientCategory.NOTEBOOK_REALM.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "21-digit-numeric",
                    zones = listOf("Main Deck", "Extra Deck", "Field", "Graveyard", "Banished"),
                    resourcePools = listOf("LP", "Speed Counters"),
                    attributes = listOf("Monster", "Spell", "Trap"),
                    codeStringTemplate = "c<21-digits>.lua",
                ),
                NexusClientDefinition(
                    id = "notebook-nested-planar-mtg",
                    name = "Notebook Realm: Nested-Planar MTG",
                    category = ClientCategory.NOTEBOOK_REALM.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "21-digit-numeric",
                    zones = listOf("Library", "Battlefield", "Nested Plane Stack", "Graveyard", "Exile"),
                    resourcePools = listOf("20-Color Pie", "Planar Charge"),
                    attributes = listOf("Permanent", "Spell", "Nested Plane"),
                    codeStringTemplate = "pack-json",
                ),
                NexusClientDefinition(
                    id = "notebook-planet-cray-vanguard",
                    name = "Notebook Realm: Planet Cray Vanguard",
                    category = ClientCategory.NOTEBOOK_REALM.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "21-digit-numeric",
                    zones = listOf("Deck", "Vanguard Circle", "Rear-Guard Circle", "Damage Zone", "Soul", "Drop"),
                    resourcePools = listOf("Counter Blast", "Soul Blast", "Energy"),
                    attributes = listOf("Grade 0", "Grade 1", "Grade 2", "Grade 3", "Order"),
                    codeStringTemplate = "pack-json",
                ),
                NexusClientDefinition(
                    id = "tcg-video-game-blueprint",
                    name = "TCG Video Game Blueprint",
                    category = ClientCategory.TCG_VIDEO_GAME.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "8-digit-or-21-digit",
                    zones = listOf("Deck", "Arena", "Bench", "Discard"),
                    resourcePools = listOf("AP", "Turn Gauge"),
                    attributes = listOf("Character", "Skill", "Item"),
                    codeStringTemplate = "pack-json",
                ),
                NexusClientDefinition(
                    id = "mini-games-blueprint",
                    name = "Mini-Games Card Blueprint",
                    category = ClientCategory.MINI_GAME.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "arbitrary-string",
                    zones = listOf("Draw Pile", "Table", "Score Pile"),
                    resourcePools = listOf("Points", "Tokens"),
                    attributes = listOf("Action", "Modifier", "Hazard"),
                    codeStringTemplate = "pack-json",
                ),
                NexusClientDefinition(
                    id = "niche-defunct-blueprint",
                    name = "Niche / Defunct TCG Blueprint",
                    category = ClientCategory.NICHE_DEFUNCT.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "arbitrary-string",
                    zones = listOf("Deck", "Active Zone", "Reserve", "Discard"),
                    resourcePools = listOf("Custom Pool"),
                    attributes = listOf("Entity", "Effect"),
                    codeStringTemplate = "pack-json",
                ),
                NexusClientDefinition(
                    id = "ai-user-combo-sandbox",
                    name = "AI / User Hybrid Combo Blueprint",
                    category = ClientCategory.AI_USER_COMBO.wireValue,
                    upstreamSourceLock = false,
                    idFormat = "21-digit-numeric",
                    zones = listOf("Hybrid Deck", "Shared Field", "Graveyard", "Exile"),
                    resourcePools = listOf("20-Color Pie", "Combo Meter"),
                    attributes = listOf("Hybrid Unit", "Combo Spell", "Relic"),
                    codeStringTemplate = "c<21-digits>.lua",
                ),
                // ---- STRICTLY READ-ONLY LOCKED UPSTREAM REFERENCE CLIENTS ----
                NexusClientDefinition(
                    id = "upstream-locked-edopro",
                    name = "ProjectIgnis / EDOPro (Read-Only Upstream)",
                    category = ClientCategory.LOCKED_UPSTREAM_REFERENCE.wireValue,
                    upstreamSourceLock = true,
                    upstreamRepo = "ProjectIgnis/EDOPro",
                    nonLockedForkId = "custom-21digit-duel-clone",
                    idFormat = "8-digit-numeric",
                    zones = listOf("Main Deck", "Extra Deck", "Field", "GY", "Banished"),
                    resourcePools = listOf("LP"),
                    attributes = listOf("Monster", "Spell", "Trap"),
                    notes = "LOCKED UPSTREAM CLIENT — Read-only reference only. Card coding & export are blocked; target the non-locked 21-digit fork instead.",
                ),
                NexusClientDefinition(
                    id = "upstream-locked-forge",
                    name = "Card-Forge / Forge (Read-Only Upstream)",
                    category = ClientCategory.LOCKED_UPSTREAM_REFERENCE.wireValue,
                    upstreamSourceLock = true,
                    upstreamRepo = "Card-Forge/forge",
                    nonLockedForkId = "custom-21digit-planar-clone",
                    idFormat = "arbitrary-string",
                    zones = listOf("Library", "Hand", "Battlefield", "Graveyard", "Exile"),
                    resourcePools = listOf("Mana"),
                    attributes = listOf("Creature", "Spell", "Land"),
                    notes = "LOCKED UPSTREAM CLIENT — Read-only reference only. Card coding & export are blocked; target the non-locked 21-digit fork instead.",
                ),
            ),
            notebookFormats = listOf(
                NotebookFormatEntry(
                    id = "nb-duel-monsters-core",
                    name = "Duel Monsters Notebook Format",
                    realm = "Duel Monsters",
                    rulesSummary = "Standard match cards strictly disallow time-travel flavor flags (Filters 19-21); only End Turn / Extra Turn temporal effects allowed.",
                ),
                NotebookFormatEntry(
                    id = "nb-nested-planar-mtg",
                    name = "Nested-Planar MTG Format",
                    realm = "Nested-Planar MTG",
                    rulesSummary = "20-Color Pie pip grammar supported with modular planar stack zones.",
                ),
                NotebookFormatEntry(
                    id = "nb-planet-cray-vanguard",
                    name = "Planet Cray Vanguard Format",
                    realm = "Planet Cray Vanguard",
                    rulesSummary = "Vanguard/Rear-Guard circle format with Soul & Damage Zone tracking.",
                ),
            ),
            aiUserCombos = listOf(
                AiUserComboEntry(
                    id = "combo-ultimate-planar-duel",
                    name = "Ultimate Planar-Duel Hybrid",
                    sourceClients = listOf("ultimate-tcg-wip", "notebook-nested-planar-mtg"),
                ),
            ),
        )
    }
}
