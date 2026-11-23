package com.example.domain.nexus

import java.math.BigInteger

/**
 * Pure Kotlin Card Code Validator, Locked-Client Firewall, 21-Digit BigInteger Engine,
 * 20-Color Pie Pip Validator, Notebook Time-Travel Guardrail Enforcer, and Lua/JSON Code Generator.
 *
 * Strictly zero Android UI / Compose / DOM dependencies.
 */

enum class CardIdKind {
    NUMERIC_8_DIGIT,
    NUMERIC_21_DIGIT,
    UUID,
    CUSTOM_STRING,
}

data class CardValidationIssue(
    val code: String,
    val message: String,
    val isBlocking: Boolean = true,
)

data class CardValidationReport(
    val isValid: Boolean,
    val cardIdKind: CardIdKind,
    val normalized21DigitRegister: String?,
    val issues: List<CardValidationIssue>,
) {
    val blockingErrors: List<CardValidationIssue>
        get() = issues.filter { it.isBlocking }
}

object CardCodeValidator {

    val NUMERIC_21_DIGIT_REGEX = Regex("^[0-9]{1,21}$")
    val NUMERIC_8_DIGIT_REGEX = Regex("^[0-9]{8}$")
    val UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )
    val FORBIDDEN_22_DIGIT_SEQUENCE_REGEX = Regex(".*[0-9]{22}.*")

    val MAX_21_DIGIT_BIG_INT: BigInteger = BigInteger("999999999999999999999")
    val TIME_TRAVEL_FORBIDDEN_FILTERS: Set<Int> = setOf(19, 20, 21)

    val ALLOWED_MATCH_TEMPORAL_EFFECTS: Set<String> = setOf(
        "End Turn",
        "Extra Turn",
    )

    private val TIME_TRAVEL_FLAVOR_KEYWORDS = listOf(
        "time travel",
        "time-travel",
        "rewind timeline",
        "alternate timeline",
        "temporal paradox",
        "filter 19",
        "filter 20",
        "filter 21",
    )

    // Validates canonical 20-Color Pie pips:
    // {C}, {W}..{Aq}, Layer 2 fused {W·U}, Value states :T/:S/:M, Choice splits {W/U},
    // Weighted leads {2W/U}, and Layer 20 {Ω·M}..{Ω·E}
    private val COLOR_SYMBOL_PATTERN =
        TwentyColorPieCatalog.baseSymbols
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

    private val VALUE_STATE_PATTERN = "(?::[TSM])?"
    private val ATOM_PATTERN = "(?:$COLOR_SYMBOL_PATTERN)$VALUE_STATE_PATTERN"
    private val VALID_PIP_REGEX = Regex(
        "^(?:" +
            "\\{C\\}" +
            "|\\{$ATOM_PATTERN\\}" +
            "|\\{$ATOM_PATTERN·$ATOM_PATTERN\\}" +
            "|\\{$ATOM_PATTERN/$ATOM_PATTERN\\}" +
            "|\\{[2-9]$ATOM_PATTERN/$ATOM_PATTERN\\}" +
            "|\\{Ω·[MCSVE]\\}" +
            ")$"
    )

    /**
     * Strict Locked-Client Firewall:
     * Only clients with `upstreamSourceLock == false` may be targeted for card coding,
     * 21-digit register binding, or pack export.
     */
    fun isClientWritable(client: NexusClientDefinition): Boolean = !client.upstreamSourceLock

    /**
     * Resolves the recommended Non-Locked 21-Digit Fork when the user clicks
     * "Target Non-Locked 21-Digit Fork Instead" on a locked upstream reference client.
     */
    fun resolveNonLockedFork(
        lockedClient: NexusClientDefinition,
        allClients: List<NexusClientDefinition>,
    ): NexusClientDefinition? {
        val explicitForkId = lockedClient.nonLockedForkId
        if (!explicitForkId.isNullOrBlank()) {
            allClients.firstOrNull { it.id == explicitForkId && !it.upstreamSourceLock }?.let { return it }
        }
        return allClients.firstOrNull {
            !it.upstreamSourceLock &&
                it.category == ClientCategory.CUSTOM_21_DIGIT_CLONE.wireValue
        } ?: allClients.firstOrNull { !it.upstreamSourceLock }
    }

    fun classifyCardId(rawId: String): CardIdKind {
        val trimmed = rawId.trim()
        return when {
            NUMERIC_8_DIGIT_REGEX.matches(trimmed) -> CardIdKind.NUMERIC_8_DIGIT
            NUMERIC_21_DIGIT_REGEX.matches(trimmed) -> CardIdKind.NUMERIC_21_DIGIT
            UUID_REGEX.matches(trimmed) -> CardIdKind.UUID
            else -> CardIdKind.CUSTOM_STRING
        }
    }

    /**
     * Native overflow-free BigInteger 21-digit register addition/offset.
     * Returns null if input is non-numeric or result exceeds 21 digits (999999999999999999999).
     */
    fun computeBigIntRegisterOffset(baseRegister: String, offset: BigInteger): String? {
        val trimmed = baseRegister.trim()
        if (!NUMERIC_21_DIGIT_REGEX.matches(trimmed)) return null
        val sum = BigInteger(trimmed).add(offset)
        if (sum < BigInteger.ZERO || sum > MAX_21_DIGIT_BIG_INT) return null
        return sum.toString()
    }

    fun padTo21Digits(numericId: String): String? {
        val trimmed = numericId.trim()
        if (!NUMERIC_21_DIGIT_REGEX.matches(trimmed)) return null
        return trimmed.padStart(21, '0')
    }

    fun isValid21DigitCodingVariable(value: String): Boolean {
        return !FORBIDDEN_22_DIGIT_SEQUENCE_REGEX.matches(value)
    }

    fun isValidColorPiePip(pip: String): Boolean {
        return VALID_PIP_REGEX.matches(pip.trim())
    }

    fun validateCard(
        card: NexusCodedCard,
        targetClient: NexusClientDefinition?,
    ): CardValidationReport {
        val issues = mutableListOf<CardValidationIssue>()
        val trimmedId = card.cardId.trim()
        val idKind = classifyCardId(trimmedId)

        // 1. Strict Locked-Client Firewall
        if (targetClient == null) {
            issues += CardValidationIssue(
                code = "CLIENT_NOT_FOUND",
                message = "Target client '${card.targetClientId}' does not exist in the active Nexus Hub index.",
            )
        } else if (targetClient.upstreamSourceLock) {
            issues += CardValidationIssue(
                code = "LOCKED_UPSTREAM_CLIENT",
                message = "Locked upstream client '${targetClient.name}' is read-only. Target a non-locked 21-digit fork instead.",
            )
        }

        // 2. Card ID & 21-Digit Register validation (BigInteger-backed, zero overflow)
        if (trimmedId.isEmpty()) {
            issues += CardValidationIssue(
                code = "EMPTY_CARD_ID",
                message = "Card ID is required (8-digit, 21-digit numeric, UUID, or arbitrary string ID).",
            )
        } else if (trimmedId.all { it.isDigit() } && !NUMERIC_21_DIGIT_REGEX.matches(trimmedId)) {
            issues += CardValidationIssue(
                code = "CARD_ID_EXCEEDS_21_DIGITS",
                message = "Numeric Card ID exceeds 21 digits (max 999999999999999999999).",
            )
        }

        val normalizedRegister = when {
            !card.register21Digit.isNullOrBlank() -> {
                val reg = card.register21Digit.trim()
                if (!NUMERIC_21_DIGIT_REGEX.matches(reg)) {
                    issues += CardValidationIssue(
                        code = "INVALID_21_DIGIT_REGISTER",
                        message = "21-digit register must match ^[0-9]{1,21}$ (received '$reg').",
                    )
                    null
                } else {
                    BigInteger(reg).toString()
                }
            }
            idKind == CardIdKind.NUMERIC_8_DIGIT || idKind == CardIdKind.NUMERIC_21_DIGIT -> {
                BigInteger(trimmedId).toString()
            }
            else -> null
        }

        // 3. 21-Digit Coding Variable Validation (!/.*[0-9]{22}.*/)
        card.codingVariables.forEach { (key, value) ->
            if (!isValid21DigitCodingVariable(key) || !isValid21DigitCodingVariable(value)) {
                issues += CardValidationIssue(
                    code = "CODING_VAR_22_DIGITS",
                    message = "Coding variable '$key' contains 22+ consecutive digits, violating the 21-digit register boundary.",
                )
            }
        }

        // 4. Card Name check
        if (card.cardName.isBlank()) {
            issues += CardValidationIssue(
                code = "EMPTY_CARD_NAME",
                message = "Card name is required before export.",
            )
        }

        // 5. Strict Time-Travel Client Guardrail (Filters 19-21)
        val mode = CardEditorMode.entries.firstOrNull { it.wireValue == card.editorMode }
            ?: CardEditorMode.MATCH_GAMEPLAY

        if (mode == CardEditorMode.MATCH_GAMEPLAY) {
            if (card.allowTimeTravelFlagsOnMatchCards) {
                issues += CardValidationIssue(
                    code = "TIME_TRAVEL_FLAG_FORBIDDEN",
                    message = "allowTimeTravelFlagsOnMatchCards must strictly remain false on standard match gameplay cards.",
                )
            }
            val forbiddenFilters = card.flavorFilters.filter { it in TIME_TRAVEL_FORBIDDEN_FILTERS }.sorted()
            if (forbiddenFilters.isNotEmpty()) {
                issues += CardValidationIssue(
                    code = "MATCH_CARD_FORBIDDEN_FILTERS",
                    message = "Filters ${forbiddenFilters.joinToString(", ")} (Time-Travel flavor flags) are blocked on Match Gameplay cards. Switch to 'Avatar Profile / Memorial Forge Dossier' mode to use them.",
                )
            }
            val invalidTemporal = card.temporalEffects.filter { effect ->
                ALLOWED_MATCH_TEMPORAL_EFFECTS.none { it.equals(effect.trim(), ignoreCase = true) }
            }
            if (invalidTemporal.isNotEmpty()) {
                issues += CardValidationIssue(
                    code = "INVALID_MATCH_TEMPORAL_EFFECT",
                    message = "Match gameplay cards may ONLY declare 'End Turn' or 'Extra Turn' temporal effects (blocked: ${invalidTemporal.joinToString(", ")}).",
                )
            }
            val combinedText = "${card.cardName} ${card.rulesText}".lowercase()
            val matchedKeyword = TIME_TRAVEL_FLAVOR_KEYWORDS.firstOrNull { combinedText.contains(it) }
            if (matchedKeyword != null) {
                issues += CardValidationIssue(
                    code = "TIME_TRAVEL_TEXT_ON_MATCH_CARD",
                    message = "Time-travel flavor reference ('$matchedKeyword') is blocked on Match Gameplay cards. Only 'End Turn' and 'Extra Turn' temporal effects are permitted.",
                )
            }
        }

        // 6. Canonical 20-Color Pie Pip Grammar check
        card.colorPiePips.forEach { pip ->
            if (!isValidColorPiePip(pip)) {
                issues += CardValidationIssue(
                    code = "INVALID_COLOR_PIE_PIP",
                    message = "Invalid 20-Color Pie pip token '$pip'.",
                )
            }
        }

        return CardValidationReport(
            isValid = issues.none { it.isBlocking },
            cardIdKind = idKind,
            normalized21DigitRegister = normalizedRegister,
            issues = issues,
        )
    }

    /**
     * Generates a clean, non-proprietary `c<21-digits>.lua` script stub bound to the non-locked client.
     */
    fun generateLuaScript(
        card: NexusCodedCard,
        targetClient: NexusClientDefinition,
    ): String {
        require(!targetClient.upstreamSourceLock) {
            "Locked-Client Firewall: cannot generate Lua script for locked upstream client '${targetClient.id}'."
        }
        val safeRegister = card.register21Digit
            ?.trim()
            ?.takeIf { NUMERIC_21_DIGIT_REGEX.matches(it) }
            ?: card.cardId.trim().takeIf { NUMERIC_21_DIGIT_REGEX.matches(it) }
            ?: "0"
        val padded = padTo21Digits(safeRegister) ?: "000000000000000000000"
        val escapedName = card.cardName.replace("\"", "\\\"").replace("\n", " ")
        val escapedType = card.cardType.replace("\"", "\\\"").replace("\n", " ")
        val escapedPips = card.colorPiePips.joinToString("")
        val escapedTemporal = card.temporalEffects.joinToString(", ")

        return buildString {
            append("-- Nexus Master Hub Non-Locked Client Script\n")
            append("-- File: c$padded.lua\n")
            append("-- Client: ${targetClient.id} (${targetClient.name}) [upstreamSourceLock=false]\n")
            append("-- Card ID: ${card.cardId} | 21-Digit Register: $safeRegister\n")
            append("-- Editor Mode: ${card.editorMode}\n")
            append("local CARD_ID = \"${card.cardId.replace("\"", "\\\"")}\"\n")
            append("local REGISTER_21 = \"$safeRegister\"\n")
            append("local c = {}\n\n")
            append("c.metadata = {\n")
            append("    id = CARD_ID,\n")
            append("    register21 = REGISTER_21,\n")
            append("    client = \"${targetClient.id}\",\n")
            append("    name = \"$escapedName\",\n")
            append("    card_type = \"$escapedType\",\n")
            append("    color_pie_pips = \"$escapedPips\",\n")
            append("    editor_mode = \"${card.editorMode}\",\n")
            append("    allow_time_travel_match_flags = false,\n")
            if (escapedTemporal.isNotEmpty()) {
                append("    temporal_effects = \"$escapedTemporal\",\n")
            }
            append("}\n\n")
            if (card.codingVariables.isNotEmpty()) {
                append("c.registers = {\n")
                card.codingVariables.toSortedMap().forEach { (k, v) ->
                    val safeK = k.replace("\"", "\\\"")
                    val safeV = v.replace("\"", "\\\"")
                    append("    [\"$safeK\"] = \"$safeV\",\n")
                }
                append("}\n\n")
            }
            append("function c.initial_effect(card_ref)\n")
            append("    if not card_ref then return false end\n")
            append("    return true\n")
            append("end\n\n")
            append("return c\n")
        }
    }
}
