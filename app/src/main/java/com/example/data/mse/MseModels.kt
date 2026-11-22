package com.example.data.mse

/**
 * Lossless AST node for Magic Set Editor 2 tab-indented text files (`set`).
 * Preserves unknown fields, comments, blank lines, ordering, and multiline text verbatim.
 */
sealed class MseLineNode {
    data class CommentOrBlank(val rawLine: String) : MseLineNode()

    data class Entry(
        val key: String,
        val inlineValue: String? = null,
        val multilineValue: List<String> = emptyList(),
        val children: List<MseLineNode> = emptyList(),
        val isBlock: Boolean = false,
    ) : MseLineNode() {
        val textValue: String
            get() = when {
                multilineValue.isNotEmpty() -> multilineValue.joinToString("\n")
                inlineValue != null -> inlineValue
                else -> ""
            }
    }
}

/**
 * Lossless representation of an entire `.mse-set`'s `set` file.
 */
data class MseSetDocument(
    val nodes: List<MseLineNode>,
    val hadUtf8Bom: Boolean = false,
    val lineSeparator: String = "\n",
) {
    val mseVersion: String
        get() = findTopLevelScalar("mse version") ?: "2.0.0"

    val game: String
        get() = findTopLevelScalar("game") ?: "magic"

    val stylesheet: String
        get() = findTopLevelScalar("stylesheet") ?: "m15-altered"

    val setTitle: String
        get() {
            val setInfo = nodes.filterIsInstance<MseLineNode.Entry>()
                .firstOrNull { it.key == "set info" && it.isBlock }
            return setInfo?.children
                ?.filterIsInstance<MseLineNode.Entry>()
                ?.firstOrNull { it.key == "title" }
                ?.textValue
                ?.takeIf { it.isNotBlank() }
                ?: "Untitled Set"
        }

    val cards: List<MseCardView>
        get() {
            val result = mutableListOf<MseCardView>()
            var cardIndex = 0
            nodes.forEachIndexed { nodeIndex, node ->
                if (node is MseLineNode.Entry && node.key == "card" && node.isBlock) {
                    result += MseCardView(
                        index = cardIndex++,
                        documentNodeIndex = nodeIndex,
                        entry = node,
                    )
                }
            }
            return result
        }

    fun findTopLevelScalar(key: String): String? =
        nodes.filterIsInstance<MseLineNode.Entry>()
            .firstOrNull { it.key == key && !it.isBlock }
            ?.textValue

    fun withTopLevelScalar(key: String, value: String): MseSetDocument {
        val updated = nodes.toMutableList()
        val idx = updated.indexOfFirst { it is MseLineNode.Entry && it.key == key && !it.isBlock }
        val newEntry = MseLineNode.Entry(key = key, inlineValue = value)
        if (idx >= 0) {
            updated[idx] = newEntry
        } else {
            updated.add(0, newEntry)
        }
        return copy(nodes = updated)
    }

    fun withCardFieldUpdated(cardIndex: Int, fieldKey: String, newValue: String?): MseSetDocument {
        val targetCard = cards.getOrNull(cardIndex) ?: return this
        val cardEntry = targetCard.entry
        val updatedChildren = cardEntry.children.toMutableList()
        val existingIdx = updatedChildren.indexOfFirst {
            it is MseLineNode.Entry && it.key == fieldKey
        }

        // CRITICAL CONTRACT: image field MUST be removed/empty when null or blank.
        if (fieldKey == "image" && newValue.isNullOrBlank()) {
            if (existingIdx >= 0) {
                updatedChildren.removeAt(existingIdx)
            }
        } else {
            val cleanVal = newValue ?: ""
            val isMultiline = cleanVal.contains('\n') ||
                fieldKey in MULTILINE_CARD_FIELDS && cleanVal.isNotEmpty()
            val replacement = if (isMultiline) {
                MseLineNode.Entry(
                    key = fieldKey,
                    inlineValue = null,
                    multilineValue = cleanVal.split("\n"),
                    isBlock = false,
                )
            } else {
                MseLineNode.Entry(
                    key = fieldKey,
                    inlineValue = cleanVal,
                    multilineValue = emptyList(),
                    isBlock = false,
                )
            }
            if (existingIdx >= 0) {
                updatedChildren[existingIdx] = replacement
            } else {
                updatedChildren.add(replacement)
            }
        }

        val updatedNodes = nodes.toMutableList()
        updatedNodes[targetCard.documentNodeIndex] = cardEntry.copy(children = updatedChildren)
        return copy(nodes = updatedNodes)
    }

    fun withAppendedBlankCard(timestampIso: String): MseSetDocument {
        // Strictly NO default card art: `image` is NOT populated and strictly resolves to null.
        val blankCard = MseLineNode.Entry(
            key = "card",
            isBlock = true,
            children = listOf(
                MseLineNode.Entry(key = "has styling", inlineValue = "false"),
                MseLineNode.Entry(key = "notes", inlineValue = ""),
                MseLineNode.Entry(key = "time created", inlineValue = timestampIso),
                MseLineNode.Entry(key = "time modified", inlineValue = timestampIso),
                MseLineNode.Entry(key = "name", inlineValue = ""),
                MseLineNode.Entry(key = "casting cost", inlineValue = ""),
                MseLineNode.Entry(key = "super type", inlineValue = ""),
                MseLineNode.Entry(key = "sub type", inlineValue = ""),
                MseLineNode.Entry(key = "rule text", inlineValue = ""),
                MseLineNode.Entry(key = "flavor text", inlineValue = ""),
                MseLineNode.Entry(key = "power", inlineValue = ""),
                MseLineNode.Entry(key = "toughness", inlineValue = ""),
                MseLineNode.Entry(key = "illustrator", inlineValue = ""),
            ),
        )
        return copy(nodes = nodes + blankCard)
    }

    fun withStylingFontOverride(stylesheetId: String, fontFamily: String): MseSetDocument {
        val updatedNodes = nodes.toMutableList()
        val stylingIdx = updatedNodes.indexOfFirst {
            it is MseLineNode.Entry && it.key == "styling" && it.isBlock
        }
        val fontEntry = MseLineNode.Entry(key = "custom font override", inlineValue = fontFamily)
        if (stylingIdx >= 0) {
            val stylingBlock = updatedNodes[stylingIdx] as MseLineNode.Entry
            val stylingChildren = stylingBlock.children.toMutableList()
            val sheetIdx = stylingChildren.indexOfFirst {
                it is MseLineNode.Entry && it.key == stylesheetId && it.isBlock
            }
            if (sheetIdx >= 0) {
                val sheetBlock = stylingChildren[sheetIdx] as MseLineNode.Entry
                val sheetChildren = sheetBlock.children.toMutableList()
                val fontIdx = sheetChildren.indexOfFirst {
                    it is MseLineNode.Entry && it.key == "custom font override"
                }
                if (fontIdx >= 0) {
                    sheetChildren[fontIdx] = fontEntry
                } else {
                    sheetChildren.add(fontEntry)
                }
                stylingChildren[sheetIdx] = sheetBlock.copy(children = sheetChildren)
            } else {
                stylingChildren.add(
                    MseLineNode.Entry(
                        key = stylesheetId,
                        isBlock = true,
                        children = listOf(fontEntry),
                    )
                )
            }
            updatedNodes[stylingIdx] = stylingBlock.copy(children = stylingChildren)
        } else {
            val insertAt = updatedNodes.indexOfFirst {
                it is MseLineNode.Entry && it.key == "card"
            }.let { if (it >= 0) it else updatedNodes.size }
            updatedNodes.add(
                insertAt,
                MseLineNode.Entry(
                    key = "styling",
                    isBlock = true,
                    children = listOf(
                        MseLineNode.Entry(
                            key = stylesheetId,
                            isBlock = true,
                            children = listOf(fontEntry),
                        )
                    ),
                ),
            )
        }
        return copy(nodes = updatedNodes)
    }

    companion object {
        val MULTILINE_CARD_FIELDS = setOf("rule text", "flavor text", "notes", "text")
    }
}

/**
 * Typed convenience view over a lossless `card:` block inside [MseSetDocument].
 * Strictly enforces `image` == null when no image filename is stored.
 */
data class MseCardView(
    val index: Int,
    val documentNodeIndex: Int,
    val entry: MseLineNode.Entry,
) {
    fun field(key: String): String =
        entry.children
            .filterIsInstance<MseLineNode.Entry>()
            .firstOrNull { it.key == key }
            ?.textValue
            ?: ""

    val name: String get() = field("name")
    val castingCost: String get() = field("casting cost")
    val superType: String get() = field("super type")
    val subType: String get() = field("sub type")
    val ruleText: String get() = field("rule text")
    val flavorText: String get() = field("flavor text")
    val power: String get() = field("power")
    val toughness: String get() = field("toughness")
    val illustrator: String get() = field("illustrator")

    /**
     * Strictly null when no artwork is embedded in the `.mse-set`.
     */
    val image: String?
        get() = field("image").trim().takeIf { it.isNotEmpty() }

    val allEditableFields: Map<String, String>
        get() = entry.children
            .filterIsInstance<MseLineNode.Entry>()
            .filter { !it.isBlock }
            .associate { it.key to it.textValue }
}

/**
 * Complete lossless in-memory `.mse-set` ZIP archive.
 */
data class MseSetArchive(
    val displayName: String,
    val document: MseSetDocument,
    val setEntryName: String = "set",
    val embeddedBlobs: Map<String, ByteArray> = emptyMap(),
    val sourceUri: String? = null,
) {
    fun getArtBytes(cardIndex: Int): ByteArray? {
        val imageName = document.cards.getOrNull(cardIndex)?.image ?: return null
        return embeddedBlobs[imageName]
    }
}

/**
 * Parsed `.mse-style` geometry & font descriptor.
 */
data class MseHitbox(
    val field: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int = 0,
    val fontFamily: String? = null,
)

data class MseStyleSpec(
    val stylesheetId: String,
    val game: String,
    val shortName: String,
    val fullName: String,
    val cardWidthPx: Int,
    val cardHeightPx: Int,
    val cardDpi: Int,
    val hitboxes: List<MseHitbox>,
    val referencedFonts: List<String>,
    val sourceLabel: String,
) {
    val artHitbox: MseHitbox?
        get() = hitboxes.firstOrNull { it.field == "image" || it.field == "art" }

    val artAspectRatio: Float
        get() {
            val box = artHitbox
            return if (box != null && box.width > 0f && box.height > 0f) {
                box.width / box.height
            } else {
                316f / 231f
            }
        }
}
