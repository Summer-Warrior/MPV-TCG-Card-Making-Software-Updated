package com.example

import com.example.domain.comparison.CardOrigin
import com.example.domain.frameworks.FrameworkCategory
import com.example.domain.frameworks.FrameworkMseScaffoldGenerator
import com.example.domain.frameworks.UniversalFrameworkCatalog
import com.example.domain.telemetry.CardInteractionMetric
import com.example.domain.telemetry.CardUsageAnalyzer
import com.example.domain.telemetry.CardUsageMetric
import com.example.domain.telemetry.CustomCardUsageReport
import com.example.domain.telemetry.TelemetryBadgeKind
import com.example.domain.telemetry.TelemetryInteractionType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CardUsageAndFrameworksUnitTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun cardUsageAnalyzer_deduplicatesAndComputesOriginMetricsAndBadges() {
        val report1 = CustomCardUsageReport(
            reportId = "rep_001",
            generatedAt = "2026-11-26T01:00:00Z",
            totalMatchesTracked = 10,
            cardStats = listOf(
                CardUsageMetric(
                    cardId = "oc_overtuned",
                    cardName = "Astral Sovereign",
                    cardOrigin = CardOrigin.ORIGINAL_OC_CARD.wireValue,
                    matchesIncluded = 10,
                    timesDrawn = 9,
                    timesPlayed = 8,
                    winsWhenPlayed = 7,
                    lossesWhenPlayed = 1,
                    averageTurnPlayed = 3.5,
                    ruleOrScriptErrorCount = 0,
                ),
                CardUsageMetric(
                    cardId = "canon_underplayed",
                    cardName = "Old Relic",
                    cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                    matchesIncluded = 10,
                    timesDrawn = 5,
                    timesPlayed = 1,
                    winsWhenPlayed = 0,
                    lossesWhenPlayed = 1,
                    averageTurnPlayed = 6.0,
                    ruleOrScriptErrorCount = 2,
                ),
            ),
            interactions = listOf(
                CardInteractionMetric(
                    sourceCardId = "oc_overtuned",
                    sourceCardName = "Astral Sovereign",
                    sourceCardOrigin = CardOrigin.ORIGINAL_OC_CARD.wireValue,
                    targetCardId = "canon_underplayed",
                    targetCardName = "Old Relic",
                    targetCardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                    interactionType = TelemetryInteractionType.COMBO_SYNERGY.wireValue,
                    occurrenceCount = 4,
                ),
                CardInteractionMetric(
                    sourceCardId = "var_1",
                    sourceCardName = "Alternate Relic",
                    sourceCardOrigin = CardOrigin.FAN_ALTERNATE_VARIANT.wireValue,
                    targetCardId = "canon_underplayed",
                    targetCardName = "Old Relic",
                    targetCardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
                    interactionType = TelemetryInteractionType.SCRIPT_EXCEPTION.wireValue,
                    occurrenceCount = 2,
                ),
            ),
        )

        // Duplicate reportId should be ignored during deduplication
        val duplicateReport = report1.copy(totalMatchesTracked = 999)
        val merged = CardUsageAnalyzer.mergeAndDeduplicate(listOf(report1), listOf(duplicateReport))
        assertEquals(1, merged.size)

        val analysis = CardUsageAnalyzer.analyze(
            reports = listOf(report1, duplicateReport),
            originFilter = null,
            interactionTypeFilter = null,
        )

        assertEquals(1, analysis.totalReportsCount)
        assertEquals(10, analysis.totalMatchesTracked)
        assertEquals(4, analysis.canonToOcInteractionCount)
        assertEquals(2, analysis.canonToVariantInteractionCount)
        assertEquals(3, analysis.totalErrorEvents) // 2 metric errors + 1 SCRIPT_EXCEPTION interaction row

        val ocRow = analysis.cardRows.first { it.cardId == "oc_overtuned" }
        assertTrue(ocRow.badges.contains(TelemetryBadgeKind.HIGH_WIN_RATE_OUTLIER))
        assertFalse(ocRow.badges.contains(TelemetryBadgeKind.UNDERPLAYED))

        val canonRow = analysis.cardRows.first { it.cardId == "canon_underplayed" }
        assertTrue(canonRow.badges.contains(TelemetryBadgeKind.UNDERPLAYED))
        assertTrue(canonRow.badges.contains(TelemetryBadgeKind.SCRIPT_OR_RULE_ERROR))

        val filteredByInteraction = CardUsageAnalyzer.analyze(
            reports = listOf(report1),
            interactionTypeFilter = TelemetryInteractionType.SCRIPT_EXCEPTION,
        )
        assertEquals(1, filteredByInteraction.interactions.size)
        assertEquals(TelemetryInteractionType.SCRIPT_EXCEPTION, filteredByInteraction.interactions.first().interactionType)
    }

    @Test
    fun universalFrameworksCatalog_contains110PlusFrameworksAndUltimateGuardrails() {
        val assetFile = listOf(
            File("src/main/assets/frameworks/tcg_card_frameworks.json"),
            File("app/src/main/assets/frameworks/tcg_card_frameworks.json"),
        ).firstOrNull { it.exists() }
        assertNotNull("Bundled tcg_card_frameworks.json must exist", assetFile)

        val adapter = moshi.adapter(UniversalFrameworkCatalog::class.java)
        val catalog = adapter.fromJson(assetFile!!.readText())
        assertNotNull(catalog)

        val frameworks = catalog!!.frameworks
        assertTrue("Expected >= 110 frameworks, found ${frameworks.size}", frameworks.size >= 110)

        val uniqueIds = frameworks.map { it.id }.toSet()
        assertEquals("All framework IDs must be unique", frameworks.size, uniqueIds.size)

        // Verify all 8 categories are populated
        val presentCategories = frameworks.map { it.categoryEnum }.toSet()
        FrameworkCategory.entries.forEach { category ->
            assertTrue("Missing framework category: $category", category in presentCategories)
        }

        // Verify Ultimate TCG enforces 0 default selected colors + Time-Travel guardrail
        val ultimate = frameworks.firstOrNull { it.id == "ultimate_tcg_wip" }
        assertNotNull("Ultimate TCG framework must exist", ultimate)
        assertTrue(ultimate!!.defaultSelectedColors.isEmpty())
        assertTrue(ultimate.timeTravelMatchGuardrail)

        // Verify clean-room scaffold generation keeps default art blank/null
        val scaffold = FrameworkMseScaffoldGenerator.generateScaffold(ultimate)
        assertTrue(scaffold.gameFileContent.contains("name: image"))
        assertTrue(scaffold.gameFileContent.contains("strictly null/blank by default"))
        assertTrue(scaffold.styleFileContent.contains("card style:"))
        assertFalse(scaffold.gameFileContent.contains("default_art"))
    }
}
