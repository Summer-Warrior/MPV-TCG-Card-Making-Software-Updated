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
}
