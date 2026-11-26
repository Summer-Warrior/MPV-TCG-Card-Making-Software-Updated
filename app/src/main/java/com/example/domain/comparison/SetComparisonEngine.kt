package com.example.domain.comparison

import com.example.data.mse.MseCardView
import com.example.data.mse.MseSetArchive
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure Kotlin domain engine for:
 * 1. Side-by-side Custom / Fan-Fiction Split-Off Set vs. Official Set Comparison
 * 2. Mandatory Canon Lore Preservation auditing (`preserveAllOriginalOfficialCards == true`)
 * 3. Balance, Cost Curve, Stat-to-Cost bracket comparison, and Outlier / Power-Creep flags
 * 4. Metadata-only reference baseline copying (STRICTLY keeping `image == null`)
 * 5. Real Card-Forge `cardsfolder` `.txt` parser and dynamic Style Field Mapping
 *
 * Zero Android UI / Compose imports.
 */

enum class CardOrigin(val wireValue: String, val displayLabel: String) {
    CANON_ORIGINAL("CANON_ORIGINAL", "Canon Original • Preserved"),
    FAN_ALTERNATE_VARIANT("FAN_ALTERNATE_VARIANT", "Fan Alternate Variant"),
    ORIGINAL_OC_CARD("ORIGINAL_OC_CARD", "Original OC / Fan-Fiction"),
    ;

    companion object {
        fun fromWire(raw: String?): CardOrigin =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
                ?: ORIGINAL_OC_CARD
    }
}

enum class SplitOffKind(val wireValue: String, val displayLabel: String) {
    FAN_FICTION_AU("FAN_FICTION_AU", "Fan-Fiction AU"),
    SEQUEL_EXPANSION("SEQUEL_EXPANSION", "Sequel Expansion"),
    CROSSOVER_HYBRID("CROSSOVER_HYBRID", "Crossover Hybrid"),
    NOTEBOOK_REALM("NOTEBOOK_REALM", "Notebook Realm"),
    LORE_BRANCH("LORE_BRANCH", "Lore Branch"),
    ;

    companion object {
        fun fromWire(raw: String?): SplitOffKind =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
                ?: FAN_FICTION_AU
    }
}

@JsonClass(generateAdapter = true)
data class SplitOffSetManifest(
    @Json(name = "splitOffId") val splitOffId: String,
    @Json(name = "splitOffTitle") val splitOffTitle: String,
    @Json(name = "splitOffKind") val splitOffKind: String = SplitOffKind.FAN_FICTION_AU.wireValue,
    @Json(name = "parentOfficialSetId") val parentOfficialSetId: String,
    @Json(name = "parentOfficialSetName") val parentOfficialSetName: String,
    @Json(name = "parentOfficialSource") val parentOfficialSource: String,
    @Json(name = "parentGameId") val parentGameId: String,
    @Json(name = "preserveAllOriginalOfficialCards") val preserveAllOriginalOfficialCards: Boolean = true,
    @Json(name = "preservedCanonCardCount") val preservedCanonCardCount: Int = 0,
    @Json(name = "canonDivergenceNotes") val canonDivergenceNotes: String = "",
    @Json(name = "upstreamSourceLock") val upstreamSourceLock: Boolean = false,
    @Json(name = "frameworkId") val frameworkId: String? = null,
    @Json(name = "createdAt") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class OfficialSetSummary(
    @Json(name = "setId") val setId: String,
    @Json(name = "setName") val setName: String,
    @Json(name = "source") val source: String, // scryfall | ygoprodeck | pokemontcg | local_cdb | local_mse_set | forge_txt | nexus_pack | open_url
    @Json(name = "gameId") val gameId: String,
    @Json(name = "cardCount") val cardCount: Int = 0,
    @Json(name = "releasedAt") val releasedAt: String = "",
    @Json(name = "iconUrl") val iconUrl: String? = null,
    @Json(name = "queryParam") val queryParam: String = "",
)

@JsonClass(generateAdapter = true)
data class ComparableCardRecord(
    @Json(name = "id") val id: String,
    @Json(name = "passcodeOr21Digit") val passcodeOr21Digit: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "costOrLevel") val costOrLevel: String = "",
    @Json(name = "numericCost") val numericCost: Int? = null,
    @Json(name = "typeLine") val typeLine: String = "",
    @Json(name = "superType") val superType: String = "",
    @Json(name = "subType") val subType: String = "",
    @Json(name = "powerOrAtk") val powerOrAtk: String = "",
    @Json(name = "toughnessOrDef") val toughnessOrDef: String = "",
    @Json(name = "rulesText") val rulesText: String = "",
    @Json(name = "keywords") val keywords: List<String> = emptyList(),
    @Json(name = "colorsOrAttributes") val colorsOrAttributes: List<String> = emptyList(),
    @Json(name = "rarity") val rarity: String = "",
    @Json(name = "cardOrigin") val cardOrigin: String = CardOrigin.ORIGINAL_OC_CARD.wireValue,
    @Json(name = "linkedCanonCardId") val linkedCanonCardId: String? = null,
    @Json(name = "extraFields") val extraFields: Map<String, String> = emptyMap(),
    @Json(name = "image") val image: String? = null,
) {
    val originEnum: CardOrigin get() = CardOrigin.fromWire(cardOrigin)
}

@JsonClass(generateAdapter = true)
data class ComparableSet(
    @Json(name = "setId") val setId: String,
    @Json(name = "setName") val setName: String,
    @Json(name = "source") val source: String,
    @Json(name = "gameId") val gameId: String,
    @Json(name = "cards") val cards: List<ComparableCardRecord>,
    @Json(name = "splitOffManifest") val splitOffManifest: SplitOffSetManifest? = null,
)

enum class ComparisonCategory(val displayLabel: String) {
    CANON_PRESERVED("Canon Original (Preserved)"),
    FAN_ALTERNATE_VARIANT("Fan Alternate Variant (Linked)"),
    ORIGINAL_OC_CARD("Original Fan-Fiction / OC"),
    MODIFIED_FROM_OFFICIAL("Modified vs. Official"),
    MISSING_CANON_OFFICIAL("Missing Official Card"),
}

data class CardFieldDiff(
    val fieldName: String,
    val leftValue: String,
    val rightValue: String,
)

data class CardDiffEntry(
    val category: ComparisonCategory,
    val leftCard: ComparableCardRecord?,
    val rightOfficialCard: ComparableCardRecord?,
    val fieldDiffs: List<CardFieldDiff>,
)

data class CostBracketStat(
    val bracketLabel: String,
    val leftCount: Int,
    val rightCount: Int,
    val leftAvgStatTotal: Double?,
    val rightAvgStatTotal: Double?,
)

data class DistributionDelta(
    val label: String,
    val leftCount: Int,
    val rightCount: Int,
    val delta: Int,
)

data class BalanceOutlierFlag(
    val cardId: String,
    val cardName: String,
    val cardOrigin: CardOrigin,
    val reason: String,
    val severity: String, // "HIGH" | "MEDIUM"
)

data class SetComparisonResult(
    val leftSetName: String,
    val rightOfficialSetName: String,
    val isSplitOffComparison: Boolean,
    val canonPreservedCount: Int,
    val parentOfficialTotalCount: Int,
    val canonRetentionPercent: Double,
    val isCanonPreservationIntact: Boolean,
    val missingCanonWarning: String?,
    val cardDiffs: List<CardDiffEntry>,
    val costBrackets: List<CostBracketStat>,
    val typeDistributionDeltas: List<DistributionDelta>,
    val colorDistributionDeltas: List<DistributionDelta>,
    val outlierFlags: List<BalanceOutlierFlag>,
    val leftAverageCost: Double,
    val rightAverageCost: Double,
)

object SetComparisonEngine {

    const val SPLIT_OFF_MANIFEST_BLOB_NAME = "split_off_manifest.json"
    const val MSE_FIELD_CARD_ORIGIN = "card origin"
    const val MSE_FIELD_LINKED_CANON_ID = "linked canon card id"
    const val MSE_FIELD_CANON_SOURCE_ID = "canon source id"
    const val MSE_FIELD_FRAMEWORK_ID = "framework id"

    /**
     * Converts an active `.mse-set` archive into a normalized [ComparableSet].
     * Strictly preserves null/blank image semantics and reads canon origin fields losslessly.
     */
    fun fromMseSetArchive(
        archive: MseSetArchive,
        splitOffManifest: SplitOffSetManifest? = null,
    ): ComparableSet {
        val cards = archive.document.cards.mapIndexed { idx, card ->
            cardViewToComparable(card, idx, hasSplitOffManifest = splitOffManifest != null)
        }
        return ComparableSet(
            setId = splitOffManifest?.splitOffId ?: archive.id,
            setName = archive.document.setTitle,
            source = if (splitOffManifest != null) "split_off_mse_set" else "local_mse_set",
            gameId = archive.document.game,
            cards = cards,
            splitOffManifest = splitOffManifest,
        )
    }

    fun cardViewToComparable(
        card: MseCardView,
        index: Int,
        hasSplitOffManifest: Boolean = false,
    ): ComparableCardRecord {
        val rawCost = card.castingCost.ifBlank {
            card.field("level").ifBlank { card.field("grade").ifBlank { card.field("cost") } }
        }
        val atk = card.power.ifBlank { card.field("atk").ifBlank { card.field("hp") } }
        val def = card.toughness.ifBlank { card.field("def").ifBlank { card.field("shield") } }
        val explicitOrigin = card.field(MSE_FIELD_CARD_ORIGIN).trim()
        val resolvedOrigin = when {
            explicitOrigin.isNotEmpty() -> CardOrigin.fromWire(explicitOrigin).wireValue
            hasSplitOffManifest -> CardOrigin.ORIGINAL_OC_CARD.wireValue
            else -> CardOrigin.ORIGINAL_OC_CARD.wireValue
        }
        val sourceId = card.field(MSE_FIELD_CANON_SOURCE_ID).trim().ifBlank { "mse_card_${index + 1}" }
        val passcode = card.field("passcode").trim()
            .ifBlank { card.field("register 21 digit").trim() }
            .ifBlank { card.field("code string").trim() }
            .ifBlank { null }
        val linkedCanon = card.field(MSE_FIELD_LINKED_CANON_ID).trim().ifBlank { null }

        return ComparableCardRecord(
            id = sourceId,
            passcodeOr21Digit = passcode,
            name = card.name.ifBlank { "(Untitled Card #${index + 1})" },
            costOrLevel = rawCost,
            numericCost = parseNumericCost(rawCost),
            typeLine = card.fullTypeLine.ifBlank { card.field("card type") },
            superType = card.superType,
            subType = card.subType,
            powerOrAtk = atk,
            toughnessOrDef = def,
            rulesText = card.ruleText.ifBlank { card.field("text") },
            keywords = extractKeywords(card.ruleText.ifBlank { card.field("text") }),
            colorsOrAttributes = inferColorsOrAttributes(card),
            rarity = card.field("rarity"),
            cardOrigin = resolvedOrigin,
            linkedCanonCardId = linkedCanon,
            extraFields = card.allEditableFields.filterKeys { it != "image" },
            image = card.image, // strictly null unless user embedded art
        )
    }

    /**
     * Deterministically compares a custom/split-off set (left) against an official reference set (right).
     */
    fun compareSets(
        leftSet: ComparableSet,
        rightOfficialSet: ComparableSet,
    ): SetComparisonResult {
        val rightById = rightOfficialSet.cards.associateBy { it.id.trim().lowercase() }
        val rightByPasscode = rightOfficialSet.cards
            .filter { !it.passcodeOr21Digit.isNullOrBlank() }
            .associateBy { it.passcodeOr21Digit!!.trim().lowercase() }
        val rightByName = rightOfficialSet.cards
            .groupBy { normalizeName(it.name) }
            .mapValues { it.value.first() }

        val matchedRightIds = mutableSetOf<String>()
        val preservedCanonRightIds = mutableSetOf<String>()
        val diffs = mutableListOf<CardDiffEntry>()

        for (left in leftSet.cards) {
            val linkedId = left.linkedCanonCardId?.trim()?.lowercase()
            val directCanonMatch: ComparableCardRecord? = when {
                !linkedId.isNullOrBlank() -> rightById[linkedId] ?: rightByPasscode[linkedId]
                !left.passcodeOr21Digit.isNullOrBlank() && rightByPasscode.containsKey(left.passcodeOr21Digit.trim().lowercase()) ->
                    rightByPasscode[left.passcodeOr21Digit.trim().lowercase()]
                rightById.containsKey(left.id.trim().lowercase()) ->
                    rightById[left.id.trim().lowercase()]
                else -> rightByName[normalizeName(left.name)]
            }

            if (left.originEnum == CardOrigin.FAN_ALTERNATE_VARIANT) {
                val fieldDiffs = if (directCanonMatch != null) computeFieldDiffs(left, directCanonMatch) else emptyList()
                diffs += CardDiffEntry(
                    category = ComparisonCategory.FAN_ALTERNATE_VARIANT,
                    leftCard = left,
                    rightOfficialCard = directCanonMatch,
                    fieldDiffs = fieldDiffs,
                )
                continue
            }

            if (directCanonMatch != null) {
                matchedRightIds += directCanonMatch.id
                val fieldDiffs = computeFieldDiffs(left, directCanonMatch)
                val isPreservedCanon = left.originEnum == CardOrigin.CANON_ORIGINAL || fieldDiffs.isEmpty()
                if (isPreservedCanon && fieldDiffs.isEmpty()) {
                    preservedCanonRightIds += directCanonMatch.id
                    diffs += CardDiffEntry(
                        category = ComparisonCategory.CANON_PRESERVED,
                        leftCard = left,
                        rightOfficialCard = directCanonMatch,
                        fieldDiffs = emptyList(),
                    )
                } else {
                    if (left.originEnum == CardOrigin.CANON_ORIGINAL) {
                        preservedCanonRightIds += directCanonMatch.id
                    }
                    diffs += CardDiffEntry(
                        category = ComparisonCategory.MODIFIED_FROM_OFFICIAL,
                        leftCard = left,
                        rightOfficialCard = directCanonMatch,
                        fieldDiffs = fieldDiffs,
                    )
                }
            } else {
                diffs += CardDiffEntry(
                    category = ComparisonCategory.ORIGINAL_OC_CARD,
                    leftCard = left,
                    rightOfficialCard = null,
                    fieldDiffs = emptyList(),
                )
            }
        }

        // Any official cards missing from the left set
        for (official in rightOfficialSet.cards) {
            if (official.id !in matchedRightIds) {
                diffs += CardDiffEntry(
                    category = ComparisonCategory.MISSING_CANON_OFFICIAL,
                    leftCard = null,
                    rightOfficialCard = official,
                    fieldDiffs = emptyList(),
                )
            }
        }

        val officialTotal = rightOfficialSet.cards.size
        val preservedCount = preservedCanonRightIds.size
        val retentionPct = if (officialTotal == 0) 100.0 else (preservedCount.toDouble() / officialTotal.toDouble()) * 100.0
        val missingCount = (officialTotal - preservedCount).coerceAtLeast(0)
        val isSplitOff = leftSet.splitOffManifest != null
        val isIntact = missingCount == 0
        val missingWarning = if (missingCount > 0) {
            if (isSplitOff) {
                "Canon Lore Preservation Warning: $missingCount of $officialTotal parent official card(s) are missing from this Split-Off Set."
            } else {
                "$missingCount official card(s) from '${rightOfficialSet.setName}' are not present in '${leftSet.setName}'."
            }
        } else {
            null
        }

        // Cost brackets & average stat-to-cost comparison
        val bracketLabels = listOf("0", "1", "2", "3", "4", "5", "6+")
        val leftByBracket = Array(7) { mutableListOf<ComparableCardRecord>() }
        val rightByBracket = Array(7) { mutableListOf<ComparableCardRecord>() }

        leftSet.cards.forEach { card ->
            val cost = card.numericCost ?: parseNumericCost(card.costOrLevel)
            if (cost != null) leftByBracket[cost.coerceIn(0, 6)] += card
        }
        rightOfficialSet.cards.forEach { card ->
            val cost = card.numericCost ?: parseNumericCost(card.costOrLevel)
            if (cost != null) rightByBracket[cost.coerceIn(0, 6)] += card
        }

        val costBrackets = bracketLabels.mapIndexed { idx, label ->
            val leftStats = leftByBracket[idx].mapNotNull(::computeStatTotal)
            val rightStats = rightByBracket[idx].mapNotNull(::computeStatTotal)
            CostBracketStat(
                bracketLabel = label,
                leftCount = leftByBracket[idx].size,
                rightCount = rightByBracket[idx].size,
                leftAvgStatTotal = leftStats.takeIf { it.isNotEmpty() }?.average(),
                rightAvgStatTotal = rightStats.takeIf { it.isNotEmpty() }?.average(),
            )
        }

        val typeDeltas = computeDistributionDeltas(
            leftSet.cards.map { primaryTypeLabel(it.typeLine.ifBlank { it.superType }) },
            rightOfficialSet.cards.map { primaryTypeLabel(it.typeLine.ifBlank { it.superType }) },
        )
        val colorDeltas = computeDistributionDeltas(
            leftSet.cards.flatMap { it.colorsOrAttributes.ifEmpty { listOf("Colorless / Neutral") } },
            rightOfficialSet.cards.flatMap { it.colorsOrAttributes.ifEmpty { listOf("Colorless / Neutral") } },
        )

        // Automatic Outlier / Power-Creep detection comparing custom/OC/variant cards against official bracket benchmarks
        val outlierFlags = mutableListOf<BalanceOutlierFlag>()
        val officialAvgByBracket = costBrackets.map { it.rightAvgStatTotal }
        val officialRulesAvgLen = rightOfficialSet.cards.map { it.rulesText.length }.takeIf { it.isNotEmpty() }?.average() ?: 120.0

        for (card in leftSet.cards) {
            if (card.originEnum == CardOrigin.CANON_ORIGINAL) continue
            val cost = card.numericCost ?: parseNumericCost(card.costOrLevel)
            val statTotal = computeStatTotal(card)
            if (cost != null && statTotal != null) {
                val benchmark = officialAvgByBracket[cost.coerceIn(0, 6)]
                if (benchmark != null && benchmark > 0.0) {
                    val ratio = statTotal / benchmark
                    if (ratio >= 1.35) {
                        outlierFlags += BalanceOutlierFlag(
                            cardId = card.id,
                            cardName = card.name,
                            cardOrigin = card.originEnum,
                            reason = "Power-Creep Outlier: Stat total (${statTotal.roundToInt()}) at cost $cost exceeds official set avg (${benchmark.roundToInt()}) by ${((ratio - 1.0) * 100).roundToInt()}%.",
                            severity = if (ratio >= 1.6) "HIGH" else "MEDIUM",
                        )
                    } else if (ratio <= 0.55) {
                        outlierFlags += BalanceOutlierFlag(
                            cardId = card.id,
                            cardName = card.name,
                            cardOrigin = card.originEnum,
                            reason = "Under-Statted Outlier: Stat total (${statTotal.roundToInt()}) at cost $cost is ${((1.0 - ratio) * 100).roundToInt()}% below official set avg (${benchmark.roundToInt()}).",
                            severity = "MEDIUM",
                        )
                    }
                }
            }
            if (card.rulesText.length > (officialRulesAvgLen * 2.5).coerceAtLeast(360.0)) {
                outlierFlags += BalanceOutlierFlag(
                    cardId = card.id,
                    cardName = card.name,
                    cardOrigin = card.originEnum,
                    reason = "Text Complexity Flag: Rules text (${card.rulesText.length} chars) is significantly denser than official set average (${officialRulesAvgLen.roundToInt()} chars).",
                    severity = "MEDIUM",
                )
            }
        }

        val leftCosts = leftSet.cards.mapNotNull { it.numericCost ?: parseNumericCost(it.costOrLevel) }
        val rightCosts = rightOfficialSet.cards.mapNotNull { it.numericCost ?: parseNumericCost(it.costOrLevel) }

        return SetComparisonResult(
            leftSetName = leftSet.setName,
            rightOfficialSetName = rightOfficialSet.setName,
            isSplitOffComparison = isSplitOff,
            canonPreservedCount = preservedCount,
            parentOfficialTotalCount = officialTotal,
            canonRetentionPercent = retentionPct,
            isCanonPreservationIntact = isIntact,
            missingCanonWarning = missingWarning,
            cardDiffs = diffs,
            costBrackets = costBrackets,
            typeDistributionDeltas = typeDeltas,
            colorDistributionDeltas = colorDeltas,
            outlierFlags = outlierFlags,
            leftAverageCost = leftCosts.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            rightAverageCost = rightCosts.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
        )
    }

    /**
     * Builds a metadata-only MSE field map from an official card baseline.
     * STRICT INVARIANT: `image` is NEVER included or auto-populated (remains strictly null/blank).
     */
    fun createReferenceBaselineFields(
        officialCard: ComparableCardRecord,
        origin: CardOrigin = CardOrigin.FAN_ALTERNATE_VARIANT,
    ): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        fields["name"] = if (origin == CardOrigin.FAN_ALTERNATE_VARIANT) {
            "${officialCard.name} (Alternate)"
        } else {
            officialCard.name
        }
        fields["casting cost"] = officialCard.costOrLevel
        val splitType = splitTypeLine(officialCard.typeLine, officialCard.superType, officialCard.subType)
        fields["super type"] = splitType.first
        fields["sub type"] = splitType.second
        fields["rule text"] = officialCard.rulesText
        if (officialCard.powerOrAtk.isNotBlank()) fields["power"] = officialCard.powerOrAtk
        if (officialCard.toughnessOrDef.isNotBlank()) fields["toughness"] = officialCard.toughnessOrDef
        if (officialCard.rarity.isNotBlank()) fields["rarity"] = officialCard.rarity
        if (!officialCard.passcodeOr21Digit.isNullOrBlank()) {
            fields["passcode"] = officialCard.passcodeOr21Digit
        }
        fields[MSE_FIELD_CARD_ORIGIN] = origin.wireValue
        if (origin == CardOrigin.CANON_ORIGINAL) {
            fields[MSE_FIELD_CANON_SOURCE_ID] = officialCard.id
        } else if (origin == CardOrigin.FAN_ALTERNATE_VARIANT) {
            fields[MSE_FIELD_LINKED_CANON_ID] = officialCard.id
        }
        // Copy non-image extra metadata safely (never copy "image")
        officialCard.extraFields.forEach { (k, v) ->
            if (!k.equals("image", ignoreCase = true) && !fields.containsKey(k) && v.isNotBlank()) {
                fields[k] = v
            }
        }
        // Strictly ensure "image" key is NEVER present
        fields.remove("image")
        return fields
    }

    /**
     * Parses real Card-Forge `cardsfolder` `.txt` script files into a [ComparableCardRecord].
     */
    fun parseForgeCardText(rawText: String, fileName: String = "forge_card.txt"): ComparableCardRecord? {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val props = mutableMapOf<String, String>()
        val svars = mutableListOf<String>()
        val keywords = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) continue
            val key = trimmed.substring(0, colonIdx).trim()
            val value = trimmed.substring(colonIdx + 1).trim()
            when (key) {
                "SVar" -> svars += value
                "K" -> keywords += value
                "Oracle" -> props["Oracle"] = value.replace("\\n", "\n")
                else -> if (!props.containsKey(key)) props[key] = value
            }
        }

        val name = props["Name"]?.takeIf { it.isNotBlank() } ?: return null
        val rawCost = props["ManaCost"].orEmpty().replace(" ", "")
        val types = props["Types"].orEmpty()
        val pt = props["PT"].orEmpty()
        val ptParts = pt.split("/")
        val power = ptParts.getOrNull(0)?.trim().orEmpty()
        val toughness = ptParts.getOrNull(1)?.trim().orEmpty()
        val oracle = props["Oracle"].orEmpty()
        val splitTypes = splitTypeLine(types, "", "")
        val extra = linkedMapOf<String, String>()
        if (svars.isNotEmpty()) extra["forge_svars"] = svars.joinToString(" | ")
        props["Loyalty"]?.let { extra["loyalty"] = it }

        return ComparableCardRecord(
            id = "forge_${normalizeName(name).ifBlank { fileName }}",
            passcodeOr21Digit = null,
            name = name,
            costOrLevel = rawCost,
            numericCost = parseNumericCost(rawCost),
            typeLine = types,
            superType = splitTypes.first,
            subType = splitTypes.second,
            powerOrAtk = power,
            toughnessOrDef = toughness,
            rulesText = oracle,
            keywords = keywords,
            colorsOrAttributes = inferColorsFromManaCost(rawCost),
            cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
            extraFields = extra,
            image = null,
        )
    }

    /**
     * Auto-suggests field key mappings between source card keys and target framework/style field keys.
     */
    fun suggestDynamicFieldMapping(
        sourceKeys: Set<String>,
        targetKeys: List<String>,
    ): Map<String, String> {
        val mapping = linkedMapOf<String, String>()
        val targetLower = targetKeys.associateBy { it.lowercase() }
        val aliases = mapOf(
            "name" to listOf("name", "card name", "title"),
            "casting cost" to listOf("casting cost", "cost", "mana", "level", "grade", "ink", "energy", "play cost", "pitch"),
            "super type" to listOf("super type", "card type", "type", "category", "class"),
            "sub type" to listOf("sub type", "subtype", "race", "trait", "traits", "archetype"),
            "rule text" to listOf("rule text", "effect", "text", "oracle", "ability", "description"),
            "power" to listOf("power", "atk", "attack", "strength", "dp", "hp"),
            "toughness" to listOf("toughness", "def", "defense", "willpower", "health", "shield", "retreat"),
            "flavor text" to listOf("flavor text", "flavor", "lore"),
        )

        for (src in sourceKeys) {
            if (src.equals("image", ignoreCase = true)) continue
            val low = src.lowercase()
            if (targetLower.containsKey(low)) {
                mapping[src] = targetLower[low]!!
                continue
            }
            val matchedTarget = aliases.entries.firstOrNull { (_, group) -> low in group }
                ?.value
                ?.firstNotNullOfOrNull { alias -> targetLower[alias] }
            if (matchedTarget != null) {
                mapping[src] = matchedTarget
            }
        }
        return mapping
    }

    private fun computeFieldDiffs(
        left: ComparableCardRecord,
        right: ComparableCardRecord,
    ): List<CardFieldDiff> {
        val diffs = mutableListOf<CardFieldDiff>()
        fun check(label: String, l: String, r: String) {
            val nl = l.trim().replace(Regex("\\s+"), " ")
            val nr = r.trim().replace(Regex("\\s+"), " ")
            if (!nl.equals(nr, ignoreCase = true)) {
                diffs += CardFieldDiff(label, l.trim(), r.trim())
            }
        }
        check("Name", left.name, right.name)
        check("Cost / Level", left.costOrLevel, right.costOrLevel)
        check("Type Line", left.typeLine, right.typeLine)
        val leftStat = formatStatPair(left.powerOrAtk, left.toughnessOrDef)
        val rightStat = formatStatPair(right.powerOrAtk, right.toughnessOrDef)
        check("Stats (P/T or ATK/DEF)", leftStat, rightStat)
        check("Rules / Oracle Text", left.rulesText, right.rulesText)
        return diffs
    }

    private fun formatStatPair(a: String, b: String): String = when {
        a.isNotBlank() && b.isNotBlank() -> "$a/$b"
        a.isNotBlank() -> a
        b.isNotBlank() -> b
        else -> ""
    }

    private fun computeDistributionDeltas(
        leftLabels: List<String>,
        rightLabels: List<String>,
    ): List<DistributionDelta> {
        val leftCounts = leftLabels.groupingBy { it }.eachCount()
        val rightCounts = rightLabels.groupingBy { it }.eachCount()
        val allKeys = (leftCounts.keys + rightCounts.keys).sorted()
        return allKeys.map { key ->
            val l = leftCounts[key] ?: 0
            val r = rightCounts[key] ?: 0
            DistributionDelta(label = key, leftCount = l, rightCount = r, delta = l - r)
        }.sortedByDescending { abs(it.delta) + it.leftCount + it.rightCount }
    }

    fun parseNumericCost(raw: String): Int? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        cleaned.toIntOrNull()?.let { return it.coerceAtLeast(0) }
        if (cleaned.contains("{")) {
            var total = 0
            var foundAny = false
            Regex("\\{([^}]+)\\}").findAll(cleaned).forEach { m ->
                val token = m.groupValues[1].trim()
                val num = token.toIntOrNull()
                if (num != null) {
                    total += num
                    foundAny = true
                } else if (!token.equals("X", ignoreCase = true) && token.isNotEmpty()) {
                    total += 1
                    foundAny = true
                }
            }
            return if (foundAny) total else null
        }
        var sum = 0
        var matched = false
        Regex("\\d+").findAll(cleaned).forEach {
            sum += it.value.toIntOrNull() ?: 0
            matched = true
        }
        val pipCount = Regex("[WUBRGCS]").findAll(cleaned.uppercase()).count()
        if (pipCount > 0) {
            sum += pipCount
            matched = true
        }
        return if (matched) sum else null
    }

    private fun computeStatTotal(card: ComparableCardRecord): Double? {
        val p = card.powerOrAtk.trim().toDoubleOrNull()
        val t = card.toughnessOrDef.trim().toDoubleOrNull()
        return when {
            p != null && t != null -> p + t
            p != null -> p
            t != null -> t
            else -> null
        }
    }

    private fun splitTypeLine(typeLine: String, superType: String, subType: String): Pair<String, String> {
        if (superType.isNotBlank() || subType.isNotBlank()) return superType to subType
        val separators = listOf(" — ", " - ", " / ")
        for (sep in separators) {
            val idx = typeLine.indexOf(sep)
            if (idx > 0) {
                return typeLine.substring(0, idx).trim() to typeLine.substring(idx + sep.length).trim()
            }
        }
        return typeLine.trim() to ""
    }

    private fun primaryTypeLabel(typeLine: String): String {
        val low = typeLine.trim().lowercase()
        return when {
            low.isEmpty() -> "Untyped"
            low.contains("creature") -> "Creature"
            low.contains("instant") -> "Instant"
            low.contains("sorcery") -> "Sorcery"
            low.contains("artifact") -> "Artifact"
            low.contains("enchantment") -> "Enchantment"
            low.contains("planeswalker") -> "Planeswalker"
            low.contains("land") -> "Land"
            low.contains("monster") -> "Monster"
            low.contains("spell") -> "Spell"
            low.contains("trap") -> "Trap"
            low.contains("trainer") -> "Trainer"
            low.contains("pokémon") || low.contains("pokemon") -> "Pokémon"
            low.contains("energy") -> "Energy"
            else -> typeLine.trim().split(' ', '—', '-').firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Other"
        }
    }

    private fun inferColorsOrAttributes(card: MseCardView): List<String> {
        val attr = card.field("attribute").trim()
        if (attr.isNotEmpty()) return listOf(attr.uppercase())
        val explicit = card.field("card color").trim()
        if (explicit.isNotEmpty()) {
            return explicit.split(",", " ").mapNotNull { tok ->
                val t = tok.trim().lowercase()
                when {
                    t.contains("white") -> "White"
                    t.contains("blue") -> "Blue"
                    t.contains("black") -> "Black"
                    t.contains("red") -> "Red"
                    t.contains("green") -> "Green"
                    t.contains("artifact") -> "Artifact"
                    t.contains("land") -> "Land"
                    else -> null
                }
            }.distinct()
        }
        return inferColorsFromManaCost(card.castingCost)
    }

    private fun inferColorsFromManaCost(cost: String): List<String> {
        val up = cost.uppercase()
        return buildList {
            if (up.contains('W')) add("White")
            if (up.contains('U')) add("Blue")
            if (up.contains('B')) add("Black")
            if (up.contains('R')) add("Red")
            if (up.contains('G')) add("Green")
        }
    }

    private fun extractKeywords(rulesText: String): List<String> {
        val known = listOf(
            "Flying", "Trample", "Haste", "Vigilance", "Lifelink", "Deathtouch",
            "First Strike", "Double Strike", "Menace", "Reach", "Hexproof", "Ward",
            "Indestructible", "Flash", "Defender", "Quick Effect", "Piercing", "Rush",
        )
        return known.filter { rulesText.contains(it, ignoreCase = true) }
    }

    fun normalizeName(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
