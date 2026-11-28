package com.example

import com.example.data.mse.MseSetParser
import com.example.data.mse.MseStyleParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MseParsersUnitTest {

    @Test
    fun blankArchiveStrictlyHasNullCardArt() {
        val blank = MseSetParser.createBlankArchive()
        assertEquals(1, blank.document.cards.size)
        assertNull("New card art MUST initialize strictly to null", blank.document.cards.first().image)
        assertTrue(blank.embeddedBlobs.isEmpty())

        val appendedDoc = blank.document.withAppendedBlankCard("2026-11-22 12:00:00")
        assertEquals(2, appendedDoc.cards.size)
        assertNull("Appended card art MUST initialize strictly to null", appendedDoc.cards[1].image)
    }

    @Test
    fun losslessSetTextRoundTripPreservesUnknownFieldsAndMultilineRules() {
        val original = buildString {
            append("mse version: 2.0.0\n")
            append("game: magic\n")
            append("stylesheet: m15-altered\n")
            append("custom unknown header: keep-me-exact\n")
            append("set info:\n")
            append("\ttitle: Foundry Test\n")
            append("\tcustom meta tag: alpha-9\n")
            append("card:\n")
            append("\thas styling: false\n")
            append("\tname: Clockwork Draftsman\n")
            append("\trule text:\n")
            append("\t\tFlying\n")
            append("\t\t{T}: Draw a card.\n")
            append("\tunknown card key: untouched_value\n")
        }

        val parsed = MseSetParser.parseSetText(original)
        assertEquals("Foundry Test", parsed.setTitle)
        assertEquals("Clockwork Draftsman", parsed.cards.first().name)
        assertEquals("Flying\n{T}: Draw a card.", parsed.cards.first().ruleText)
        assertNull(parsed.cards.first().image)

        val serialized = MseSetParser.serializeSetText(parsed)
        assertEquals(original, serialized)
    }

    @Test
    fun losslessZipArchivePreservesEmbeddedBlobsUnmodified() {
        val sampleBlob = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val blank = MseSetParser.createBlankArchive()
        val withBlob = blank.copy(
            document = blank.document.withCardFieldUpdated(0, "image", "image1"),
            embeddedBlobs = mapOf("image1" to sampleBlob, "extra_meta.dat" to byteArrayOf(9, 8, 7)),
        )

        val out = ByteArrayOutputStream()
        MseSetParser.writeArchive(withBlob, out)

        val restored = MseSetParser.readArchive(
            inputStream = ByteArrayInputStream(out.toByteArray()),
            displayName = "roundtrip.mse-set",
        )
        assertEquals("image1", restored.document.cards.first().image)
        assertArrayEquals(sampleBlob, restored.embeddedBlobs["image1"])
        assertArrayEquals(byteArrayOf(9, 8, 7), restored.embeddedBlobs["extra_meta.dat"])
    }

    @Test
    fun mseStyleParserExtractsDimensionsHitboxesAndReferencedFonts() {
        val styleText = """
            mse version: 2.0.0
            game: yugioh
            short name: Series 10
            full name: Yu-Gi-Oh! Series 10
            card width: 421
            card height: 614
            card dpi: 150
            card style:
            	name:
            		left: 32
            		top: 28
            		right: 345
            		bottom: 66
            		z index: 2
            		font:
            			name: Matrix II
            	image:
            		left: 49
            		top: 112
            		width: 322
            		height: 322
            		z index: 1
            	rule text:
            		left: 34
            		top: 460
            		width: 353
            		height: 110
            		z index: 2
            		font:
            			name: ITC Stone Serif
        """.trimIndent()

        val spec = MseStyleParser.parseStyleText(styleText, "yugioh-series10.mse-style")
        assertEquals(421, spec.cardWidthPx)
        assertEquals(614, spec.cardHeightPx)
        assertEquals(150, spec.cardDpi)
        assertEquals(3, spec.hitboxes.size)

        val nameBox = spec.hitboxes.first { it.field == "name" }
        assertEquals(313f, nameBox.width, 0.01f)
        assertEquals(38f, nameBox.height, 0.01f)
        assertTrue(spec.referencedFonts.contains("Matrix II"))
        assertTrue(spec.referencedFonts.contains("ITC Stone Serif"))
    }

    @Test
    fun setInfoKeywordsAndCardMutationsPreserveLosslessFormatAndNullArt() {
        val blank = MseSetParser.createBlankArchive(
            game = "magic",
            stylesheet = "m15-altered",
            title = "Clockwork Horizon",
        )
        val withInfo = blank.document.withSetInfoUpdated(
            blank.document.setInfo.copy(
                artist = "Studio Collective",
                setCode = "CWH",
            )
        )
        assertEquals("Clockwork Horizon", withInfo.setInfo.title)
        assertEquals("Studio Collective", withInfo.setInfo.artist)
        assertEquals("CWH", withInfo.setInfo.setCode)

        val withKw = withInfo.withAddedKeyword(
            keyword = "Chronocast",
            match = "Chronocast <cost>",
            reminder = "You may cast this spell from exile for its chronocast cost.",
            mode = "custom",
        )
        assertEquals(1, withKw.keywords.size)
        assertEquals("Chronocast", withKw.keywords.first().keyword)

        val withCard = withKw.withCardFieldUpdated(0, "name", "Aether Astrolabe")
        val duplicated = withCard.withDuplicatedCard(0, "2026-11-22 14:00:00")
        assertEquals(2, duplicated.cards.size)
        assertEquals("Aether Astrolabe", duplicated.cards[1].name)
        assertNull("Duplicated blank-art card MUST keep null image", duplicated.cards[1].image)

        val afterDelete = duplicated.withDeletedCard(1)
        assertEquals(1, afterDelete.cards.size)

        val afterKwDelete = afterDelete.withDeletedKeyword(0)
        assertTrue(afterKwDelete.keywords.isEmpty())

        val roundTrip = MseSetParser.parseSetText(MseSetParser.serializeSetText(afterKwDelete))
        assertEquals("Clockwork Horizon", roundTrip.setInfo.title)
        assertEquals("CWH", roundTrip.setInfo.setCode)
        assertEquals("Aether Astrolabe", roundTrip.cards.first().name)
        assertNull(roundTrip.cards.first().image)
    }

    @Test
    fun parseGameTextExtractsExactMseGameMetadata() {
        val gameText = """
            mse version: 2.0.0
            short name: Magic
            full name: Magic the Gathering
            icon: card-back.png
            position hint: 01
        """.trimIndent()

        val parsed = MseStyleParser.parseGameText(gameText, "magic.mse-game")
        assertEquals("magic", parsed.gameId)
        assertEquals("Magic", parsed.shortName)
        assertEquals("Magic the Gathering", parsed.fullName)
        assertEquals("card-back.png", parsed.iconFile)
        assertEquals(1, parsed.positionHint)
    }

    @Test
    fun parseStyleTextExtractsIconDependsOnChoiceImagesAndValidatesGeometry() {
        val styleText = """
            mse version: 2.0.0
            game: magic
            short name: M15 Mainframe
            full name: M15 Mainframe
            icon: card-sample.png
            position hint: 001
            depends on:
            	package: magic.mse-game
            	version: 2020-04-25
            depends on:
            	package: magic-blends.mse-include
            	version: 2015-05-23
            card width: 375
            card height: 523
            card dpi: 150
            card style:
            	card color:
            		left: 0
            		top: 0
            		width: 375
            		height: 523
            		z index: 0
            		default: wcard.jpg
            		choice images:
            			white: wcard.jpg
            			blue: ucard.jpg
            			red: rcard.jpg
            	name:
            		left: 30
            		top: 26
            		width: 315
            		height: 28
            		z index: 2
            		font:
            			name: Beleren Bold
            			size: 16
            			color: rgb(0,0,0)
            	image:
            		left: 29
            		top: 60
            		width: 316
            		height: 231
            		z index: 1
            	rule text:
            		left: 29
            		top: 327
            		width: 317
            		height: 154
            		z index: 2
        """.trimIndent()

        val spec = MseStyleParser.parseStyleText(styleText, "magic-m15-altered.mse-style")
        assertEquals("card-sample.png", spec.iconFile)
        assertEquals(1, spec.positionHint)
        assertTrue(spec.dependsOn.contains("magic-blends.mse-include"))
        assertEquals("wcard.jpg", spec.defaultFrameImage)
        assertEquals("ucard.jpg", spec.baseFrameChoices["blue"])
        assertEquals("rcard.jpg", spec.baseFrameChoices["red"])
        assertTrue("Valid style hitboxes should pass composite geometry validation", spec.hasValidCompositeGeometry())

        val collapsedStyle = """
            game: custom
            card width: 375
            card height: 523
            card style:
            	name:
            		left: 0
            		top: 0
            		width: 100
            		height: 20
            	image:
            		left: 0
            		top: 0
            		width: 100
            		height: 100
            	rule text:
            		left: 0
            		top: 0
            		width: 100
            		height: 80
        """.trimIndent()
        val collapsedSpec = MseStyleParser.parseStyleText(collapsedStyle, "collapsed.mse-style")
        assertTrue(
            "Origin-collapsed hitboxes must NOT pass composite geometry validation",
            !collapsedSpec.hasValidCompositeGeometry(),
        )
    }

    @Test
    fun parseManaValueHandlesBraceAndCompactManaCosts() {
        assertEquals(4, com.example.ui.workspace.parseManaValue("{2}{U}{U}"))
        assertEquals(5, com.example.ui.workspace.parseManaValue("3RG"))
        assertEquals(1, com.example.ui.workspace.parseManaValue("{X}{W}"))
        assertEquals(0, com.example.ui.workspace.parseManaValue(""))
        assertEquals(2, com.example.ui.workspace.parseManaValue("WW"))
    }

    @Test
    fun blacklistRejectsTinyOverlayIconsAndAllowsRealBaseCardFrames() {
        val rejected = listOf(
            "20slice.png",
            "flashdot.png",
            "loyalty.png",
            "loyaltydown.png",
            "loyaltynaught.png",
            "pt_box.png",
            "holo_stamp.png",
            "nyx_crown.png",
            "watermark_guild.png",
            "card-sample.png",
            "card-back.png",
            "snow_overlay.png",
        )
        for (name in rejected) {
            assertTrue(
                "Expected non-card overlay '$name' to be blacklisted",
                MseStyleParser.isBlacklistedNonCardAsset(name),
            )
        }

        val allowed = listOf(
            "ccard.jpg",
            "wcard.jpg",
            "ucard.jpg",
            "bcard.jpg",
            "rcard.jpg",
            "gcard.jpg",
            "mcard.jpg",
            "acard.jpg",
            "lcard.jpg",
            "frames/monster_normal.png",
            "bg/background_gold.png",
        )
        for (name in allowed) {
            assertTrue(
                "Expected real base frame '$name' to NOT be blacklisted",
                !MseStyleParser.isBlacklistedNonCardAsset(name),
            )
        }
    }

    @Test
    fun portraitCardDimensionGateRejectsIconsAndAcceptsFullCardFrames() {
        // Real MSE card sizes
        assertTrue(com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(375, 523))
        assertTrue(com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(744, 1039))
        assertTrue(com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(421, 614))

        // Tiny icons / square overlays / wide banners must fail
        assertTrue(!com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(20, 20))
        assertTrue(!com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(120, 120))
        assertTrue(!com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(375, 375))
        assertTrue(!com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(523, 375))
        assertTrue(!com.example.data.mse.MseTemplateCatalogRepository.isValidPortraitCardDimensions(200, 300))
    }

    @Test
    fun styleParserMarksHiddenFieldsAndOverlayFilterExcludesAliasBorderColorAndLoyalty() {
        val styleText = """
            mse version: 2.0.0
            game: magic
            card width: 375
            card height: 523
            card dpi: 150
            card style:
            	name:
            		left: 30
            		top: 26
            		width: 315
            		height: 28
            		z index: 2
            	alias:
            		left: 30
            		top: 56
            		width: 315
            		height: 20
            		visible: { card.alias != "" }
            	border color:
            		left: 0
            		top: 0
            		width: 375
            		height: 523
            		visible: false
            	card color 2:
            		left: 0
            		top: 0
            		width: 375
            		height: 523
            	loyalty:
            		left: 310
            		top: 460
            		width: 40
            		height: 30
            	image:
            		left: 29
            		top: 60
            		width: 316
            		height: 231
            	type:
            		left: 32
            		top: 296
            		width: 311
            		height: 28
            	rule text:
            		left: 29
            		top: 327
            		width: 317
            		height: 154
        """.trimIndent()

        val spec = MseStyleParser.parseStyleText(styleText, "magic-m15-mainframe.mse-style")
        val aliasBox = spec.hitboxes.first { it.field == "alias" }
        val borderBox = spec.hitboxes.first { it.field == "border color" }
        val nameBox = spec.hitboxes.first { it.field == "name" }

        assertTrue("Scripted visible: expression must mark field hidden/conditional", aliasBox.isHiddenOrConditional)
        assertTrue("visible: false must mark field hidden/conditional", borderBox.isHiddenOrConditional)
        assertTrue("Core visible field 'name' must remain visible", !nameBox.isHiddenOrConditional)

        val visibleOverlays = com.example.ui.workspace.prepareOverlayTextHitboxes(spec.hitboxes, "magic")
        val visibleNames = visibleOverlays.map { it.field }.toSet()
        assertTrue(visibleNames.contains("name"))
        assertTrue(visibleNames.contains("type"))
        assertTrue(visibleNames.contains("rule text"))
        assertTrue(!visibleNames.contains("alias"))
        assertTrue(!visibleNames.contains("border color"))
        assertTrue(!visibleNames.contains("card color 2"))
        assertTrue(!visibleNames.contains("loyalty"))
        assertTrue(!visibleNames.contains("image"))
    }

    @Test
    fun testScriptedStyleFallbackHitboxesAndLocalZipFrameBlacklist() {
        val scriptedStyleText = """
            mse version: 2.0.0
            game: magic
            short name: M15 Mainframe
            full name: M15 Mainframe (Scripted Coordinates)
            card width: 744
            card height: 1039
            card dpi: 300
            card style:
            	name:
            		left: { name_left() }
            		top: { name_top() }
            		width: { name_width() }
            		height: { name_height() }
            	image:
            		left: { image_left() }
            		top: { image_top() }
            		width: { image_width() }
            		height: { image_height() }
            	rule text:
            		left: { rule_text_left() }
            		top: { rule_text_top() }
            		width: { rule_text_width() }
            		height: { rule_text_height() }
        """.trimIndent()

        val spec = MseStyleParser.parseStyleText(scriptedStyleText, "magic-m15-altered.mse-style")
        assertTrue(
            "Scripted brace coordinate style must report hasValidCompositeGeometry() == false",
            !spec.hasValidCompositeGeometry(),
        )

        val effectiveHitboxes = com.example.ui.workspace.resolveEffectiveCompositeHitboxes(spec)
        assertTrue("Fallback hitboxes must never be empty", effectiveHitboxes.isNotEmpty())

        val hitboxMap = effectiveHitboxes.associateBy { it.field }
        assertTrue("Fallback hitboxes must contain 'image'", hitboxMap.containsKey("image"))
        assertTrue("Fallback hitboxes must contain 'name'", hitboxMap.containsKey("name"))
        assertTrue("Fallback hitboxes must contain 'rule text'", hitboxMap.containsKey("rule text"))

        // Verify proportional scaling from 375x523 standard spec up to 744x1039
        val standardSpec = MseStyleParser.defaultStandardGeometrySpec()
        val stdName = standardSpec.hitboxes.first { it.field == "name" }
        val scaledName = hitboxMap.getValue("name")
        val expectedScaleX = 744f / standardSpec.cardWidthPx.toFloat()
        val expectedScaleY = 1039f / standardSpec.cardHeightPx.toFloat()
        assertEquals(stdName.left * expectedScaleX, scaledName.left, 0.5f)
        assertEquals(stdName.top * expectedScaleY, scaledName.top, 0.5f)
        assertEquals(stdName.width * expectedScaleX, scaledName.width, 0.5f)
        assertEquals(stdName.height * expectedScaleY, scaledName.height, 0.5f)

        // Verify local .mse-style ZIP blacklist rejects overlays while keeping full base frames
        assertTrue(MseStyleParser.isBlacklistedNonCardAsset("20slice.png"))
        assertTrue(MseStyleParser.isBlacklistedNonCardAsset("loyalty.png"))
        assertTrue(MseStyleParser.isBlacklistedNonCardAsset("flashdot.png"))
        assertTrue(MseStyleParser.isBlacklistedNonCardAsset("pt_box.png"))
        assertTrue(!MseStyleParser.isBlacklistedNonCardAsset("ccard.jpg"))
        assertTrue(!MseStyleParser.isBlacklistedNonCardAsset("wcard.jpg"))
        assertTrue(!MseStyleParser.isBlacklistedNonCardAsset("mcard.jpg"))
    }
}


