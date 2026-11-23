package com.example

import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.ConsentStatus
import com.example.domain.nexus.DualConsentRecord
import com.example.domain.nexus.EngineCompatibilityAuditor
import com.example.domain.nexus.EngineHealthSeverity
import com.example.domain.nexus.EngineSyncSnapshot
import com.example.domain.nexus.HandshakeContract
import com.example.domain.nexus.MasterHubAdminGate
import com.example.domain.nexus.OmnichromaSacredCatalog
import com.example.ui.nexus.EngineSyncUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineHandshakeAndReviewerUnitTest {

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
    fun liveEngineSourceParserExtractsVariantsBigInt21EnumsAndFirestoreBounds() {
        val sampleModels = """
            enum class TurnPhase { UNTAP, DRAW, MAIN_1, BATTLE, END }
            enum class CardEffectType { ACTIVATED, TRIGGERED, QUICK, END_TURN, EXTRA_TURN }
            data class CardTemplate(val id: String, val baseVariantId: String = "EDOPRO_CUSTOM_FORK")
            val variants = listOf("EDOPRO_CUSTOM_FORK", "FORGE_CUSTOM_CLONE", "HYBRID_FORGE_EDOPRO_MIX")
        """.trimIndent()
        val sampleEngine = """
            object BigInt21Engine {
                val ops = listOf("ADD_21", "MUL_21", "SUB_21", "MOD_21", "SCALE_ATK_LP_21")
            }
        """.trimIndent()
        val sampleRules = """
            function validCard(data) {
              return data.name.size() <= 96 && data.rulesText.size() <= 1500;
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
        assertEquals(96, snapshot.firestoreBounds.maxCardNameLength)
        assertEquals(1500, snapshot.firestoreBounds.maxRulesTextLength)
        assertTrue(snapshot.schemaDefinitions.turnPhases.contains("BATTLE"))
        assertTrue(snapshot.schemaDefinitions.cardEffectTypes.contains("EXTRA_TURN"))
        assertEquals(3, snapshot.baseVariants.size)
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
    fun cardFrameReviewerChecksDelimitersLayer20GeometriesAndGeneratesAllThreeScripts() {
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
            rulesText = "Take an Extra Turn.",
            codeString = "123456789012345678901",
            statText = "3000/2500",
            selectedPips = listOf("{C}", "{W}", "{Ω·A}"),
            baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
            effectType = "EXTRA_TURN",
            editorMode = CardEditorMode.MATCH_GAMEPLAY,
            flavorFilters = emptySet(),
            frameWidthPx = 375,
            frameHeightPx = 523,
            snapshot = snapshot,
        )

        assertTrue(result.isCompatible)
        assertTrue(result.issues.any { it.code == "RESERVED_DELIMITER_DETECTED" })
        assertFalse(result.sanitizedDescriptor.contains("Aether | Vanguard"))
        assertTrue(result.sanitizedDescriptor.contains("Aether / Vanguard"))
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
    fun masterHubAdminGateRestrictsBlockingAlertStrictlyToAdminRolesIdsOrEmails() {
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
