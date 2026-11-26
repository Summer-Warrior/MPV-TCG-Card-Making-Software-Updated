package com.example

import com.example.data.mse.MseSetArchive
import com.example.data.mse.MseSetDocument
import com.example.data.mse.MseSetParser
import com.example.domain.comparison.CardOrigin
import com.example.domain.comparison.ComparableCardRecord
import com.example.domain.comparison.ComparableSet
import com.example.domain.comparison.ComparisonCategory
import com.example.domain.comparison.SetComparisonEngine
import com.example.domain.comparison.SplitOffKind
import com.example.domain.comparison.SplitOffSetManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SetComparisonAndSplitOffUnitTest {

    @Test
    fun compareSets_preserves100PercentCanonAndDetectsVariantsAndOcs() {
        val officialCards = listOf(
            ComparableCardRecord(
                id = "canon_1",
                name = "Sunblade Knight",
                costOrLevel = "{1}{W}",
                numericCost = 2,
                typeLine = "Creature — Human Knight",
                powerOrAtk = "2",
                toughnessOrDef = "2",
                rulesText = "First Strike",
                cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
            ),
            ComparableCardRecord(
                id = "canon_2",
                name = "Arcane Bolt",
                costOrLevel = "{R}",
                numericCost = 1,
                typeLine = "Instant",
                rulesText = "Deal 3 damage to any target.",
                cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue,
            ),
        )
        val officialSet = ComparableSet(
            setId = "core_2026",
            setName = "Core 2026",
            source = "scryfall",
            gameId = "magic",
            cards = officialCards,
        )

        val splitOffManifest = SplitOffSetManifest(
            splitOffId = "au_eclipse",
            splitOffTitle = "Eclipse AU",
            splitOffKind = SplitOffKind.FAN_FICTION_AU.wireValue,
            parentOfficialSetId = "core_2026",
            parentOfficialSetName = "Core 2026",
            parentOfficialSource = "scryfall",
            parentGameId = "magic",
            preserveAllOriginalOfficialCards = true,
            preservedCanonCardCount = 2,
        )

        val customCards = officialCards + listOf(
            ComparableCardRecord(
                id = "variant_1",
                name = "Sunblade Knight (Shadow AU)",
                costOrLevel = "{1}{W}{B}",
                numericCost = 3,
                typeLine = "Creature — Shadow Knight",
                powerOrAtk = "3",
                toughnessOrDef = "2",
                rulesText = "First Strike, Lifelink",
                cardOrigin = CardOrigin.FAN_ALTERNATE_VARIANT.wireValue,
                linkedCanonCardId = "canon_1",
            ),
            ComparableCardRecord(
                id = "oc_1",
                name = "Vaelen, Eclipse Herald",
                costOrLevel = "{2}{B}",
                numericCost = 3,
                typeLine = "Legendary Creature — Avatar",
                powerOrAtk = "9",
                toughnessOrDef = "9",
                rulesText = "Flying, Haste",
                cardOrigin = CardOrigin.ORIGINAL_OC_CARD.wireValue,
            ),
        )

        val leftSet = ComparableSet(
            setId = "au_eclipse",
            setName = "Eclipse AU",
            source = "split_off_mse_set",
            gameId = "magic",
            cards = customCards,
            splitOffManifest = splitOffManifest,
        )

        val result = SetComparisonEngine.compareSets(leftSet, officialSet)

        assertTrue(result.isSplitOffComparison)
        assertEquals(2, result.canonPreservedCount)
        assertEquals(2, result.parentOfficialTotalCount)
        assertEquals(100.0, result.canonRetentionPercent, 0.01)
        assertTrue(result.isCanonPreservationIntact)
        assertNull(result.missingCanonWarning)
        assertEquals(2, result.cardDiffs.count { it.category == ComparisonCategory.CANON_PRESERVED })
        assertEquals(1, result.cardDiffs.count { it.category == ComparisonCategory.FAN_ALTERNATE_VARIANT })
        assertEquals(1, result.cardDiffs.count { it.category == ComparisonCategory.ORIGINAL_OC_CARD })
    }

    @Test
    fun compareSets_flagsMissingCanonWarningWhenOfficialCardDropped() {
        val officialSet = ComparableSet(
            setId = "core_2026",
            setName = "Core 2026",
            source = "scryfall",
            gameId = "magic",
            cards = listOf(
                ComparableCardRecord(id = "c1", name = "Card One", cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue),
                ComparableCardRecord(id = "c2", name = "Card Two", cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue),
            ),
        )
        val splitOff = ComparableSet(
            setId = "broken_au",
            setName = "Broken AU",
            source = "split_off_mse_set",
            gameId = "magic",
            cards = listOf(
                ComparableCardRecord(id = "c1", name = "Card One", cardOrigin = CardOrigin.CANON_ORIGINAL.wireValue),
            ),
            splitOffManifest = SplitOffSetManifest(
                splitOffId = "broken_au",
                splitOffTitle = "Broken AU",
                parentOfficialSetId = "core_2026",
                parentOfficialSetName = "Core 2026",
                parentOfficialSource = "scryfall",
                parentGameId = "magic",
                preserveAllOriginalOfficialCards = true,
            ),
        )

        val result = SetComparisonEngine.compareSets(splitOff, officialSet)

        assertFalse(result.isCanonPreservationIntact)
        assertNotNull(result.missingCanonWarning)
        assertEquals(1, result.canonPreservedCount)
        assertEquals(50.0, result.canonRetentionPercent, 0.01)
        assertEquals(1, result.cardDiffs.count { it.category == ComparisonCategory.MISSING_CANON_OFFICIAL })
    }

    @Test
    fun createReferenceBaselineFields_strictlyOmitsImageField() {
        val official = ComparableCardRecord(
            id = "scry_99",
            name = "Chrono Dragon",
            costOrLevel = "{4}{U}{R}",
            typeLine = "Creature — Dragon",
            powerOrAtk = "5",
            toughnessOrDef = "5",
            rulesText = "Flying",
            extraFields = mapOf("image" to "forbidden.png", "artist" to "Jane Doe"),
            image = "should_never_copy.png",
        )

        val variantFields = SetComparisonEngine.createReferenceBaselineFields(
            officialCard = official,
            origin = CardOrigin.FAN_ALTERNATE_VARIANT,
        )

        assertFalse("image key must never be populated", variantFields.containsKey("image"))
        assertEquals("Chrono Dragon (Alternate)", variantFields["name"])
        assertEquals(CardOrigin.FAN_ALTERNATE_VARIANT.wireValue, variantFields[SetComparisonEngine.MSE_FIELD_CARD_ORIGIN])
        assertEquals("scry_99", variantFields[SetComparisonEngine.MSE_FIELD_LINKED_CANON_ID])
        assertEquals("Jane Doe", variantFields["artist"])
    }

    @Test
    fun parseForgeCardText_parsesStandardForgeScript() {
        val forgeScript = """
            Name:Lightning Seraph
            ManaCost:2 R W
            Types:Creature Angel
            PT:4/3
            K:Flying
            K:Haste
            Oracle:Flying, haste\nWhen Lightning Seraph enters, deal 2 damage to any target.
            SVar:Picture:https://example.invalid/never_auto_fetched.jpg
        """.trimIndent()

        val parsed = SetComparisonEngine.parseForgeCardText(forgeScript, "lightning_seraph.txt")

        assertNotNull(parsed)
        assertEquals("Lightning Seraph", parsed!!.name)
        assertEquals("2RW", parsed.costOrLevel)
        assertEquals(4, parsed.numericCost)
        assertEquals("4", parsed.powerOrAtk)
        assertEquals("3", parsed.toughnessOrDef)
        assertTrue(parsed.keywords.contains("Flying"))
        assertTrue(parsed.keywords.contains("Haste"))
        assertNull("Default card art must remain strictly null", parsed.image)
    }

    @Test
    fun suggestDynamicFieldMapping_mapsCrossGameKeysAndNeverMapsImage() {
        val mapping = SetComparisonEngine.suggestDynamicFieldMapping(
            sourceKeys = setOf("name", "level", "atk", "def", "effect", "image"),
            targetKeys = listOf("name", "casting cost", "power", "toughness", "rule text", "image"),
        )

        assertEquals("name", mapping["name"])
        assertEquals("casting cost", mapping["level"])
        assertEquals("power", mapping["atk"])
        assertEquals("toughness", mapping["def"])
        assertEquals("rule text", mapping["effect"])
        assertFalse("image must never be remapped", mapping.containsKey("image"))
    }

    @Test
    fun mseSetArchive_losslessRoundTripWithSplitOffManifestAndNullImageGuard() {
        val blank = MseSetParser.createBlankArchive(
            game = "magic",
            stylesheet = "m15-altered",
            title = "Chrono Split-Off",
        )
        val docWithCanonAndOc = blank.document
            .withDeletedCard(0)
            .withAppendedCardFromFields(
                fields = mapOf(
                    "name" to "Canon Anchor",
                    "atk" to "2500",
                    "image" to "forbidden_art.png",
                    SetComparisonEngine.MSE_FIELD_CARD_ORIGIN to CardOrigin.CANON_ORIGINAL.wireValue,
                ),
                timestampIso = "2026-11-26 00:00:01",
            )
            .withAppendedCardFromFields(
                fields = mapOf(
                    "name" to "OC Challenger",
                    "atk" to "3000",
                    SetComparisonEngine.MSE_FIELD_CARD_ORIGIN to CardOrigin.ORIGINAL_OC_CARD.wireValue,
                ),
                timestampIso = "2026-11-26 00:00:02",
            )
            .withRemappedCardFields(
                fieldMapping = mapOf("atk" to "power", "image" to "power"),
                skipCanonOriginals = true,
            )

        val cards = docWithCanonAndOc.cards
        assertEquals(2, cards.size)
        assertNull("image must be stripped on append", cards[0].image)
        assertEquals("Canon original must not be mutated during remap", "", cards[0].field("power"))
        assertEquals("OC card should receive remapped power field", "3000", cards[1].field("power"))

        val manifestBytes = """{"splitOffId":"chrono_au","preserveAllOriginalOfficialCards":true}""".toByteArray()
        val archive = blank.copy(
            document = docWithCanonAndOc,
            embeddedBlobs = mapOf(SetComparisonEngine.SPLIT_OFF_MANIFEST_BLOB_NAME to manifestBytes),
        )

        val out = ByteArrayOutputStream()
        MseSetParser.writeArchive(archive, out)
        val roundTripped = MseSetParser.readArchive(
            inputStream = ByteArrayInputStream(out.toByteArray()),
            displayName = "chrono_au.mse-set",
        )

        assertEquals(2, roundTripped.document.cards.size)
        assertTrue(roundTripped.embeddedBlobs.containsKey(SetComparisonEngine.SPLIT_OFF_MANIFEST_BLOB_NAME))
        assertEquals(
            String(manifestBytes),
            String(roundTripped.embeddedBlobs.getValue(SetComparisonEngine.SPLIT_OFF_MANIFEST_BLOB_NAME)),
        )
    }
}
