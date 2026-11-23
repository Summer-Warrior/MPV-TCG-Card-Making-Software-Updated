package com.example

import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.ConsentStatus
import com.example.domain.nexus.DualConsentRecord
import com.example.domain.nexus.EngineCompatibilityAuditor
import com.example.domain.nexus.EngineHealthSeverity
import com.example.domain.nexus.EngineHealthStatusPayload
import com.example.domain.nexus.EngineSyncSnapshot
import com.example.domain.nexus.HandshakeContract
import com.example.domain.nexus.MasterHubAdminGate
import com.example.domain.nexus.MutualLinkStatus
import com.example.domain.nexus.NexusAppManifest
import com.example.domain.nexus.OmnichromaSacredCatalog
import com.example.ui.nexus.EngineSyncUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineHandshakeAndReviewerUnitTest {

    @Test
    fun appManifestAndContractTargetLiveProjectMpvRpMasterHub() {
        val appManifest = NexusAppManifest()
        val contract = HandshakeContract()
        assertEquals("Summer-Warrior/Project-MPV-RP-Master-Hub", appManifest.linkedMasterHub)
        assertEquals("Summer-Warrior/Project-MPV-RP-Master-Hub", contract.masterHubRepo)
        assertEquals("com.aistudio.tcgstudio.kqrmvx", appManifest.androidPackageName)
        assertEquals("tcgstudio://nexus-hub", appManifest.deepLinkScheme)
        assertEquals("com.aistudio.nexustcg.vxqrmk", appManifest.gameEngineAndroidPackage)
        assertEquals("nexus-tcg-engine://open", appManifest.gameEngineDeepLinkScheme)
        assertEquals("GITHUB_RELEASES_PUBLIC_PLAY_STORE_PERSONAL_ONLY", appManifest.distributionPolicy)
    }

    @Test
    fun dualEngineConsentRuleRequiresBothEnginesConsentedForExactCommitSha() {
        val sha = "abc123def4567890"
        val pendingContract = HandshakeContract(
            cardMakerConsent = DualConsentRecord(status = ConsentStatus.CONSENTED.wireValue, commitSha = sha),
            gameEngineConsent = DualConsentRecord(status = ConsentStatus.PENDING.wireValue, commitSha = sha),
        )
        assertFalse(
            "Single-sided consent MUST NOT finalize sync",
            EngineCompatibilityAuditor.canFinalizeEngineSync(pendingContract, sha),
        )

        val mismatchedShaContract = pendingContract.copy(
            gameEngineConsent = DualConsentRecord(status = ConsentStatus.CONSENTED.wireValue, commitSha = "different999"),
        )
        assertFalse(
            "Mismatched commit SHAs MUST NOT finalize sync",
            EngineCompatibilityAuditor.canFinalizeEngineSync(mismatchedShaContract, sha),
        )

        val bothConsented = pendingContract.copy(
            gameEngineConsent = DualConsentRecord(status = ConsentStatus.CONSENTED.wireValue, commitSha = sha),
        )
        assertTrue(
            "Both engines CONSENTED for exact commit SHA must unlock finalization",
            EngineCompatibilityAuditor.canFinalizeEngineSync(bothConsented, sha),
        )
    }

    @Test
    fun parameterizedKotlinEnumsAndTargetedFirestoreRulesBoundsParseAccurately() {
        val sampleModels = """
            enum class TurnPhase(val label: String) {
                DRAW("Draw Phase"),
                STANDBY("Standby Phase"),
                MAIN("Main Phase"),
                COMBAT("Combat Phase"),
                END("End Phase");

                fun next(): TurnPhase {
                    return when (this) {
                        DRAW -> STANDBY
                        else -> END
                    }
                }
            }

            enum class CardEffectType(val code: Int, val summary: String) {
                DIRECT_DAMAGE_BURN(1, "Burn"),
                LIFE_RESTORE_HEAL(2, "Heal"),
                ATK_DEF_BUFF(3, "Buff"),
                DRAW_CARDS(4, "Draw"),
                RESOURCE_RAMP(5, "Ramp"),
                TARGET_DESTROY_REMOVAL(6, "Destroy"),
                NEGATE_AND_COUNTER(7, "Negate");

                companion object {
                    fun fromCode(c: Int): CardEffectType {
                        return entries.firstOrNull { it.code == c } ?: DIRECT_DAMAGE_BURN
                    }
                }
            }

            val variants = listOf("ULTIMATE_TCG_WIP", "EDOPRO_CUSTOM_FORK", "FORGE_CUSTOM_CLONE", "HYBRID_FORGE_EDOPRO_MIX")
        """.trimIndent()
        val sampleEngine = """
            object BigInt21Engine {
                val ops = listOf("ADD_21", "MUL_21", "SUB_21", "MOD_21", "SCALE_ATK_LP_21")
            }
        """.trimIndent()
        val sampleRules = """
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                function isValidId(id) {
                  return id is string && id.size() <= 64;
                }
                function validCustomCard(data) {
                  return data.customCardName is string && data.customCardName.size() <= 100
                    && data.customCardAbility is string && data.customCardAbility.size() <= 1000
                    && data.cardIds.size() <= 100
                    && data.hostBoardCards.size() <= 60;
                }
              }
            }
        """.trimIndent()

        val snapshot = EngineCompatibilityAuditor.parseSnapshotFromEngineFiles(
            engineRepo = "Summer-Warrior/Tcg-game-engine-test",
            engineBranch = "main",
            commitSha = "998877665544",
            fetchedFiles = mapOf(
                "app/src/main/java/com/example/data/Models.kt" to sampleModels,
                "app/src/main/java/com/example/data/GameEngine.kt" to sampleEngine,
                "firestore.rules" to sampleRules,
            ),
            fallbackBaseline = EngineSyncSnapshot(),
        )

        assertEquals("998877665544", snapshot.commitSha)
        assertEquals(listOf("ADD_21", "MUL_21", "SUB_21", "MOD_21", "SCALE_ATK_LP_21"), snapshot.bigInt21Spec.supportedFunctions)
        assertEquals(
            "Targeted firestore.rules parser must read customCardName <= 100 and ignore isValidId <= 64",
            100,
            snapshot.firestoreBounds.maxCardNameLength,
        )
        assertEquals(1000, snapshot.firestoreBounds.maxRulesTextLength)
        assertEquals(listOf("DRAW", "STANDBY", "MAIN", "COMBAT", "END"), snapshot.schemaDefinitions.turnPhases)
        assertEquals(
            listOf(
                "DIRECT_DAMAGE_BURN",
                "LIFE_RESTORE_HEAL",
                "ATK_DEF_BUFF",
                "DRAW_CARDS",
                "RESOURCE_RAMP",
                "TARGET_DESTROY_REMOVAL",
                "NEGATE_AND_COUNTER",
            ),
            snapshot.schemaDefinitions.cardEffectTypes,
        )
        assertEquals(4, snapshot.baseVariants.size)
    }

    @Test
    fun etag304UnchangedFilesUseCachedBodiesAndReturnHealthyInsteadOfFalseBreaking() {
        val contract = HandshakeContract()
        val cachedFiles = mapOf(
            "app/src/main/java/com/example/data/GameEngine.kt" to "object BigInt21Engine { fun add21() = 21 }",
            "firestore.rules" to "data.customCardName.size() <= 100 && data.customCardAbility.size() <= 1000",
            "firebase-blueprint.json" to """{"entities": {"Card": {}}}""",
            "app/src/main/java/com/example/data/Models.kt" to "enum class TurnPhase { DRAW, MAIN, END }",
        )

        val healthyWithCache = EngineCompatibilityAuditor.runBenefitIntegrityAudit(
            contract = contract,
            commitSha = "a1b2c3d4e5f6",
            fetchedFiles = cachedFiles,
            missingFiles = emptyList(),
            remoteEngineConsentStatus = ConsentStatus.CONSENTED.wireValue,
            unchanged304Files = cachedFiles.keys,
        )
        assertEquals(EngineHealthSeverity.HEALTHY.wireValue, healthyWithCache.severity)
        assertEquals("NOT_REQUIRED", healthyWithCache.masterHubAckStatus)
        assertTrue(healthyWithCache.brokenCapabilities.isEmpty())

        // Even if a caller passes empty/unchanged 304 entries with zero missing files, do not false-positive BREAKING
        val healthy304Only = EngineCompatibilityAuditor.runBenefitIntegrityAudit(
            contract = contract,
            commitSha = "a1b2c3d4e5f6",
            fetchedFiles = mapOf(
                "app/src/main/java/com/example/data/GameEngine.kt" to "/* ETAG_304_UNCHANGED */",
                "firestore.rules" to "/* ETAG_304_UNCHANGED */",
                "firebase-blueprint.json" to "/* ETAG_304_UNCHANGED */",
            ),
            missingFiles = emptyList(),
            remoteEngineConsentStatus = null,
            unchanged304Files = setOf(
                "app/src/main/java/com/example/data/GameEngine.kt",
                "firestore.rules",
                "firebase-blueprint.json",
            ),
        )
        assertEquals(EngineHealthSeverity.HEALTHY.wireValue, healthy304Only.severity)
    }

    @Test
    fun benefitIntegrityAuditFlagsBreakingChangesAndSignsHealthStatusPayload() {
        val contract = HandshakeContract()
        val breakingHealth = EngineCompatibilityAuditor.runBenefitIntegrityAudit(
            contract = contract,
            commitSha = "deadbeef123456",
            fetchedFiles = mapOf(
                "app/src/main/java/com/example/data/Models.kt" to "class EmptyModel",
            ),
            missingFiles = listOf("firestore.rules", "firebase-blueprint.json", "GameEngine.kt"),
            remoteEngineConsentStatus = ConsentStatus.REJECTED.wireValue,
        )

        assertEquals(EngineHealthSeverity.BREAKING_INCOMPATIBLE.wireValue, breakingHealth.severity)
        assertEquals("PENDING_ACK", breakingHealth.masterHubAckStatus)
        assertTrue(breakingHealth.brokenCapabilities.contains("HANDSHAKE_CONTRACT_REVOKED"))
        assertTrue(breakingHealth.brokenCapabilities.contains("21_DIGIT_BIGINT_CODES"))
        assertEquals(64, breakingHealth.signatureSha256.length)
    }

    @Test
    fun alignmentWarningShowsPopupWhenMisalignedEvenWithEmptyAdminGateAndDeduplicatesPerSha() {
        val emptyAdminGate = MasterHubAdminGate()
        assertFalse(
            "Empty admin gate must evaluate isMasterHubAdmin as false",
            EngineCompatibilityAuditor.isMasterHubAdmin(emptyAdminGate, emptySet(), "user_1", "u@example.com"),
        )

        val degradedHealth = EngineHealthStatusPayload(
            alertId = "alert-1",
            failingCommitSha = "commit777888",
            severity = EngineHealthSeverity.DEGRADED.wireValue,
            brokenCapabilities = listOf("FIRESTORE_RULES_OR_BLUEPRINT_MISSING"),
        )
        val eval = EngineCompatibilityAuditor.evaluateAlignmentWarning(
            healthStatus = degradedHealth,
            mutualLinkStatus = MutualLinkStatus.LINKED_OUT_OF_SYNC,
            hasCheckedHubLink = true,
            contract = HandshakeContract(),
            stagedSnapshot = null,
            dismissedAlignmentKeys = emptySet(),
        )
        assertTrue(
            "Alignment warning popup MUST trigger for degraded engine or out-of-sync Hub even when admin gate is empty",
            eval.shouldShowPopup,
        )
        assertTrue(eval.reasons.size >= 2)

        val dismissedEval = EngineCompatibilityAuditor.evaluateAlignmentWarning(
            healthStatus = degradedHealth,
            mutualLinkStatus = MutualLinkStatus.LINKED_OUT_OF_SYNC,
            hasCheckedHubLink = true,
            contract = HandshakeContract(),
            stagedSnapshot = null,
            dismissedAlignmentKeys = setOf(eval.alignmentKey),
        )
        assertFalse(
            "Dismissed alignment key MUST suppress duplicate pop-up for the same SHA/state",
            dismissedEval.shouldShowPopup,
        )

        val healthyEval = EngineCompatibilityAuditor.evaluateAlignmentWarning(
            healthStatus = EngineHealthStatusPayload(
                alertId = "ok",
                failingCommitSha = "clean111",
                severity = EngineHealthSeverity.HEALTHY.wireValue,
            ),
            mutualLinkStatus = MutualLinkStatus.LINKED_AND_CURRENT,
            hasCheckedHubLink = true,
            contract = HandshakeContract(),
            stagedSnapshot = null,
            dismissedAlignmentKeys = emptySet(),
        )
        assertFalse("Healthy aligned repos must NOT trigger a warning pop-up", healthyEval.shouldShowPopup)
    }

    @Test
    fun cardFrameReviewerChecksDelimitersUnifiedV2DescriptorLayer20GeometriesAndScripts() {
        val initialState = EngineSyncUiState()
        assertTrue(
            "Card Frame Reviewer MUST load with ZERO default 20-Color Pie colors selected",
            initialState.reviewSelectedPips.isEmpty(),
        )
        assertEquals(
            "Layer 20 Omnichroma must define exactly 6 bespoke sacred celestial geometries",
            6,
            OmnichromaSacredCatalog.sixSacredGeometries.size,
        )

        val snapshot = EngineSyncSnapshot()
        val result = EngineCompatibilityAuditor.reviewCardFrame(
            cardName = "Aether | Vanguard",
            cardType = "Creature",
            rulesText = "Deal 300 damage.",
            codeString = "123456789012345678901",
            statText = "3000/2500",
            selectedPips = listOf("{C}", "{W}", "{Ω·A}"),
            baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
            effectType = "DIRECT_DAMAGE_BURN",
            editorMode = CardEditorMode.MATCH_GAMEPLAY,
            flavorFilters = emptySet(),
            frameWidthPx = 375,
            frameHeightPx = 523,
            snapshot = snapshot,
        )

        assertTrue(result.isCompatible)
        assertTrue(result.issues.any { it.code == "RESERVED_DELIMITER_DETECTED" })
        assertTrue(result.sanitizedDescriptor.startsWith("@V2|HYBRID_FORGE_EDOPRO_MIX|123456789012345678901|Aether / Vanguard|"))
        assertFalse(result.sanitizedDescriptor.contains("Aether | Vanguard"))
        assertTrue(result.legacyDescriptor.startsWith("Aether / Vanguard|{C}{W}{Ω·A}|3000|2500|Creature|"))
        assertTrue(result.generatedLuaScript.contains("c123456789012345678901.lua"))
        assertTrue(result.generatedForgeSvarScript.contains("SVar:CodeString21:123456789012345678901"))
        assertTrue(result.generatedHybridScript.contains("[FORGE_SVAR]") && result.generatedHybridScript.contains("[LUA_21]"))

        // Incompatible effect type & forbidden Filter 19 on Match card must fail review
        val badReview = EngineCompatibilityAuditor.reviewCardFrame(
            cardName = "Broken Card",
            cardType = "Spell",
            rulesText = "Invalid",
            codeString = "123456789012345678901",
            statText = "",
            selectedPips = listOf("{W}"),
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            effectType = "REMOVED_LEGACY_EFFECT",
            editorMode = CardEditorMode.MATCH_GAMEPLAY,
            flavorFilters = setOf(19),
            frameWidthPx = 375,
            frameHeightPx = 523,
            snapshot = snapshot,
        )
        assertFalse(badReview.isCompatible)
        assertTrue(badReview.issues.any { it.code == "EFFECT_TYPE_UNSUPPORTED" })
        assertTrue(badReview.issues.any { it.code == "COST_BOX_TIME_TRAVEL_BLOCKED" })
    }

    @Test
    fun masterHubAdminGateRestrictsAdminActionsStrictlyToAdminRolesIdsOrEmails() {
        val gate = MasterHubAdminGate(
            adminRoleIds = listOf("role_admin_99"),
            adminUserIds = listOf("user_founder_1"),
            adminEmails = listOf("admin@nexus-hub.org"),
        )
        assertTrue(EngineCompatibilityAuditor.isMasterHubAdmin(gate, setOf("role_admin_99"), "", ""))
        assertTrue(EngineCompatibilityAuditor.isMasterHubAdmin(gate, emptySet(), "user_founder_1", ""))
        assertTrue(EngineCompatibilityAuditor.isMasterHubAdmin(gate, emptySet(), "", "ADMIN@NEXUS-HUB.ORG"))
        assertFalse(EngineCompatibilityAuditor.isMasterHubAdmin(gate, setOf("role_player_1"), "user_2", "player@example.com"))
    }
}
