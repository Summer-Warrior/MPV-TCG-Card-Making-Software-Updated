package com.example.domain.frameworks

import com.example.data.mse.MseHitbox
import com.example.data.mse.MseStyleSpec

/**
 * Pure Kotlin generator that builds valid clean-room `.mse-game` (`game`) and `.mse-style` (`style`)
 * scaffolds for any of the 110+ Universal TCG Card Frameworks, written strictly into
 * `custom_templates/` (never touching read-only `upstream/`).
 *
 * Also builds structured [MseStyleSpec] descriptors so `StructuredOfflineCardLayout` shows
 * ONLY that TCG's real fields while strictly preserving `image == null` until user upload.
 */

data class GeneratedMseScaffold(
    val frameworkId: String,
    val gameFolderName: String,
    val styleFolderName: String,
    val gameFileContent: String,
    val styleFileContent: String,
)

object FrameworkMseScaffoldGenerator {

    fun sanitizeMseId(raw: String): String =
        raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "custom-tcg" }

    fun generateScaffold(framework: UniversalCardFramework): GeneratedMseScaffold {
        val gameSlug = sanitizeMseId(framework.upstreamMseGameId ?: framework.id)
        val styleSlug = sanitizeMseId(framework.upstreamMseStyleId ?: "standard")
        val fields = framework.resolvedFieldSpecs()

        val gameFile = buildString {
            appendLine("mse version: 2.0.0")
            appendLine("short name: ${framework.effectiveShortName}")
            appendLine("full name: ${framework.name}")
            appendLine("position hint: 900")
            appendLine("")
            appendLine("init script:")
            appendLine("\t# Clean-room generated scaffold for ${framework.name}")
            appendLine("\t# Category: ${framework.category}")
            appendLine("")
            appendLine("set field:")
            appendLine("\ttype: text")
            appendLine("\tname: title")
            appendLine("set field:")
            appendLine("\ttype: text")
            appendLine("\tname: set code")
            appendLine("set field:")
            appendLine("\ttype: text")
            appendLine("\tname: description")
            appendLine("\tmulti line: true")
            appendLine("")
            appendLine("card field:")
            appendLine("\ttype: image")
            appendLine("\tname: image")
            appendLine("\tdescription: User-supplied artwork (strictly null/blank by default)")
            appendLine("")
            fields.forEach { field ->
                if (!field.key.equals("image", ignoreCase = true)) {
                    appendLine("card field:")
                    appendLine("\ttype: text")
                    appendLine("\tname: ${field.key}")
                    if (field.fieldType == "multiline" || field.fieldType == "grid") {
                        appendLine("\tmulti line: true")
                    }
                    appendLine("\tdescription: ${field.label}")
                }
            }
            // Lossless Fan-Fiction Split-Off Canon Preservation metadata fields
            appendLine("card field:")
            appendLine("\ttype: text")
            appendLine("\tname: card origin")
            appendLine("\tshow statistics: false")
            appendLine("card field:")
            appendLine("\ttype: text")
            appendLine("\tname: linked canon card id")
            appendLine("\tshow statistics: false")
            appendLine("card field:")
            appendLine("\ttype: text")
            appendLine("\tname: canon source id")
            appendLine("\tshow statistics: false")
        }

        val w = framework.cardWidthPx.coerceAtLeast(250)
        val h = framework.cardHeightPx.coerceAtLeast(350)
        val margin = (w * 0.06f).toInt()
        val contentW = w - (margin * 2)

        val styleFile = buildString {
            appendLine("mse version: 2.0.0")
            appendLine("game: $gameSlug")
            appendLine("short name: ${framework.effectiveShortName} Standard")
            appendLine("full name: ${framework.name} — Clean-Room Scaffold")
            appendLine("position hint: 100")
            appendLine("")
            appendLine("card width: $w")
            appendLine("card height: $h")
            appendLine("card dpi: ${framework.dpi.coerceAtLeast(150)}")
            appendLine("")
            appendLine("card style:")
            // Art window hitbox (strictly empty unless user supplies art)
            val artTop = (h * 0.12f).toInt()
            val artHeight = (h * 0.38f).toInt()
            appendLine("\timage:")
            appendLine("\t\tleft: $margin")
            appendLine("\t\ttop: $artTop")
            appendLine("\t\twidth: $contentW")
            appendLine("\t\theight: $artHeight")
            appendLine("\t\tz index: 1")

            var cursorTop = (h * 0.53f).toInt()
            val rowStep = ((h * 0.42f) / fields.size.coerceAtLeast(1)).toInt().coerceIn(22, 64)
            fields.forEachIndexed { idx, field ->
                val isHeader = field.section == "header"
                val boxTop = if (isHeader) (h * 0.04f).toInt() else cursorTop
                val boxLeft = if (isHeader && idx > 0) (w * 0.68f).toInt() else margin
                val boxWidth = if (isHeader && idx == 0) (contentW * 0.65f).toInt()
                else if (isHeader) (contentW * 0.30f).toInt()
                else contentW
                val boxHeight = if (field.fieldType == "multiline" || field.fieldType == "grid") {
                    (rowStep * 1.8f).toInt().coerceAtMost(h - boxTop - 16)
                } else {
                    rowStep.coerceAtMost(32)
                }
                if (!isHeader) {
                    cursorTop = (cursorTop + boxHeight + 4).coerceAtMost(h - 32)
                }
                appendLine("\t${field.key}:")
                appendLine("\t\tleft: $boxLeft")
                appendLine("\t\ttop: $boxTop")
                appendLine("\t\twidth: $boxWidth")
                appendLine("\t\theight: $boxHeight")
                appendLine("\t\tz index: ${idx + 2}")
                appendLine("\t\tfont:")
                appendLine("\t\t\tname: Open Sans")
                appendLine("\t\t\tsize: ${if (isHeader) 14 else 11}")
            }
        }

        return GeneratedMseScaffold(
            frameworkId = framework.id,
            gameFolderName = "$gameSlug.mse-game",
            styleFolderName = "$gameSlug-$styleSlug.mse-style",
            gameFileContent = gameFile,
            styleFileContent = styleFile,
        )
    }

    /**
     * Converts a [UniversalCardFramework] into an [MseStyleSpec] for clean structured editing
     * when no upstream MagicSetEditorPacks `.mse-style` is installed yet.
     */
    fun toStructuredStyleSpec(framework: UniversalCardFramework): MseStyleSpec {
        val w = framework.cardWidthPx.coerceAtLeast(250)
        val h = framework.cardHeightPx.coerceAtLeast(350)
        val fields = framework.resolvedFieldSpecs()
        val hitboxes = buildList {
            add(
                MseHitbox(
                    field = "image",
                    left = w * 0.06f,
                    top = h * 0.12f,
                    width = w * 0.88f,
                    height = h * 0.38f,
                    zIndex = 1,
                )
            )
            fields.forEachIndexed { idx, f ->
                add(
                    MseHitbox(
                        field = f.key,
                        left = w * 0.06f,
                        top = h * (0.04f + (idx * 0.08f)).coerceAtMost(0.90f),
                        width = w * 0.88f,
                        height = if (f.fieldType == "multiline") h * 0.18f else h * 0.06f,
                        zIndex = idx + 2,
                    )
                )
            }
        }
        return MseStyleSpec(
            stylesheetId = sanitizeMseId(framework.upstreamMseStyleId ?: "standard"),
            game = sanitizeMseId(framework.upstreamMseGameId ?: framework.id),
            shortName = framework.effectiveShortName,
            fullName = framework.name,
            cardWidthPx = w,
            cardHeightPx = h,
            cardDpi = framework.dpi,
            hitboxes = hitboxes,
            referencedFonts = listOf("Open Sans"),
            sourceLabel = "Universal Framework (${framework.categoryEnum.chipLabel})",
        )
    }
}
