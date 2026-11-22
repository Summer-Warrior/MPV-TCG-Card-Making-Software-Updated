package com.example.data.mse

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Real Magic Set Editor 2 `.mse-style` parser.
 * Extracts `card width`, `card height`, `card dpi`, `card style` field geometry
 * (`left`, `top`, `width`, `height`, `right`, `bottom`, `z index`), and referenced `font: name:` families.
 */
object MseStyleParser {

    private val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
    private val ELSE_NUMBER_REGEX = Regex("\\belse\\s+(-?\\d+(?:\\.\\d+)?)")
    private val QUOTED_FONT_REGEX = Regex("\"([^\"]+)\"")

    fun parseStyleText(rawText: String, sourceLabel: String = "style"): MseStyleSpec {
        val cleaned = rawText.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val lines = cleaned.split("\n")

        var game = "magic"
        var shortName = "Custom Style"
        var fullName = "Custom Style"
        var cardWidth = 375
        var cardHeight = 523
        var cardDpi = 150

        val hitboxes = mutableListOf<MseHitbox>()
        val referencedFonts = linkedSetOf<String>()

        var inCardStyle = false
        var currentField: String? = null
        var inFontSubBlock = false

        var left: Float? = null
        var top: Float? = null
        var width: Float? = null
        var height: Float? = null
        var right: Float? = null
        var bottom: Float? = null
        var zIndex = 0
        var fieldFont: String? = null

        fun flushCurrentField() {
            val fieldName = currentField ?: return
            val l = left
            val t = top
            val w = width
            val h = height
            val r = right
            val b = bottom
            val resolvedLeft = l ?: if (r != null && w != null) r - w else null
            val resolvedTop = t ?: if (b != null && h != null) b - h else null
            val resolvedWidth = w ?: if (r != null && resolvedLeft != null) r - resolvedLeft else null
            val resolvedHeight = h ?: if (b != null && resolvedTop != null) b - resolvedTop else null

            if (resolvedLeft != null && resolvedTop != null &&
                resolvedWidth != null && resolvedHeight != null &&
                resolvedWidth > 0f && resolvedHeight > 0f
            ) {
                hitboxes += MseHitbox(
                    field = fieldName,
                    left = resolvedLeft,
                    top = resolvedTop,
                    width = resolvedWidth,
                    height = resolvedHeight,
                    zIndex = zIndex,
                    fontFamily = fieldFont,
                )
            }

            currentField = null
            inFontSubBlock = false
            left = null
            top = null
            width = null
            height = null
            right = null
            bottom = null
            zIndex = 0
            fieldFont = null
        }

        for (rawLine in lines) {
            if (rawLine.isBlank()) continue
            val tabs = countLeadingTabs(rawLine)
            val trimmed = rawLine.substring(tabs).trim()
            if (trimmed.startsWith("#")) continue

            when (tabs) {
                0 -> {
                    flushCurrentField()
                    inCardStyle = false
                    val colon = trimmed.indexOf(':')
                    if (colon > 0) {
                        val key = trimmed.substring(0, colon).trim()
                        val value = trimmed.substring(colon + 1).trim()
                        when (key) {
                            "game" -> if (value.isNotEmpty()) game = value
                            "short name" -> if (value.isNotEmpty()) shortName = value
                            "full name" -> if (value.isNotEmpty()) fullName = value
                            "card width" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardWidth = it }
                            "card height" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardHeight = it }
                            "card dpi" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardDpi = it }
                            "card style" -> inCardStyle = true
                        }
                    }
                }

                1 -> {
                    if (inCardStyle) {
                        flushCurrentField()
                        val colon = trimmed.indexOf(':')
                        if (colon > 0) {
                            currentField = trimmed.substring(0, colon).trim()
                        }
                    }
                }

                2 -> {
                    if (inCardStyle && currentField != null) {
                        inFontSubBlock = false
                        val colon = trimmed.indexOf(':')
                        if (colon > 0) {
                            val key = trimmed.substring(0, colon).trim()
                            val value = trimmed.substring(colon + 1).trim()
                            when (key) {
                                "left" -> left = extractNumeric(value)
                                "top" -> top = extractNumeric(value)
                                "width" -> width = extractNumeric(value)
                                "height" -> height = extractNumeric(value)
                                "right" -> right = extractNumeric(value)
                                "bottom" -> bottom = extractNumeric(value)
                                "z index" -> zIndex = extractNumeric(value)?.toInt() ?: 0
                                "font" -> inFontSubBlock = true
                            }
                        }
                    }
                }

                3 -> {
                    if (inCardStyle && currentField != null && inFontSubBlock) {
                        val colon = trimmed.indexOf(':')
                        if (colon > 0) {
                            val key = trimmed.substring(0, colon).trim()
                            val value = trimmed.substring(colon + 1).trim()
                            if (key == "name" || key == "italic name" || key == "symbol font") {
                                extractFontNames(value).forEach { font ->
                                    if (fieldFont == null && key == "name") {
                                        fieldFont = font
                                    }
                                    referencedFonts.add(font)
                                }
                            }
                        }
                    }
                }
            }
        }
        flushCurrentField()

        val stylesheetId = sourceLabel
            .substringAfterLast('/')
            .removeSuffix(".mse-style")
            .removeSuffix(".zip")
            .ifBlank { shortName.lowercase().replace(' ', '-') }

        return MseStyleSpec(
            stylesheetId = stylesheetId,
            game = game,
            shortName = shortName,
            fullName = fullName,
            cardWidthPx = cardWidth,
            cardHeightPx = cardHeight,
            cardDpi = cardDpi,
            hitboxes = hitboxes.sortedBy { it.zIndex },
            referencedFonts = referencedFonts.toList(),
            sourceLabel = sourceLabel,
        )
    }

    /**
     * Reads either a plain text `style` file or a zipped `.mse-style` package containing a `style` entry.
     */
    fun parseStyleInput(inputStream: InputStream, sourceLabel: String): MseStyleSpec {
        val rawBytes = inputStream.readBytes()
        // Detect ZIP magic header PK\x03\x04
        val isZip = rawBytes.size >= 4 &&
            rawBytes[0] == 0x50.toByte() &&
            rawBytes[1] == 0x4B.toByte() &&
            rawBytes[2] == 0x03.toByte() &&
            rawBytes[3] == 0x04.toByte()

        if (isZip) {
            ZipInputStream(rawBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (entry.name == "style" || entry.name.endsWith("/style"))) {
                        val text = zis.readBytes().toString(StandardCharsets.UTF_8)
                        return parseStyleText(text, sourceLabel)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            throw IllegalArgumentException("ZIP archive does not contain a 'style' definition file.")
        }

        val text = rawBytes.toString(StandardCharsets.UTF_8)
        return parseStyleText(text, sourceLabel)
    }

    /**
     * Standard M15 coordinate geometry spec (metadata only — NOT a fake card renderer;
     * used solely to place transparent tap hitboxes when offline before a custom `.mse-style` is loaded).
     */
    fun defaultStandardGeometrySpec(): MseStyleSpec = MseStyleSpec(
        stylesheetId = "m15-altered",
        game = "magic",
        shortName = "M15 Standard Geometry",
        fullName = "Magic M15 Hitbox Geometry (375×523 @ 150 DPI)",
        cardWidthPx = 375,
        cardHeightPx = 523,
        cardDpi = 150,
        hitboxes = listOf(
            MseHitbox(field = "name", left = 30f, top = 26f, width = 240f, height = 26f, zIndex = 2, fontFamily = "Beleren2016"),
            MseHitbox(field = "casting cost", left = 274f, top = 26f, width = 71f, height = 26f, zIndex = 2),
            MseHitbox(field = "image", left = 29f, top = 60f, width = 316f, height = 231f, zIndex = 1),
            MseHitbox(field = "super type", left = 32f, top = 297f, width = 150f, height = 22f, zIndex = 2, fontFamily = "Beleren2016"),
            MseHitbox(field = "sub type", left = 186f, top = 297f, width = 124f, height = 22f, zIndex = 2, fontFamily = "Beleren2016"),
            MseHitbox(field = "rule text", left = 30f, top = 327f, width = 314f, height = 104f, zIndex = 2, fontFamily = "MPlantin"),
            MseHitbox(field = "flavor text", left = 30f, top = 433f, width = 314f, height = 44f, zIndex = 2, fontFamily = "MPlantin"),
            MseHitbox(field = "power", left = 292f, top = 468f, width = 28f, height = 24f, zIndex = 3, fontFamily = "Beleren2016"),
            MseHitbox(field = "toughness", left = 322f, top = 468f, width = 28f, height = 24f, zIndex = 3, fontFamily = "Beleren2016"),
            MseHitbox(field = "illustrator", left = 44f, top = 493f, width = 180f, height = 18f, zIndex = 2, fontFamily = "Relay-Medium"),
        ),
        referencedFonts = listOf("Beleren2016", "MPlantin", "Relay-Medium"),
        sourceLabel = "Built-in M15 hitbox geometry (load .mse-style or connect MSE Bridge for template-exact boxes)",
    )

    private fun countLeadingTabs(line: String): Int {
        var count = 0
        while (count < line.length && line[count] == '\t') {
            count++
        }
        return count
    }

    private fun extractNumeric(raw: String): Float? {
        raw.toFloatOrNull()?.let { return it }
        // If scripted (`{ if ... then 35 else 29 }`), prefer the `else` fallback number
        ELSE_NUMBER_REGEX.find(raw)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        return NUMBER_REGEX.find(raw)?.value?.toFloatOrNull()
    }

    private fun extractFontNames(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.contains('{') && !trimmed.contains('}')) {
            return listOf(trimmed.trim('"'))
        }
        return QUOTED_FONT_REGEX.findAll(trimmed)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && !it.startsWith("magic-") }
            .toList()
    }
}
