package com.example.domain.nexus

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Pure Kotlin domain models for:
 * - nexus-app.json (Master Hub registration + Discord Lock + Admin Gate)
 * - handshake-contract.json (Two-Way Handshake + Dual-Engine Update Consent)
 * - engine-sync-snapshot.json (Live Tcg-game-engine-test schemas & bounds)
 * - docs/engine-health-status.json (Signed Master Hub Notification Handshake payload)
 * - Card Frame Reviewer & Layer 20 Omnichroma 6 Bespoke Sacred Celestial Geometries
 */

@JsonClass(generateAdapter = true)
data class DiscordLockConfig(
    @Json(name = "mode") val mode: String = "DISCORD_SIGNED_IN",
    @Json(name = "guildId") val guildId: String = "",
    @Json(name = "allowedRoleIds") val allowedRoleIds: List<String> = emptyList(),
    @Json(name = "allowedUserIds") val allowedUserIds: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MasterHubAdminGate(
    @Json(name = "adminRoleIds") val adminRoleIds: List<String> = emptyList(),
    @Json(name = "adminUserIds") val adminUserIds: List<String> = emptyList(),
    @Json(name = "adminEmails") val adminEmails: List<String> = emptyList(),
    @Json(name = "inheritFromMasterHubManifest") val inheritFromMasterHubManifest: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class NexusAppManifest(
    @Json(name = "repo") val repo: String = "Summer-Warrior/MPV-TCG-Card-Making-Software-Updated",
    @Json(name = "displayName") val displayName: String = "MPV TCG Card Making Software",
    @Json(name = "category") val category: String = "TCG_TOOL",
    @Json(name = "launchType") val launchType: String = "ANDROID_APK_AND_WEB",
    @Json(name = "webUrl") val webUrl: String = "https://summer-warrior.github.io/MPV-TCG-Card-Making-Software-Updated/",
    @Json(name = "upstreamSourceLock") val upstreamSourceLock: Boolean = false,
    @Json(name = "linkedMasterHub") val linkedMasterHub: String = "Summer-Warrior/Nexus-Master-Hub",
    @Json(name = "linkedGameEngineRepo") val linkedGameEngineRepo: String = "Summer-Warrior/Tcg-game-engine-test",
    @Json(name = "requiredDiscordLock") val requiredDiscordLock: DiscordLockConfig = DiscordLockConfig(),
    @Json(name = "masterHubAdminGate") val masterHubAdminGate: MasterHubAdminGate = MasterHubAdminGate(),
)

enum class ConsentStatus(val wireValue: String) {
    PENDING("PENDING"),
    CONSENTED("CONSENTED"),
    REJECTED("REJECTED"),
}

@JsonClass(generateAdapter = true)
data class DualConsentRecord(
    @Json(name = "status") val status: String = ConsentStatus.PENDING.wireValue,
    @Json(name = "commitSha") val commitSha: String = "",
    @Json(name = "approvedBy") val approvedBy: String = "",
    @Json(name = "timestamp") val timestamp: String = "",
)

@JsonClass(generateAdapter = true)
data class HandshakeContract(
    @Json(name = "contractVersion") val contractVersion: String = "1.0.0",
    @Json(name = "expectedProtocolHash") val expectedProtocolHash: String = "sha256:tcg-dual-engine-handshake-v1",
    @Json(name = "cardMakerRepo") val cardMakerRepo: String = "Summer-Warrior/MPV-TCG-Card-Making-Software-Updated",
    @Json(name = "gameEngineRepo") val gameEngineRepo: String = "Summer-Warrior/Tcg-game-engine-test",
    @Json(name = "masterHubRepo") val masterHubRepo: String = "Summer-Warrior/Nexus-Master-Hub",
    @Json(name = "requiredEngineFiles") val requiredEngineFiles: List<String> = DEFAULT_REQUIRED_ENGINE_FILES,
    @Json(name = "requiredCapabilities") val requiredCapabilities: List<String> = DEFAULT_REQUIRED_CAPABILITIES,
    @Json(name = "dualConsentRule") val dualConsentRule: String = "NO_SCHEMA_SYNC_OR_EXPORT_MIGRATION_WITHOUT_BOTH_CONSENTED",
    @Json(name = "cardMakerConsent") val cardMakerConsent: DualConsentRecord = DualConsentRecord(),
    @Json(name = "gameEngineConsent") val gameEngineConsent: DualConsentRecord = DualConsentRecord(),
) {
    companion object {
        val DEFAULT_REQUIRED_ENGINE_FILES = listOf(
            "firebase-blueprint.json",
            "firestore.rules",
            "app/src/main/java/com/example/data/Models.kt",
            "app/src/main/java/com/example/data/GameEngine.kt",
            "metadata.json",
            "client-codestring-index.json",
        )
        val DEFAULT_REQUIRED_CAPABILITIES = listOf(
            "21_DIGIT_BIGINT_CODES",
            "NON_LOCKED_CUSTOM_CLONES",
            "CARD_FRAME_DESCRIPTOR_MAPPING",
            "TURN_PHASE_AND_EFFECT_ENUMS",
            "20_COLOR_PIE_PIP_GRAMMAR",
        )
    }
}

@JsonClass(generateAdapter = true)
data class EngineBaseVariantSpec(
    @Json(name = "baseVariantId") val baseVariantId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "upstreamSourceLock") val upstreamSourceLock: Boolean = false,
    @Json(name = "zones") val zones: List<String> = emptyList(),
    @Json(name = "phases") val phases: List<String> = emptyList(),
    @Json(name = "resourceLabels") val resourceLabels: List<String> = emptyList(),
    @Json(name = "scriptType") val scriptType: String = "LUA_21",
)

@JsonClass(generateAdapter = true)
data class BigInt21Spec(
    @Json(name = "maxDigits") val maxDigits: Int = 21,
    @Json(name = "codeRegex") val codeRegex: String = "^[0-9]{1,21}$",
    @Json(name = "forbiddenOverflowRegex") val forbiddenOverflowRegex: String = ".*[0-9]{22}.*",
    @Json(name = "supportedFunctions") val supportedFunctions: List<String> = listOf(
        "ADD_21",
        "MUL_21",
        "SUB_21",
        "MOD_21",
        "SCALE_ATK_LP_21",
    ),
)

@JsonClass(generateAdapter = true)
data class FirestoreBoundsSpec(
    @Json(name = "maxCardNameLength") val maxCardNameLength: Int = 120,
    @Json(name = "maxCardTypeLength") val maxCardTypeLength: Int = 120,
    @Json(name = "maxRulesTextLength") val maxRulesTextLength: Int = 2000,
    @Json(name = "maxDeckSize") val maxDeckSize: Int = 100,
    @Json(name = "maxZonePlacements") val maxZonePlacements: Int = 60,
    @Json(name = "maxCodeStringLength") val maxCodeStringLength: Int = 21,
    @Json(name = "reservedDelimiters") val reservedDelimiters: List<String> = listOf("|"),
)

@JsonClass(generateAdapter = true)
data class EngineSchemaDefinitions(
    @Json(name = "cardTemplateFields") val cardTemplateFields: List<String> = listOf(
        "id",
        "codeString",
        "name",
        "cardType",
        "rulesText",
        "attack",
        "defense",
        "costPips",
        "effectType",
        "baseVariantId",
    ),
    @Json(name = "customDeckFields") val customDeckFields: List<String> = listOf(
        "deckId",
        "name",
        "baseVariantId",
        "cardIds",
        "subDeckIds",
    ),
    @Json(name = "serializedZonePlacementFields") val serializedZonePlacementFields: List<String> = listOf(
        "zoneId",
        "slotIndex",
        "cardId",
        "orientation",
        "descriptor",
    ),
    @Json(name = "turnPhases") val turnPhases: List<String> = listOf(
        "UNTAP",
        "UPKEEP",
        "DRAW",
        "STANDBY",
        "MAIN_1",
        "COMBAT",
        "BATTLE",
        "MAIN_2",
        "END",
    ),
    @Json(name = "cardEffectTypes") val cardEffectTypes: List<String> = listOf(
        "ACTIVATED",
        "TRIGGERED",
        "CONTINUOUS",
        "QUICK",
        "END_TURN",
        "EXTRA_TURN",
        "STAT_MODIFIER_21",
    ),
)

@JsonClass(generateAdapter = true)
data class EngineSyncSnapshot(
    @Json(name = "schemaVersion") val schemaVersion: String = "1.0.0",
    @Json(name = "engineRepo") val engineRepo: String = "Summer-Warrior/Tcg-game-engine-test",
    @Json(name = "engineBranch") val engineBranch: String = "main",
    @Json(name = "commitSha") val commitSha: String = "baseline-seed",
    @Json(name = "capturedAt") val capturedAt: String = "2026-11-23T00:00:00Z",
    @Json(name = "baseVariants") val baseVariants: List<EngineBaseVariantSpec> = defaultBaseVariants(),
    @Json(name = "bigInt21Spec") val bigInt21Spec: BigInt21Spec = BigInt21Spec(),
    @Json(name = "firestoreBounds") val firestoreBounds: FirestoreBoundsSpec = FirestoreBoundsSpec(),
    @Json(name = "schemaDefinitions") val schemaDefinitions: EngineSchemaDefinitions = EngineSchemaDefinitions(),
) {
    companion object {
        fun defaultBaseVariants(): List<EngineBaseVariantSpec> = listOf(
            EngineBaseVariantSpec(
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                displayName = "EDOPro Custom 21-Digit Fork",
                upstreamSourceLock = false,
                zones = listOf("Main Deck", "Extra Deck", "Monster Zone", "Spell/Trap Zone", "Field Zone", "Graveyard", "Banished"),
                phases = listOf("DRAW", "STANDBY", "MAIN_1", "BATTLE", "MAIN_2", "END"),
                resourceLabels = listOf("LP", "Counters", "Register-21"),
                scriptType = "LUA_21",
            ),
            EngineBaseVariantSpec(
                baseVariantId = "FORGE_CUSTOM_CLONE",
                displayName = "Forge Custom Planar Clone",
                upstreamSourceLock = false,
                zones = listOf("Library", "Hand", "Battlefield", "Stack", "Graveyard", "Exile", "Command"),
                phases = listOf("UNTAP", "UPKEEP", "DRAW", "MAIN_1", "COMBAT", "MAIN_2", "END"),
                resourceLabels = listOf("20-Color Mana", "Life", "Energy"),
                scriptType = "FORGE_SVAR",
            ),
            EngineBaseVariantSpec(
                baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                displayName = "Hybrid Forge x EDOPro Mix",
                upstreamSourceLock = false,
                zones = listOf("Main Deck", "Sub-Deck", "Battlefield", "Monster/Spell Zone", "Stack", "Graveyard", "Exile"),
                phases = listOf("UNTAP", "DRAW", "STANDBY", "MAIN_1", "BATTLE", "MAIN_2", "END"),
                resourceLabels = listOf("LP / Life", "20-Color Pie", "Register-21"),
                scriptType = "HYBRID_LUA_SVAR",
            ),
        )
    }
}

enum class EngineHealthSeverity(val wireValue: String) {
    HEALTHY("HEALTHY"),
    DEGRADED("DEGRADED"),
    BREAKING_INCOMPATIBLE("BREAKING_INCOMPATIBLE"),
}

@JsonClass(generateAdapter = true)
data class EngineHealthStatusPayload(
    @Json(name = "alertId") val alertId: String,
    @Json(name = "sourceRepo") val sourceRepo: String = "Summer-Warrior/MPV-TCG-Card-Making-Software-Updated",
    @Json(name = "targetEngineRepo") val targetEngineRepo: String = "Summer-Warrior/Tcg-game-engine-test",
    @Json(name = "failingCommitSha") val failingCommitSha: String = "",
    @Json(name = "severity") val severity: String = EngineHealthSeverity.HEALTHY.wireValue,
    @Json(name = "brokenCapabilities") val brokenCapabilities: List<String> = emptyList(),
    @Json(name = "affectedCardFrames") val affectedCardFrames: List<String> = emptyList(),
    @Json(name = "masterHubAckStatus") val masterHubAckStatus: String = "NOT_REQUIRED",
    @Json(name = "timestamp") val timestamp: String = "",
    @Json(name = "signatureSha256") val signatureSha256: String = "",
)

data class EngineCommitDiff(
    val fromCommitSha: String,
    val toCommitSha: String,
    val addedVariantIds: List<String>,
    val removedVariantIds: List<String>,
    val addedBigIntFunctions: List<String>,
    val removedBigIntFunctions: List<String>,
    val addedTurnPhases: List<String>,
    val removedTurnPhases: List<String>,
    val addedEffectTypes: List<String>,
    val removedEffectTypes: List<String>,
    val boundsChanged: Boolean,
) {
    val hasDiff: Boolean
        get() = fromCommitSha != toCommitSha ||
            addedVariantIds.isNotEmpty() ||
            removedVariantIds.isNotEmpty() ||
            addedBigIntFunctions.isNotEmpty() ||
            removedBigIntFunctions.isNotEmpty() ||
            addedTurnPhases.isNotEmpty() ||
            removedTurnPhases.isNotEmpty() ||
            addedEffectTypes.isNotEmpty() ||
            removedEffectTypes.isNotEmpty() ||
            boundsChanged
}

data class CardFrameAuditIssue(
    val code: String,
    val message: String,
    val severity: EngineHealthSeverity,
)

data class CardFrameReviewAuditResult(
    val isCompatible: Boolean,
    val sanitizedDescriptor: String,
    val generatedLuaScript: String,
    val generatedForgeSvarScript: String,
    val generatedHybridScript: String,
    val issues: List<CardFrameAuditIssue>,
)

/**
 * Layer 20 Omnichroma {Omega} breaks free from Layers 1–19 Diamond Option B pips
 * into 6 bespoke sacred celestial geometries.
 */
enum class SacredCelestialShape {
    HEXAGRAM_STAR,
    CELESTIAL_OCTAGRAM,
    SACRED_HALO_RING,
    ASTRAL_MERKABA,
    PRIMORDIAL_SUNBURST,
    SOLAR_ASTROLABE,
}

data class OmnichromaGeometrySpec(
    val code: String,
    val geometryName: String,
    val shape: SacredCelestialShape,
)

object OmnichromaSacredCatalog {
    val sixSacredGeometries: List<OmnichromaGeometrySpec> = listOf(
        OmnichromaGeometrySpec("{Ω·M}", "Hexagram Star", SacredCelestialShape.HEXAGRAM_STAR),
        OmnichromaGeometrySpec("{Ω·C}", "Celestial Octagram", SacredCelestialShape.CELESTIAL_OCTAGRAM),
        OmnichromaGeometrySpec("{Ω·S}", "Sacred Halo Ring", SacredCelestialShape.SACRED_HALO_RING),
        OmnichromaGeometrySpec("{Ω·V}", "Astral Merkaba", SacredCelestialShape.ASTRAL_MERKABA),
        OmnichromaGeometrySpec("{Ω·E}", "Primordial Sunburst", SacredCelestialShape.PRIMORDIAL_SUNBURST),
        OmnichromaGeometrySpec("{Ω·A}", "Solar Astrolabe", SacredCelestialShape.SOLAR_ASTROLABE),
    )

    val validCodes: Set<String> = sixSacredGeometries.map { it.code }.toSet()
}
