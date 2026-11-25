package com.example.domain.telemetry

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Canonical Telemetry Report Schema (`schemaVersion: "1.0.0"`)
 * shared with `Summer-Warrior/Tcg-game-engine-test` (`com.aistudio.nexustcg.vxqrmk`).
 *
 * Pure Kotlin domain models — zero Android UI / Compose dependencies.
 */

enum class TelemetryInteractionType(val wireValue: String, val displayLabel: String) {
    CHAIN_RESPONSE("CHAIN_RESPONSE", "Chain Response"),
    COMBO_SYNERGY("COMBO_SYNERGY", "Combo Synergy"),
    COUNTER_NEGATED("COUNTER_NEGATED", "Counter / Negated"),
    COMBAT_DEFEATED("COMBAT_DEFEATED", "Combat Defeated"),
    COMBAT_DESTROYED_BY("COMBAT_DESTROYED_BY", "Destroyed By"),
    STACK_CONFLICT("STACK_CONFLICT", "Stack Conflict"),
    RULE_VALIDATION_ERROR("RULE_VALIDATION_ERROR", "Rule Validation Error"),
    SCRIPT_EXCEPTION("SCRIPT_EXCEPTION", "Script Exception"),
    ;

    companion object {
        fun fromWire(raw: String?): TelemetryInteractionType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
                ?: COMBO_SYNERGY
    }
}

@JsonClass(generateAdapter = true)
data class CardUsageMetric(
    @Json(name = "cardId") val cardId: String,
    @Json(name = "register21Digit") val register21Digit: String? = null,
    @Json(name = "cardName") val cardName: String,
    @Json(name = "targetClientId") val targetClientId: String = "",
    @Json(name = "splitOffSetId") val splitOffSetId: String? = null,
    @Json(name = "parentOfficialSetId") val parentOfficialSetId: String? = null,
    @Json(name = "cardOrigin") val cardOrigin: String = "ORIGINAL_OC_CARD",
    @Json(name = "linkedCanonCardId") val linkedCanonCardId: String? = null,
    @Json(name = "matchesIncluded") val matchesIncluded: Int = 0,
    @Json(name = "timesDrawn") val timesDrawn: Int = 0,
    @Json(name = "timesPlayed") val timesPlayed: Int = 0,
    @Json(name = "timesActivated") val timesActivated: Int = 0,
    @Json(name = "timesAttacked") val timesAttacked: Int = 0,
    @Json(name = "timesDestroyed") val timesDestroyed: Int = 0,
    @Json(name = "totalDamageDealt") val totalDamageDealt: Long = 0L,
    @Json(name = "winsWhenPlayed") val winsWhenPlayed: Int = 0,
    @Json(name = "lossesWhenPlayed") val lossesWhenPlayed: Int = 0,
    @Json(name = "averageTurnPlayed") val averageTurnPlayed: Double = 0.0,
    @Json(name = "ruleOrScriptErrorCount") val ruleOrScriptErrorCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class CardInteractionMetric(
    @Json(name = "sourceCardId") val sourceCardId: String,
    @Json(name = "sourceCardName") val sourceCardName: String,
    @Json(name = "sourceCardOrigin") val sourceCardOrigin: String = "ORIGINAL_OC_CARD",
    @Json(name = "targetCardId") val targetCardId: String,
    @Json(name = "targetCardName") val targetCardName: String,
    @Json(name = "targetCardOrigin") val targetCardOrigin: String = "ORIGINAL_OC_CARD",
    @Json(name = "interactionType") val interactionType: String = TelemetryInteractionType.COMBO_SYNERGY.wireValue,
    @Json(name = "occurrenceCount") val occurrenceCount: Int = 1,
    @Json(name = "phase") val phase: String = "",
    @Json(name = "baseVariantId") val baseVariantId: String = "",
    @Json(name = "lastOccurredAt") val lastOccurredAt: String = "",
    @Json(name = "sampleLogLine") val sampleLogLine: String = "",
)

@JsonClass(generateAdapter = true)
data class CustomCardUsageReport(
    @Json(name = "reportId") val reportId: String,
    @Json(name = "schemaVersion") val schemaVersion: String = "1.0.0",
    @Json(name = "sourceAppRepo") val sourceAppRepo: String = "Summer-Warrior/Tcg-game-engine-test",
    @Json(name = "engineCommitSha") val engineCommitSha: String = "",
    @Json(name = "generatedAt") val generatedAt: String = "",
    @Json(name = "baseVariantId") val baseVariantId: String = "",
    @Json(name = "splitOffSetId") val splitOffSetId: String? = null,
    @Json(name = "parentOfficialSetId") val parentOfficialSetId: String? = null,
    @Json(name = "totalMatchesTracked") val totalMatchesTracked: Int = 0,
    @Json(name = "cardStats") val cardStats: List<CardUsageMetric> = emptyList(),
    @Json(name = "interactions") val interactions: List<CardInteractionMetric> = emptyList(),
)
