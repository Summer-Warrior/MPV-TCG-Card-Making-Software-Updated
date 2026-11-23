package com.example.domain.nexus

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure Kotlin Two-Way Game Engine Compatibility Auditor, Source Schema Extractor,
 * Dual-Engine Consent Gate, Multi-Target Script Generator (Lua / Forge SVar / Hybrid / Safe Descriptor),
 * Card Frame Reviewer, and Master Hub Admin Gate Evaluator.
 *
 * Strictly zero Android UI / Compose imports.
 */
object EngineCompatibilityAuditor {

    private val bigIntFuncRegex = Regex("\\b(ADD_21|MUL_21|SUB_21|MOD_21|SCALE_ATK_LP_21)\\b")
    private val variantRegex = Regex("\\b(EDOPRO_CUSTOM_FORK|FORGE_CUSTOM_CLONE|HYBRID_FORGE_EDOPRO_MIX|[A-Z0-9_]+_CUSTOM_[A-Z0-9_]+)\\b")
    private val enumBlockRegex = Regex("enum\\s+class\\s+(TurnPhase|CardEffectType)\\s*\\{([^}]*)\\}", setOf(RegexOption.DOT_MATCHES_ALL))
    private val rulesMaxLengthRegex = Regex("\\.size\\(\\)\\s*<=\\s*(\\d+)")

    /**
     * DUAL-ENGINE CONSENT RULE:
     * No schema sync, code-template overwrite, or exported card-pack format migration
     * may be finalized unless BOTH engines record `CONSENTED` for the target contract
     * version and exact commit SHA.
     */
    fun canFinalizeEngineSync(
        contract: HandshakeContract,
        targetCommitSha: String,
    ): Boolean {
        val cleanSha = targetCommitSha.trim()
        if (cleanSha.isEmpty()) return false
        val cardOk = contract.cardMakerConsent.status == ConsentStatus.CONSENTED.wireValue &&
            contract.cardMakerConsent.commitSha == cleanSha
        val engineOk = contract.gameEngineConsent.status == ConsentStatus.CONSENTED.wireValue &&
            contract.gameEngineConsent.commitSha == cleanSha
        return cardOk && engineOk
    }

    /**
     * Extracts live engine variants, BigInt21 functions, TurnPhase/CardEffectType enums,
     * and Firestore field length bounds directly from fetched `Tcg-game-engine-test` source files.
     */
    fun parseSnapshotFromEngineFiles(
        engineRepo: String,
        engineBranch: String,
        commitSha: String,
        fetchedFiles: Map<String, String>,
        fallbackBaseline: EngineSyncSnapshot,
    ): EngineSyncSnapshot {
        val modelsText = fetchedFiles["app/src/main/java/com/example/data/Models.kt"].orEmpty()
        val engineText = fetchedFiles["app/src/main/java/com/example/data/GameEngine.kt"].orEmpty()
        val rulesText = fetchedFiles["firestore.rules"].orEmpty()
        val blueprintText = fetchedFiles["firebase-blueprint.json"].orEmpty()
        val combined = listOf(modelsText, engineText, rulesText, blueprintText).joinToString("\n")

        val foundBigIntFuncs = bigIntFuncRegex.findAll(combined)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
            .ifEmpty { fallbackBaseline.bigInt21Spec.supportedFunctions }

        val foundVariantIds = variantRegex.findAll(combined)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

        val baselineMap = fallbackBaseline.baseVariants.associateBy { it.baseVariantId }
        val mergedVariants = if (foundVariantIds.isEmpty()) {
            fallbackBaseline.baseVariants
        } else {
            foundVariantIds.map { id ->
                baselineMap[id] ?: EngineBaseVariantSpec(
                    baseVariantId = id,
                    displayName = id.replace('_', ' ').lowercase(Locale.US)
                        .replaceFirstChar { ch -> ch.uppercaseChar() },
                    upstreamSourceLock = false,
                    zones = listOf("Main Deck", "Field", "Graveyard", "Exile"),
                    phases = listOf("DRAW", "MAIN_1", "BATTLE", "END"),
                    resourceLabels = listOf("LP", "20-Color Pie"),
                    scriptType = if (id.contains("FORGE")) "FORGE_SVAR" else "LUA_21",
                )
            }
        }

        val parsedEnums = mutableMapOf<String, List<String>>()
        enumBlockRegex.findAll(combined).forEach { match ->
            val enumName = match.groupValues[1]
            val entries = match.groupValues[2]
                .split(",", "\n", ";")
                .map { it.trim().substringBefore("(").substringBefore("//").trim() }
                .filter { it.matches(Regex("^[A-Z][A-Z0-9_]+$")) }
            if (entries.isNotEmpty()) {
                parsedEnums[enumName] = entries
            }
        }

        val ruleLimits = rulesMaxLengthRegex.findAll(rulesText)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        val updatedBounds = fallbackBaseline.firestoreBounds.copy(
            maxCardNameLength = ruleLimits.firstOrNull { it in 40..256 }
                ?: fallbackBaseline.firestoreBounds.maxCardNameLength,
            maxRulesTextLength = ruleLimits.firstOrNull { it in 500..10000 }
                ?: fallbackBaseline.firestoreBounds.maxRulesTextLength,
        )

        return EngineSyncSnapshot(
            schemaVersion = fallbackBaseline.schemaVersion,
            engineRepo = engineRepo,
            engineBranch = engineBranch,
            commitSha = commitSha,
            capturedAt = currentUtcIso(),
            baseVariants = mergedVariants,
            bigInt21Spec = fallbackBaseline.bigInt21Spec.copy(
                supportedFunctions = foundBigIntFuncs,
            ),
            firestoreBounds = updatedBounds,
            schemaDefinitions = fallbackBaseline.schemaDefinitions.copy(
                turnPhases = parsedEnums["TurnPhase"] ?: fallbackBaseline.schemaDefinitions.turnPhases,
                cardEffectTypes = parsedEnums["CardEffectType"] ?: fallbackBaseline.schemaDefinitions.cardEffectTypes,
            ),
        )
    }

    fun computeEngineDiff(
        baseline: EngineSyncSnapshot,
        incoming: EngineSyncSnapshot,
    ): EngineCommitDiff {
        val oldVariants = baseline.baseVariants.map { it.baseVariantId }.toSet()
        val newVariants = incoming.baseVariants.map { it.baseVariantId }.toSet()
        val oldFuncs = baseline.bigInt21Spec.supportedFunctions.toSet()
        val newFuncs = incoming.bigInt21Spec.supportedFunctions.toSet()
        val oldPhases = baseline.schemaDefinitions.turnPhases.toSet()
        val newPhases = incoming.schemaDefinitions.turnPhases.toSet()
        val oldEffects = baseline.schemaDefinitions.cardEffectTypes.toSet()
        val newEffects = incoming.schemaDefinitions.cardEffectTypes.toSet()

        return EngineCommitDiff(
            fromCommitSha = baseline.commitSha,
            toCommitSha = incoming.commitSha,
            addedVariantIds = (newVariants - oldVariants).sorted(),
            removedVariantIds = (oldVariants - newVariants).sorted(),
            addedBigIntFunctions = (newFuncs - oldFuncs).sorted(),
            removedBigIntFunctions = (oldFuncs - newFuncs).sorted(),
            addedTurnPhases = (newPhases - oldPhases).sorted(),
            removedTurnPhases = (oldPhases - newPhases).sorted(),
            addedEffectTypes = (newEffects - oldEffects).sorted(),
            removedEffectTypes = (oldEffects - newEffects).sorted(),
            boundsChanged = baseline.firestoreBounds != incoming.firestoreBounds,
        )
    }

    /**
     * Runs the Benefit-Integrity Compatibility Audit against fetched `Tcg-game-engine-test` files
     * and remote handshake contract state, returning a signed `EngineHealthStatusPayload`.
     */
    fun runBenefitIntegrityAudit(
        contract: HandshakeContract,
        commitSha: String,
        fetchedFiles: Map<String, String>,
        missingFiles: List<String>,
        remoteEngineConsentStatus: String?,
        sourceRepo: String = contract.cardMakerRepo,
    ): EngineHealthStatusPayload {
        val brokenCapabilities = mutableListOf<String>()
        val affectedFrames = mutableListOf<String>()

        if (missingFiles.isNotEmpty()) {
            brokenCapabilities += "MISSING_REQUIRED_FILES:${missingFiles.joinToString(",")}"
            affectedFrames += "ALL_ENGINE_MAPPED_FRAMES"
        }

        val models = fetchedFiles["app/src/main/java/com/example/data/Models.kt"].orEmpty()
        val engine = fetchedFiles["app/src/main/java/com/example/data/GameEngine.kt"].orEmpty()
        val rules = fetchedFiles["firestore.rules"].orEmpty()
        val combined = "$models\n$engine\n$rules"

        if (models.isNotBlank() || engine.isNotBlank()) {
            val has21Digit = combined.contains("21", ignoreCase = true) || combined.contains("BigInt", ignoreCase = true)
            if (!has21Digit) {
                brokenCapabilities += "21_DIGIT_BIGINT_CODES"
                affectedFrames += "21-Digit Register Card Frames"
            }
            val hasCoreModels = combined.contains("CardTemplate") ||
                combined.contains("CustomDeck") ||
                combined.contains("TurnPhase")
            if (!hasCoreModels) {
                brokenCapabilities += "TURN_PHASE_AND_EFFECT_ENUMS"
                affectedFrames += "CardTemplate / TurnPhase Mapped Frames"
            }
            val hasNonLockedClones = variantRegex.containsMatchIn(combined) ||
                combined.contains("custom", ignoreCase = true)
            if (!hasNonLockedClones) {
                brokenCapabilities += "NON_LOCKED_CUSTOM_CLONES"
                affectedFrames += "Non-Locked Clone Export Frames"
            }
        }

        if (remoteEngineConsentStatus == ConsentStatus.REJECTED.wireValue ||
            contract.gameEngineConsent.status == ConsentStatus.REJECTED.wireValue
        ) {
            brokenCapabilities += "HANDSHAKE_CONTRACT_REVOKED"
            affectedFrames += "ALL_EXPORTED_PACK_FRAMES"
        }

        val severity = when {
            commitSha.isBlank() -> EngineHealthSeverity.DEGRADED
            missingFiles.size >= 3 ||
                "HANDSHAKE_CONTRACT_REVOKED" in brokenCapabilities ||
                "21_DIGIT_BIGINT_CODES" in brokenCapabilities ->
                EngineHealthSeverity.BREAKING_INCOMPATIBLE
            brokenCapabilities.isNotEmpty() -> EngineHealthSeverity.DEGRADED
            else -> EngineHealthSeverity.HEALTHY
        }

        val nowIso = currentUtcIso()
        val failingSha = if (severity == EngineHealthSeverity.HEALTHY) "" else commitSha.ifBlank { "unreachable-$nowIso" }
        val alertId = "alert-${failingSha.take(12).ifBlank { "healthy" }}-${severity.wireValue.lowercase(Locale.US)}"
        val ackStatus = if (severity == EngineHealthSeverity.HEALTHY) "NOT_REQUIRED" else "PENDING_ACK"

        val canonicalForSig = listOf(
            alertId,
            sourceRepo,
            contract.gameEngineRepo,
            failingSha,
            severity.wireValue,
            brokenCapabilities.joinToString("|"),
            affectedFrames.distinct().sorted().joinToString("|"),
            ackStatus,
            nowIso,
        ).joinToString("::")

        return EngineHealthStatusPayload(
            alertId = alertId,
            sourceRepo = sourceRepo,
            targetEngineRepo = contract.gameEngineRepo,
            failingCommitSha = failingSha,
            severity = severity.wireValue,
            brokenCapabilities = brokenCapabilities,
            affectedCardFrames = affectedFrames.distinct().sorted(),
            masterHubAckStatus = ackStatus,
            timestamp = nowIso,
            signatureSha256 = sha256Hex(canonicalForSig),
        )
    }

    /**
     * Automatically sanitizes reserved board descriptor delimiters such as `|`.
     */
    fun sanitizeReservedDelimiters(raw: String, reserved: List<String> = listOf("|")): String {
        var result = raw
        reserved.forEach { delim ->
            if (delim.isNotEmpty()) {
                result = result.replace(delim, "/")
            }
        }
        return result.replace("\n", " ").replace("\r", " ").trim()
    }

    /**
     * Card Frame Reviewer: audits a card frame & its fields against BOTH the live
     * `EngineSyncSnapshot` AND the WIP TCG Onboarding Spec (20-Color Pie + Layer 20 Omnichroma,
     * reserved delimiter safety, Firestore bounds, 21-digit register/stats, and Time-Travel rules).
     */
    fun reviewCardFrame(
        cardName: String,
        cardType: String,
        rulesText: String,
        codeString: String,
        statText: String,
        selectedPips: List<String>,
        baseVariantId: String,
        effectType: String,
        editorMode: CardEditorMode,
        flavorFilters: Set<Int>,
        frameWidthPx: Int,
        frameHeightPx: Int,
        snapshot: EngineSyncSnapshot,
    ): CardFrameReviewAuditResult {
        val issues = mutableListOf<CardFrameAuditIssue>()
        val bounds = snapshot.firestoreBounds
        val variant = snapshot.baseVariants.firstOrNull { it.baseVariantId == baseVariantId }

        if (variant == null) {
            issues += CardFrameAuditIssue(
                code = "VARIANT_UNSUPPORTED_BY_ENGINE",
                message = "Base variant '$baseVariantId' is not supported by Tcg-game-engine-test@${snapshot.commitSha.take(8)}.",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        } else if (variant.upstreamSourceLock) {
            issues += CardFrameAuditIssue(
                code = "VARIANT_LOCKED",
                message = "Locked upstream variant '${variant.baseVariantId}' cannot be exported.",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        }

        // Frame geometry aspect ratio check
        if (frameWidthPx < 250 || frameHeightPx < 350) {
            issues += CardFrameAuditIssue(
                code = "FRAME_GEOMETRY_TOO_SMALL",
                message = "Frame dimensions ${frameWidthPx}x${frameHeightPx}px are below minimum printable card bounds (250x350).",
                severity = EngineHealthSeverity.DEGRADED,
            )
        }

        // Firestore length & overflow bounds
        if (cardName.isBlank()) {
            issues += CardFrameAuditIssue(
                code = "EMPTY_CARD_NAME",
                message = "Card name is required.",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        } else if (cardName.length > bounds.maxCardNameLength) {
            issues += CardFrameAuditIssue(
                code = "NAME_EXCEEDS_FIRESTORE_BOUND",
                message = "Card name length (${cardName.length}) exceeds firestore.rules limit (${bounds.maxCardNameLength}).",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        }

        if (cardType.length > bounds.maxCardTypeLength) {
            issues += CardFrameAuditIssue(
                code = "TYPE_EXCEEDS_FIRESTORE_BOUND",
                message = "Card type length (${cardType.length}) exceeds firestore.rules limit (${bounds.maxCardTypeLength}).",
                severity = EngineHealthSeverity.DEGRADED,
            )
        }

        if (rulesText.length > bounds.maxRulesTextLength) {
            issues += CardFrameAuditIssue(
                code = "RULES_TEXT_OVERFLOW",
                message = "Rules text (${rulesText.length} chars) exceeds engine Firestore bound (${bounds.maxRulesTextLength}).",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        }

        // Delimiter safety check (e.g., '|')
        val foundDelim = bounds.reservedDelimiters.firstOrNull { delim ->
            cardName.contains(delim) || cardType.contains(delim) || rulesText.contains(delim) || statText.contains(delim)
        }
        if (foundDelim != null) {
            issues += CardFrameAuditIssue(
                code = "RESERVED_DELIMITER_DETECTED",
                message = "Reserved delimiter '$foundDelim' detected in card fields; board descriptor auto-sanitizes it to '/'.",
                severity = EngineHealthSeverity.DEGRADED,
            )
        }

        // 21-digit code string & stat fitting
        val cleanCode = codeString.trim()
        val codeRegex = runCatching { Regex(snapshot.bigInt21Spec.codeRegex) }.getOrDefault(CardCodeValidator.NUMERIC_21_DIGIT_REGEX)
        if (cleanCode.isEmpty() || !codeRegex.matches(cleanCode)) {
            issues += CardFrameAuditIssue(
                code = "CODE_STRING_21_INVALID",
                message = "Code string '$cleanCode' does not satisfy BigInt21Engine regex ${snapshot.bigInt21Spec.codeRegex}.",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        }
        val overflowRegex = runCatching { Regex(snapshot.bigInt21Spec.forbiddenOverflowRegex) }
            .getOrDefault(CardCodeValidator.FORBIDDEN_22_DIGIT_SEQUENCE_REGEX)
        if (overflowRegex.matches(statText) || overflowRegex.matches(rulesText)) {
            issues += CardFrameAuditIssue(
                code = "STAT_OR_TEXT_22_DIGIT_OVERFLOW",
                message = "22+ consecutive digits detected in stats/rules text, exceeding BigInt21Engine safety limit.",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        }

        // Active ruleset / CardEffectType compatibility
        if (effectType.isNotBlank() &&
            snapshot.schemaDefinitions.cardEffectTypes.none { it.equals(effectType, ignoreCase = true) }
        ) {
            issues += CardFrameAuditIssue(
                code = "EFFECT_TYPE_UNSUPPORTED",
                message = "CardEffectType '$effectType' is not declared in latest Tcg-game-engine-test snapshot.",
                severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
            )
        }

        // Time-Travel Cost Box Flavor Filter compliance
        if (editorMode == CardEditorMode.MATCH_GAMEPLAY) {
            val forbidden = flavorFilters.filter { it in CardCodeValidator.TIME_TRAVEL_FORBIDDEN_FILTERS }.sorted()
            if (forbidden.isNotEmpty()) {
                issues += CardFrameAuditIssue(
                    code = "COST_BOX_TIME_TRAVEL_BLOCKED",
                    message = "Cost Box Flavor Filters ${forbidden.joinToString(", ")} are blocked on Match Gameplay cards.",
                    severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
                )
            }
        }

        // 20-Color Pie + Layer 20 Omnichroma 6-geometry pip check
        selectedPips.forEach { pip ->
            val validPip = CardCodeValidator.isValidColorPiePip(pip) || pip in OmnichromaSacredCatalog.validCodes
            if (!validPip) {
                issues += CardFrameAuditIssue(
                    code = "INVALID_PIP_GRAMMAR",
                    message = "Pip '$pip' does not conform to canonical 20-Color Pie Diamond Option B or Layer 20 Omnichroma grammar.",
                    severity = EngineHealthSeverity.BREAKING_INCOMPATIBLE,
                )
            }
        }

        val safeName = sanitizeReservedDelimiters(cardName, bounds.reservedDelimiters)
        val safeType = sanitizeReservedDelimiters(cardType, bounds.reservedDelimiters)
        val safeStats = sanitizeReservedDelimiters(statText, bounds.reservedDelimiters)
        val safeCode = cleanCode.ifBlank { "0" }
        val padded21 = safeCode.padStart(21, '0')
        val pipJoined = selectedPips.joinToString("")

        val sanitizedDescriptor = listOf(
            baseVariantId,
            safeCode,
            safeName,
            safeType,
            pipJoined,
            safeStats,
            effectType.ifBlank { "ACTIVATED" },
        ).joinToString("|")

        val luaScript = buildString {
            append("-- Auto-generated for $baseVariantId (Tcg-game-engine-test@${snapshot.commitSha.take(8)})\n")
            append("-- File: c$padded21.lua\n")
            append("local c$safeCode = {}\n")
            append("c$safeCode.code = \"$safeCode\"\n")
            append("c$safeCode.name = \"${safeName.replace("\"", "\\\"")}\"\n")
            append("c$safeCode.pips = \"$pipJoined\"\n")
            append("c$safeCode.effect_type = \"${effectType.ifBlank { "ACTIVATED" }}\"\n")
            append("function c$safeCode.initial_effect(card)\n")
            append("    return card ~= nil\n")
            append("end\n")
            append("return c$safeCode\n")
        }

        val forgeSvarScript = buildString {
            append("Name:$safeName\n")
            append("Types:$safeType\n")
            append("ManaCost:${pipJoined.ifBlank { "no cost" }}\n")
            append("SVar:CodeString21:$safeCode\n")
            append("SVar:BaseVariant:$baseVariantId\n")
            append("SVar:EffectType:${effectType.ifBlank { "ACTIVATED" }}\n")
            if (safeStats.isNotBlank()) {
                append("PT:$safeStats\n")
            }
            append("Oracle:${sanitizeReservedDelimiters(rulesText, bounds.reservedDelimiters)}\n")
        }

        val hybridScript = buildString {
            append("# Hybrid Forge x EDOPro Script ($baseVariantId)\n")
            append("# Descriptor: $sanitizedDescriptor\n\n")
            append("[FORGE_SVAR]\n")
            append(forgeSvarScript)
            append("\n[LUA_21]\n")
            append(luaScript)
        }

        return CardFrameReviewAuditResult(
            isCompatible = issues.none { it.severity == EngineHealthSeverity.BREAKING_INCOMPATIBLE },
            sanitizedDescriptor = sanitizedDescriptor,
            generatedLuaScript = luaScript,
            generatedForgeSvarScript = forgeSvarScript,
            generatedHybridScript = hybridScript,
            issues = issues,
        )
    }

    /**
     * Checks whether the signed-in identity satisfies Master Hub Administrative Role/User/Email gating.
     */
    fun isMasterHubAdmin(
        gate: MasterHubAdminGate,
        userRoleIds: Set<String>,
        userId: String,
        userEmail: String,
    ): Boolean {
        val cleanUser = userId.trim()
        val cleanEmail = userEmail.trim().lowercase(Locale.US)
        val cleanRoles = userRoleIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        val hasRoleMatch = gate.adminRoleIds.any { it.isNotBlank() && it.trim() in cleanRoles }
        val hasUserMatch = cleanUser.isNotEmpty() && gate.adminUserIds.any { it.equals(cleanUser, ignoreCase = true) }
        val hasEmailMatch = cleanEmail.isNotEmpty() && gate.adminEmails.any { it.trim().lowercase(Locale.US) == cleanEmail }
        return hasRoleMatch || hasUserMatch || hasEmailMatch
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun currentUtcIso(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date())
    }
}
