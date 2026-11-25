package com.example.domain.telemetry

import com.example.domain.comparison.CardOrigin

/**
 * Pure Kotlin analytics engine for Custom Card Usage & Interaction Telemetry Reports.
 * Deduplicates reports by `reportId`, aggregates per-origin (`CANON_ORIGINAL` vs
 * `FAN_ALTERNATE_VARIANT` vs `ORIGINAL_OC_CARD`) win/play rates, flags balance & script
 * outliers, and surfaces Canon <-> OC / AU interaction matrices.
 */

enum class TelemetryBadgeKind(val label: String) {
    HIGH_WIN_RATE_OUTLIER("Win Rate > 65% Outlier"),
    UNDERPLAYED("Underplayed"),
    SCRIPT_OR_RULE_ERROR("Script/Rule Error Detected"),
}

data class AggregatedCardUsageRow(
    val cardId: String,
    val register21Digit: String?,
    val cardName: String,
    val targetClientId: String,
    val splitOffSetId: String?,
    val parentOfficialSetId: String?,
    val cardOrigin: CardOrigin,
    val linkedCanonCardId: String?,
    val matchesIncluded: Int,
    val timesDrawn: Int,
    val timesPlayed: Int,
    val timesActivated: Int,
    val timesAttacked: Int,
    val timesDestroyed: Int,
    val totalDamageDealt: Long,
    val winsWhenPlayed: Int,
    val lossesWhenPlayed: Int,
    val playRatePercent: Double,
    val winRatePercent: Double,
    val averageTurnPlayed: Double,
    val ruleOrScriptErrorCount: Int,
    val badges: List<TelemetryBadgeKind>,
)

data class OriginPerformanceSummary(
    val origin: CardOrigin,
    val uniqueCards: Int,
    val totalPlays: Int,
    val averageWinRatePercent: Double,
    val totalErrors: Int,
)

data class AggregatedInteractionRow(
    val sourceCardId: String,
    val sourceCardName: String,
    val sourceOrigin: CardOrigin,
    val targetCardId: String,
    val targetCardName: String,
    val targetOrigin: CardOrigin,
    val interactionType: TelemetryInteractionType,
    val totalOccurrences: Int,
    val phase: String,
    val baseVariantId: String,
    val lastOccurredAt: String,
    val sampleLogLine: String,
    val isCrossOriginCanonOc: Boolean,
)

data class CardUsageAnalysisResult(
    val totalReportsCount: Int,
    val totalMatchesTracked: Int,
    val cardRows: List<AggregatedCardUsageRow>,
    val originSummaries: List<OriginPerformanceSummary>,
    val interactions: List<AggregatedInteractionRow>,
    val canonToOcInteractionCount: Int,
    val canonToVariantInteractionCount: Int,
    val totalErrorEvents: Int,
)

object CardUsageAnalyzer {

    /**
     * Deduplicates incoming reports strictly by `reportId` while preserving newest first ordering.
     */
    fun mergeAndDeduplicate(
        existing: List<CustomCardUsageReport>,
        incoming: List<CustomCardUsageReport>,
    ): List<CustomCardUsageReport> {
        val byId = linkedMapOf<String, CustomCardUsageReport>()
        existing.forEach { report ->
            if (report.reportId.isNotBlank()) byId[report.reportId] = report
        }
        incoming.forEach { report ->
            if (report.reportId.isNotBlank() && !byId.containsKey(report.reportId)) {
                byId[report.reportId] = report
            }
        }
        return byId.values.sortedByDescending { it.generatedAt }
    }

    /**
     * Aggregates deduplicated telemetry reports into per-card performance rows,
     * per-origin benchmarks, and cross-origin interaction insights.
     */
    fun analyze(
        reports: List<CustomCardUsageReport>,
        originFilter: CardOrigin? = null,
        interactionTypeFilter: TelemetryInteractionType? = null,
        splitOffSetIdFilter: String? = null,
    ): CardUsageAnalysisResult {
        val deduped = mergeAndDeduplicate(emptyList(), reports)
            .filter { r ->
                splitOffSetIdFilter.isNullOrBlank() ||
                    r.splitOffSetId.equals(splitOffSetIdFilter, ignoreCase = true)
            }

        val totalMatches = deduped.sumOf { it.totalMatchesTracked.coerceAtLeast(0) }

        // Group card metrics by cardId + origin
        val groupedCards = linkedMapOf<String, MutableList<CardUsageMetric>>()
        deduped.forEach { report ->
            report.cardStats.forEach { stat ->
                val key = "${stat.cardId.trim().lowercase()}|${CardOrigin.fromWire(stat.cardOrigin).wireValue}"
                groupedCards.getOrPut(key) { mutableListOf() }.add(stat)
            }
        }

        val allRows = groupedCards.values.map { list ->
            val first = list.first()
            val origin = CardOrigin.fromWire(first.cardOrigin)
            val matchesInc = list.sumOf { it.matchesIncluded }
            val drawn = list.sumOf { it.timesDrawn }
            val played = list.sumOf { it.timesPlayed }
            val activated = list.sumOf { it.timesActivated }
            val attacked = list.sumOf { it.timesAttacked }
            val destroyed = list.sumOf { it.timesDestroyed }
            val dmg = list.sumOf { it.totalDamageDealt }
            val wins = list.sumOf { it.winsWhenPlayed }
            val losses = list.sumOf { it.lossesWhenPlayed }
            val errors = list.sumOf { it.ruleOrScriptErrorCount }

            val weightedTurnDenom = list.sumOf { it.timesPlayed.coerceAtLeast(1) }
            val avgTurn = if (weightedTurnDenom > 0) {
                list.sumOf { it.averageTurnPlayed * it.timesPlayed.coerceAtLeast(1) } / weightedTurnDenom.toDouble()
            } else 0.0

            val playRate = if (matchesInc > 0) {
                ((played.toDouble() / matchesInc.toDouble()) * 100.0).coerceIn(0.0, 100.0)
            } else 0.0

            val decided = wins + losses
            val winRate = if (decided > 0) {
                (wins.toDouble() / decided.toDouble()) * 100.0
            } else 0.0

            val badges = buildList {
                if (decided >= 3 && winRate > 65.0) add(TelemetryBadgeKind.HIGH_WIN_RATE_OUTLIER)
                if (matchesInc >= 5 && playRate < 15.0) add(TelemetryBadgeKind.UNDERPLAYED)
                if (errors > 0) add(TelemetryBadgeKind.SCRIPT_OR_RULE_ERROR)
            }

            AggregatedCardUsageRow(
                cardId = first.cardId,
                register21Digit = list.firstNotNullOfOrNull { it.register21Digit?.takeIf(String::isNotBlank) },
                cardName = first.cardName,
                targetClientId = first.targetClientId,
                splitOffSetId = first.splitOffSetId,
                parentOfficialSetId = first.parentOfficialSetId,
                cardOrigin = origin,
                linkedCanonCardId = first.linkedCanonCardId,
                matchesIncluded = matchesInc,
                timesDrawn = drawn,
                timesPlayed = played,
                timesActivated = activated,
                timesAttacked = attacked,
                timesDestroyed = destroyed,
                totalDamageDealt = dmg,
                winsWhenPlayed = wins,
                lossesWhenPlayed = losses,
                playRatePercent = playRate,
                winRatePercent = winRate,
                averageTurnPlayed = avgTurn,
                ruleOrScriptErrorCount = errors,
                badges = badges,
            )
        }.sortedByDescending { it.timesPlayed }

        val originSummaries = CardOrigin.entries.map { origin ->
            val rows = allRows.filter { it.cardOrigin == origin }
            val totalWins = rows.sumOf { it.winsWhenPlayed }
            val totalLosses = rows.sumOf { it.lossesWhenPlayed }
            val avgWr = if (totalWins + totalLosses > 0) {
                (totalWins.toDouble() / (totalWins + totalLosses).toDouble()) * 100.0
            } else 0.0
            OriginPerformanceSummary(
                origin = origin,
                uniqueCards = rows.size,
                totalPlays = rows.sumOf { it.timesPlayed },
                averageWinRatePercent = avgWr,
                totalErrors = rows.sumOf { it.ruleOrScriptErrorCount },
            )
        }

        // Aggregate interactions
        val groupedInteractions = linkedMapOf<String, MutableList<CardInteractionMetric>>()
        deduped.forEach { report ->
            report.interactions.forEach { ix ->
                val type = TelemetryInteractionType.fromWire(ix.interactionType)
                val key = "${ix.sourceCardId.lowercase()}|${ix.targetCardId.lowercase()}|${type.wireValue}"
                groupedInteractions.getOrPut(key) { mutableListOf() }.add(ix)
            }
        }

        val allInteractions = groupedInteractions.values.map { list ->
            val first = list.first()
            val latest = list.maxByOrNull { it.lastOccurredAt } ?: first
            val srcOrigin = CardOrigin.fromWire(first.sourceCardOrigin)
            val tgtOrigin = CardOrigin.fromWire(first.targetCardOrigin)
            val type = TelemetryInteractionType.fromWire(first.interactionType)
            val isCross = (srcOrigin == CardOrigin.CANON_ORIGINAL && tgtOrigin == CardOrigin.ORIGINAL_OC_CARD) ||
                (srcOrigin == CardOrigin.ORIGINAL_OC_CARD && tgtOrigin == CardOrigin.CANON_ORIGINAL)

            AggregatedInteractionRow(
                sourceCardId = first.sourceCardId,
                sourceCardName = first.sourceCardName,
                sourceOrigin = srcOrigin,
                targetCardId = first.targetCardId,
                targetCardName = first.targetCardName,
                targetOrigin = tgtOrigin,
                interactionType = type,
                totalOccurrences = list.sumOf { it.occurrenceCount.coerceAtLeast(1) },
                phase = latest.phase,
                baseVariantId = latest.baseVariantId,
                lastOccurredAt = latest.lastOccurredAt,
                sampleLogLine = latest.sampleLogLine.ifBlank { first.sampleLogLine },
                isCrossOriginCanonOc = isCross,
            )
        }.sortedByDescending { it.totalOccurrences }

        val canonToOcCount = allInteractions
            .filter { it.isCrossOriginCanonOc }
            .sumOf { it.totalOccurrences }
        val canonToVariantCount = allInteractions
            .filter {
                (it.sourceOrigin == CardOrigin.CANON_ORIGINAL && it.targetOrigin == CardOrigin.FAN_ALTERNATE_VARIANT) ||
                    (it.sourceOrigin == CardOrigin.FAN_ALTERNATE_VARIANT && it.targetOrigin == CardOrigin.CANON_ORIGINAL)
            }
            .sumOf { it.totalOccurrences }

        val filteredRows = if (originFilter == null) allRows else allRows.filter { it.cardOrigin == originFilter }
        val filteredInteractions = allInteractions.filter { ix ->
            val matchesOrigin = originFilter == null || ix.sourceOrigin == originFilter || ix.targetOrigin == originFilter
            val matchesType = interactionTypeFilter == null || ix.interactionType == interactionTypeFilter
            matchesOrigin && matchesType
        }

        val totalErrors = allRows.sumOf { it.ruleOrScriptErrorCount } +
            allInteractions.count {
                it.interactionType == TelemetryInteractionType.RULE_VALIDATION_ERROR ||
                    it.interactionType == TelemetryInteractionType.SCRIPT_EXCEPTION
            }

        return CardUsageAnalysisResult(
            totalReportsCount = deduped.size,
            totalMatchesTracked = totalMatches,
            cardRows = filteredRows,
            originSummaries = originSummaries,
            interactions = filteredInteractions,
            canonToOcInteractionCount = canonToOcCount,
            canonToVariantInteractionCount = canonToVariantCount,
            totalErrorEvents = totalErrors,
        )
    }
}
