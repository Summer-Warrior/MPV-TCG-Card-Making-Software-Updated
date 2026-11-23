package com.example.data.mse

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Real Magic Set Editor 2 `.mse-game` & `.mse-style` parser.
 * Extracts:
 * - `full name:`, `short name:`, `game:`, `icon:`, `position hint:`, `depends on:`
 * - `card width:`, `card height:`, `card dpi:`
 * - `card style:` field blocks (`left`, `top`, `width`, `height`, `right`, `bottom`, `z index`,
 *   `alignment`, `font: name/size/color`, `default:`, `choice images:`, and frame image references).
 */
object MseStyleParser {

    private val NUMBER_REGEX = Regex("-?\\d+(?:\\.\\d+)?")
    private val ELSE_NUMBER_REGEX = Regex("\\belse\\s+(-?\\d+(?:\\.\\d+)?)")
    private val QUOTED_STRING_REGEX = Regex("\"([^\"]+)\"")
    private val IMAGE_FILE_REGEX = Regex("([A-Za-z0-9_./\\- ]+\\.(?:png|jpg|jpeg|webp))", RegexOption.IGNORE_CASE)
    private val RGB_REGEX = Regex("rgb\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)

    private val FRAME_FIELD_KEYS = setOf(
        "card color",
        "background",
        "frame",
        "card frame",
        "card background",
        "border",
        "type frame",
        "attribute",
        "color",
    )

    fun parseGameText(rawText: String, folderName: String): ParsedMseGameMetadata {
        val cleaned = rawText.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val lines = cleaned.split("\n")

        val gameId = folderName.removeSuffix(".mse-game").trim().lowercase()
        var shortName = gameId.replace('-', ' ').replaceFirstChar { it.uppercase() }
        var fullName = shortName
        var iconFile: String? = null
        var positionHint = 999
        var cardWidth: Int? = null
        var cardHeight: Int? = null
        var cardDpi: Int? = null

        for (rawLine in lines) {
            if (rawLine.isBlank()) continue
            val tabs = countLeadingTabs(rawLine)
            if (tabs != 0) continue
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("#")) continue
            val colon = trimmed.indexOf(':')
            if (colon <= 0) continue
            val key = trimmed.substring(0, colon).trim().lowercase()
            val value = trimmed.substring(colon + 1).trim()
            when (key) {
                "short name" -> if (value.isNotEmpty()) shortName = value
                "full name" -> if (value.isNotEmpty()) fullName = value
                "icon" -> if (value.isNotEmpty()) iconFile = value.trim('"')
                "position hint" -> extractNumeric(value)?.toInt()?.let { positionHint = it }
                "card width" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardWidth = it }
                "card height" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardHeight = it }
                "card dpi" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardDpi = it }
            }
        }

        if (fullName == shortName && shortName.isBlank()) {
            fullName = gameId.replace('-', ' ').replaceFirstChar { it.uppercase() }
            shortName = fullName
        }

        return ParsedMseGameMetadata(
            gameId = gameId,
            shortName = shortName.ifBlank { fullName },
            fullName = fullName.ifBlank { shortName },
            iconFile = iconFile,
            positionHint = positionHint,
            cardWidthPx = cardWidth,
            cardHeightPx = cardHeight,
            cardDpi = cardDpi,
        )
    }

    fun parseStyleText(rawText: String, sourceLabel: String = "style"): MseStyleSpec {
        val cleaned = rawText.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val lines = cleaned.split("\n")

        var game = "magic"
        var shortName = ""
        var fullName = ""
        var iconFile: String? = null
        var positionHint = 999
        var cardWidth = 375
        var cardHeight = 523
        var cardDpi = 150

        val hitboxes = mutableListOf<MseHitbox>()
        val referencedFonts = linkedSetOf<String>()
        val dependsOn = linkedSetOf<String>()
        val baseFrameChoices = linkedMapOf<String, String>()
        var defaultFrameImage: String? = null
        var usesScriptedBackground = false

        var inCardStyle = false
        var inDependsOn = false
        var currentField: String? = null
        var subBlock: String? = null // "font" | "choice images"

        var left: Float? = null
        var top: Float? = null
        var width: Float? = null
        var height: Float? = null
        var right: Float? = null
        var bottom: Float? = null
        var zIndex = 0
        var fieldFont: String? = null
        var fieldFontSize: Float? = null
        var fieldFontColorHex: String? = null
        var fieldAlignment: String? = null

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
                    fontSize = fieldFontSize,
                    fontColorHex = fieldFontColorHex,
                    alignment = fieldAlignment,
                )
            }

            currentField = null
            subBlock = null
            left = null
            top = null
            width = null
            height = null
            right = null
            bottom = null
            zIndex = 0
            fieldFont = null
            fieldFontSize = null
            fieldFontColorHex = null
            fieldAlignment = null
        }

        for (rawLine in lines) {
            if (rawLine.isBlank()) continue
            val tabs = countLeadingTabs(rawLine)
            val trimmed = rawLine.substring(tabs).trim()
            if (trimmed.startsWith("#")) continue

            // Capture explicit `include file: /magic-modules.mse-include/...` references anywhere in style
            if (trimmed.startsWith("include file:", ignoreCase = true)) {
                val incPath = trimmed.substringAfter(':').trim().trim('"')
                val pkg = incPath.trimStart('/').substringBefore('/')
                if (pkg.endsWith(".mse-include") || pkg.endsWith(".mse-style") || pkg.endsWith(".mse-game")) {
                    dependsOn.add(pkg)
                }
            }

            when (tabs) {
                0 -> {
                    flushCurrentField()
                    inCardStyle = false
                    inDependsOn = false
                    val colon = trimmed.indexOf(':')
                    if (colon > 0) {
                        val key = trimmed.substring(0, colon).trim().lowercase()
                        val value = trimmed.substring(colon + 1).trim()
                        when (key) {
                            "game" -> if (value.isNotEmpty()) game = value.lowercase()
                            "short name" -> if (value.isNotEmpty()) shortName = value
                            "full name" -> if (value.isNotEmpty()) fullName = value
                            "icon" -> if (value.isNotEmpty()) iconFile = value.trim('"')
                            "position hint" -> extractNumeric(value)?.toInt()?.let { positionHint = it }
                            "card width" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardWidth = it }
                            "card height" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardHeight = it }
                            "card dpi" -> extractNumeric(value)?.toInt()?.let { if (it > 0) cardDpi = it }
                            "card style" -> inCardStyle = true
                            "depends on" -> {
                                inDependsOn = true
                                if (value.isNotEmpty()) {
                                    dependsOn.add(value.trim('"'))
                                }
                            }
                        }
                    }
                }

                1 -> {
                    if (inDependsOn) {
                        val colon = trimmed.indexOf(':')
                        if (colon > 0) {
                            val key = trimmed.substring(0, colon).trim().lowercase()
                            val value = trimmed.substring(colon + 1).trim().trim('"')
                            if (key == "package" && value.isNotEmpty()) {
                                dependsOn.add(value)
                            }
                        }
                    } else if (inCardStyle) {
                        flushCurrentField()
                        val colon = trimmed.indexOf(':')
                        if (colon > 0) {
                            currentField = trimmed.substring(0, colon).trim().lowercase()
                        }
                    }
                }

                2 -> {
                    if (inCardStyle && currentField != null) {
                        subBlock = null
                        val colon = trimmed.indexOf(':')
                        if (colon > 0) {
                            val key = trimmed.substring(0, colon).trim().lowercase()
                            val value = trimmed.substring(colon + 1).trim()
                            val isFrameField = currentField in FRAME_FIELD_KEYS
                            when (key) {
                                "left" -> left = extractNumeric(value)
                                "top" -> top = extractNumeric(value)
                                "width" -> width = extractNumeric(value)
                                "height" -> height = extractNumeric(value)
                                "right" -> right = extractNumeric(value)
                                "bottom" -> bottom = extractNumeric(value)
                                "z index" -> zIndex = extractNumeric(value)?.toInt() ?: 0
                                "alignment" -> fieldAlignment = value
                                "font" -> subBlock = "font"
                                "choice images" -> subBlock = "choice images"
                                "default" -> {
                                    if (isFrameField) {
                                        if (value.contains("script:") || value.contains('{') || value.contains("card_background")) {
                                            usesScriptedBackground = true
                                        }
                                        extractImageReference(value)?.let { imgRef ->
                                            if (defaultFrameImage == null && !isNonFrameAsset(imgRef)) {
                                                defaultFrameImage = imgRef
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    if (inCardStyle && currentField != null) {
                        val isFrameField = currentField in FRAME_FIELD_KEYS
                        when (subBlock) {
                            "font" -> {
                                val colon = trimmed.indexOf(':')
                                if (colon > 0) {
                                    val key = trimmed.substring(0, colon).trim().lowercase()
                                    val value = trimmed.substring(colon + 1).trim()
                                    when (key) {
                                        "name", "italic name", "symbol font" -> {
                                            extractFontNames(value).forEach { font ->
                                                if (fieldFont == null && key == "name") {
                                                    fieldFont = font
                                                }
                                                referencedFonts.add(font)
                                            }
                                        }

                                        "size" -> fieldFontSize = extractNumeric(value)
                                        "color" -> fieldFontColorHex = parseColorHex(value)
                                    }
                                }
                            }

                            "choice images" -> {
                                val colon = trimmed.indexOf(':')
                                if (colon > 0 && isFrameField) {
                                    val choiceKey = trimmed.substring(0, colon).trim()
                                    val choiceVal = trimmed.substring(colon + 1).trim()
                                    if (choiceVal.contains("script:") || choiceVal.contains("card_background") || choiceVal.contains('{')) {
                                        usesScriptedBackground = true
                                    }
                                    val imgRef = extractImageReference(choiceVal)
                                    if (imgRef != null && !isNonFrameAsset(imgRef)) {
                                        baseFrameChoices[choiceKey] = imgRef
                                        if (choiceKey.equals("default", ignoreCase = true) && defaultFrameImage == null) {
                                            defaultFrameImage = imgRef
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Deeper indentation (e.g., scripted choice images lines)
                    if (inCardStyle && currentField in FRAME_FIELD_KEYS && subBlock == "choice images") {
                        if (trimmed.contains("card_background") || trimmed.contains("script:")) {
                            usesScriptedBackground = true
                        }
                    }
                }
            }
        }
        flushCurrentField()

        val folderClean = sourceLabel
            .substringAfterLast('/')
            .removeSuffix(".mse-style")
            .removeSuffix(".zip")
            .trim()

        val stylesheetId = folderClean
            .removePrefix("$game-")
            .ifBlank { folderClean }
            .ifBlank { shortName.lowercase().replace(' ', '-') }

        val resolvedShortName = shortName.ifBlank {
            fullName.ifBlank {
                stylesheetId.replace('-', ' ').replaceFirstChar { it.uppercase() }
            }
        }
        val resolvedFullName = fullName.ifBlank { resolvedShortName }

        if (defaultFrameImage == null && baseFrameChoices.isNotEmpty()) {
            defaultFrameImage = baseFrameChoices.entries.firstOrNull { (k, _) ->
                k.equals("default", true) || k.equals("colorless", true) || k.equals("white", true)
            }?.value ?: baseFrameChoices.values.firstOrNull()
        }

        return MseStyleSpec(
            stylesheetId = stylesheetId,
            game = game,
            shortName = resolvedShortName,
            fullName = resolvedFullName,
            cardWidthPx = cardWidth,
            cardHeightPx = cardHeight,
            cardDpi = cardDpi,
            hitboxes = hitboxes.sortedBy { it.zIndex },
            referencedFonts = referencedFonts.toList(),
            sourceLabel = sourceLabel,
            iconFile = iconFile,
            positionHint = positionHint,
            dependsOn = dependsOn.toList(),
            baseFrameChoices = baseFrameChoices.toMap(),
            defaultFrameImage = defaultFrameImage,
            usesScriptedBackground = usesScriptedBackground,
        )
    }

    /**
     * Reads either a plain text `style` file or a zipped `.mse-style` package containing a `style` entry.
     */
    fun parseStyleInput(inputStream: InputStream, sourceLabel: String): MseStyleSpec {
        val rawBytes = inputStream.readBytes()
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
     * Standard M15 coordinate geometry spec (metadata only — NOT a fake card renderer).
     */
    fun defaultStandardGeometrySpec(): MseStyleSpec = MseStyleSpec(
        stylesheetId = "m15-altered",
        game = "magic",
        shortName = "M15 Altered",
        fullName = "Magic M15 Altered (375×523 @ 150 DPI)",
        cardWidthPx = 375,
        cardHeightPx = 523,
        cardDpi = 150,
        hitboxes = listOf(
            MseHitbox(field = "name", left = 30f, top = 26f, width = 240f, height = 26f, zIndex = 2, fontFamily = "Beleren2016", fontSize = 16f),
            MseHitbox(field = "casting cost", left = 274f, top = 26f, width = 71f, height = 26f, zIndex = 2, fontSize = 14f, alignment = "middle right"),
            MseHitbox(field = "image", left = 29f, top = 60f, width = 316f, height = 231f, zIndex = 1),
            MseHitbox(field = "super type", left = 32f, top = 297f, width = 150f, height = 22f, zIndex = 2, fontFamily = "Beleren2016", fontSize = 13f),
            MseHitbox(field = "sub type", left = 186f, top = 297f, width = 124f, height = 22f, zIndex = 2, fontFamily = "Beleren2016", fontSize = 13f),
            MseHitbox(field = "rule text", left = 30f, top = 327f, width = 314f, height = 104f, zIndex = 2, fontFamily = "MPlantin", fontSize = 13f),
            MseHitbox(field = "flavor text", left = 30f, top = 433f, width = 314f, height = 44f, zIndex = 2, fontFamily = "MPlantin", fontSize = 12f),
            MseHitbox(field = "power", left = 292f, top = 468f, width = 28f, height = 24f, zIndex = 3, fontFamily = "Beleren2016", fontSize = 15f),
            MseHitbox(field = "toughness", left = 322f, top = 468f, width = 28f, height = 24f, zIndex = 3, fontFamily = "Beleren2016", fontSize = 15f),
            MseHitbox(field = "illustrator", left = 44f, top = 493f, width = 180f, height = 18f, zIndex = 2, fontFamily = "Relay-Medium", fontSize = 9f),
        ),
        referencedFonts = listOf("Beleren2016", "MPlantin", "Relay-Medium"),
        sourceLabel = "M15 Altered Geometry",
        usesScriptedBackground = true,
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
        ELSE_NUMBER_REGEX.find(raw)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        return NUMBER_REGEX.find(raw)?.value?.toFloatOrNull()
    }

    private fun extractImageReference(raw: String): String? {
        val trimmed = raw.trim().trim('"')
        if (trimmed.isEmpty()) return null
        val match = IMAGE_FILE_REGEX.find(trimmed) ?: return null
        return match.groupValues[1].trim()
    }

    private fun isNonFrameAsset(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.contains("mask") ||
            lower.contains("symbol") ||
            lower.contains("icon") ||
            lower.contains("card-sample")
    }

    private fun extractFontNames(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.contains('{') && !trimmed.contains('}')) {
            return listOf(trimmed.trim('"'))
        }
        return QUOTED_STRING_REGEX.findAll(trimmed)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && !it.startsWith("magic-") }
            .toList()
    }

    private fun parseColorHex(raw: String): String? {
        val match = RGB_REGEX.find(raw) ?: return null
        val r = match.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val g = match.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val b = match.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
        return String.format("#%02X%02X%02X", r, g, b)
    }
}
