package com.example

import com.example.data.nexus.NexusHubRepository
import com.example.domain.nexus.AiUserComboEntry
import com.example.domain.nexus.CardCodeValidator
import com.example.domain.nexus.CardEditorMode
import com.example.domain.nexus.CardIdKind
import com.example.domain.nexus.NexusClientDefinition
import com.example.domain.nexus.NexusCodedCard
import com.example.domain.nexus.NexusRepoLinkManifest
import com.example.domain.nexus.NexusSchemaValidator
import com.example.domain.nexus.NotebookFormatEntry
import com.example.domain.nexus.SchemaValidationResult
import com.example.domain.nexus.TwentyColorPieCatalog
import com.example.ui.nexus.NexusHubUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class NexusHubBridgeUnitTest {

    private val seedIndex = NexusHubRepository.buildSeedClientIndex()

    @Test
    fun canonicalRepoLinkManifestPassesRuntimeSchemaValidation() {
        val manifest = NexusRepoLinkManifest()
        assertEquals("Summer-Warrior/Project-MPV-RP-Master-Hub", manifest.linkedHubRepo)
        val result = NexusSchemaValidator.validateRepoLinkManifest(manifest)
        assertTrue(result is SchemaValidationResult.Valid)

        val invalid = manifest.copy(thisRepo = "not-a-valid-slug", bridgeSchemaVersion = "v1")
        val invalidResult = NexusSchemaValidator.validateRepoLinkManifest(invalid)
        assertTrue(invalidResult is SchemaValidationResult.Invalid)
    }

    @Test
    fun lockedClientFirewallBlocksUpstreamClientsAndResolvesNonLockedFork() {
        val lockedEdoPro = seedIndex.clients.first { it.id == "upstream-locked-edopro" }
        assertTrue(lockedEdoPro.upstreamSourceLock)
        assertFalse(CardCodeValidator.isClientWritable(lockedEdoPro))

        val card = NexusCodedCard(
            cardId = "12345678",
            targetClientId = lockedEdoPro.id,
            cardName = "Test Card",
        )
        val report = CardCodeValidator.validateCard(card, lockedEdoPro)
        assertFalse(report.isValid)
        assertTrue(report.blockingErrors.any { it.code == "LOCKED_UPSTREAM_CLIENT" })

        val fork = CardCodeValidator.resolveNonLockedFork(lockedEdoPro, seedIndex.clients)
        assertNotNull(fork)
        assertFalse(fork!!.upstreamSourceLock)
        assertEquals("custom-21digit-duel-clone", fork.id)
    }

    @Test
    fun bigInteger21DigitRegisterMathHasZeroOverflowAndRejects22Digits() {
        val nearMax = "999999999999999999998"
        assertEquals(
            "999999999999999999999",
            CardCodeValidator.computeBigIntRegisterOffset(nearMax, BigInteger.ONE),
        )
        assertNull(
            "Exceeding 21 digits must return null rather than overflowing",
            CardCodeValidator.computeBigIntRegisterOffset(nearMax, BigInteger.TWO),
        )

        val nonLocked = seedIndex.clients.first { !it.upstreamSourceLock }
        val valid21 = NexusCodedCard(
            cardId = "123456789012345678901",
            register21Digit = "123456789012345678901",
            targetClientId = nonLocked.id,
            cardName = "Chronicle Engine",
            codingVariables = mapOf("REG_A" to "999999999999999999999"),
        )
        val validReport = CardCodeValidator.validateCard(valid21, nonLocked)
        assertTrue(validReport.isValid)
        assertEquals(CardIdKind.NUMERIC_21_DIGIT, validReport.cardIdKind)

        val bad22Var = valid21.copy(
            codingVariables = mapOf("BAD_VAR" to "id_1234567890123456789012_end"),
        )
        val badReport = CardCodeValidator.validateCard(bad22Var, nonLocked)
        assertFalse(badReport.isValid)
        assertTrue(badReport.blockingErrors.any { it.code == "CODING_VAR_22_DIGITS" })
    }

    @Test
    fun strictTimeTravelClientRuleBlocksFilters19To21OnMatchCardsAndAllowsEndOrExtraTurn() {
        val nonLocked = seedIndex.clients.first { !it.upstreamSourceLock }

        val allowedMatchCard = NexusCodedCard(
            cardId = "550e8400-e29b-41d4-a716-446655440000",
            targetClientId = nonLocked.id,
            cardName = "Temporal Cadence",
            rulesText = "Take an Extra Turn after this phase.",
            editorMode = CardEditorMode.MATCH_GAMEPLAY.wireValue,
            allowTimeTravelFlagsOnMatchCards = false,
            temporalEffects = listOf("End Turn", "Extra Turn"),
        )
        val okReport = CardCodeValidator.validateCard(allowedMatchCard, nonLocked)
        assertTrue(okReport.isValid)
        assertEquals(CardIdKind.UUID, okReport.cardIdKind)

        val blockedFilterCard = allowedMatchCard.copy(flavorFilters = listOf(19, 20, 21))
        val blockedReport = CardCodeValidator.validateCard(blockedFilterCard, nonLocked)
        assertFalse(blockedReport.isValid)
        assertTrue(blockedReport.blockingErrors.any { it.code == "MATCH_CARD_FORBIDDEN_FILTERS" })

        val dossierCard = blockedFilterCard.copy(
            editorMode = CardEditorMode.AVATAR_PROFILE_MEMORIAL_DOSSIER.wireValue,
        )
        val dossierReport = CardCodeValidator.validateCard(dossierCard, nonLocked)
        assertTrue("Avatar Profile / Memorial Forge Dossier mode explicitly permits Filters 19-21", dossierReport.isValid)
    }

    @Test
    fun canonical20ColorPiePickerLoadsWithZeroDefaultColorsAndValidatesAllLayers() {
        val initialUi = NexusHubUiState()
        assertTrue(
            "20-Color Pie picker MUST load with ZERO default colors selected",
            initialUi.selectedColorPiePips.isEmpty(),
        )
        assertEquals(20, TwentyColorPieCatalog.baseTwentyColors.size)
        assertEquals(
            listOf("W", "Iv", "Cy", "Az", "U", "In", "Vi", "Ob", "B", "Cr", "Mg", "Sc", "R", "Or", "Am", "Ch", "G", "Ja", "Te", "Aq"),
            TwentyColorPieCatalog.baseTwentyColors.map { it.code.removeSurrounding("{", "}") },
        )
        assertEquals(6, TwentyColorPieCatalog.layer20OmegaPips.size)

        val validPips = listOf(
            "{C}",
            "{W}",
            "{Iv}",
            "{Az}",
            "{Vi}",
            "{Sc}",
            "{Or}",
            "{Am}",
            "{Ch}",
            "{Ja}",
            "{Aq}",
            "{W:T}",
            "{Ob:S}",
            "{Am:M}",
            "{W·Aq}",
            "{W:T·U:S}",
            "{R/G}",
            "{W|B}",
            "{R>G}",
            "{2Cr/Ob}",
            "{-R}",
            "{-(W,B)}",
            "{Ω}",
            "{Ω·M}",
            "{Ω·C}",
            "{Ω·S}",
            "{Ω·V}",
            "{Ω·E}",
            "{Ω·A}",
        )
        validPips.forEach { pip ->
            assertTrue("Expected valid canonical pip '$pip'", CardCodeValidator.isValidColorPiePip(pip))
        }
        assertFalse(CardCodeValidator.isValidColorPiePip("{ZZ}"))
        assertFalse(CardCodeValidator.isValidColorPiePip("{Gd:M}"))
        assertFalse(CardCodeValidator.isValidColorPiePip("{W:X}"))
    }

    @Test
    fun changelogDiffDetectsNewNonLockedClientsFormatsCombosAndLuaScriptGeneratesCleanly() {
        val nonLocked = seedIndex.clients.first { !it.upstreamSourceLock }
        val incoming = seedIndex.copy(
            indexRevision = "hub-rev-2",
            clients = seedIndex.clients + NexusClientDefinition(
                id = "custom-new-realm",
                name = "New Realm Client",
                upstreamSourceLock = false,
            ),
            notebookFormats = seedIndex.notebookFormats + NotebookFormatEntry(
                id = "nb-new-format",
                name = "New Notebook Format",
                realm = "Custom",
            ),
            aiUserCombos = seedIndex.aiUserCombos + AiUserComboEntry(
                id = "combo-new",
                name = "New Hybrid Combo",
                sourceClients = listOf(nonLocked.id),
            ),
        )

        val diff = NexusSchemaValidator.computeChangelogDiff(seedIndex, incoming)
        assertTrue(diff.hasChanges)
        assertEquals(1, diff.addedClients.size)
        assertEquals(1, diff.addedNotebookFormats.size)
        assertEquals(1, diff.addedCombos.size)

        val lua = CardCodeValidator.generateLuaScript(
            card = NexusCodedCard(
                cardId = "42",
                register21Digit = "42",
                targetClientId = nonLocked.id,
                cardName = "Zero Art Blade",
                colorPiePips = listOf("{W·Aq}", "{Ω·E}"),
                image = null,
            ),
            targetClient = nonLocked,
        )
        assertTrue(lua.contains("c000000000000000000042.lua"))
        assertTrue(lua.contains("upstreamSourceLock=false"))
        assertTrue(lua.contains("allow_time_travel_match_flags = false"))
    }
}
