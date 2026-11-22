package com.example.data.mse

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Real, lossless Magic Set Editor 2 `.mse-set` ZIP archive and tab-indented `set` text parser/writer.
 *
 * Guarantees:
 * 1. Exact tab indentation & unknown key preservation across round-trip reads/writes.
 * 2. Preserves every embedded ZIP binary entry unmodified unless explicitly updated/cleared by the user.
 * 3. Newly created sets & cards initialize `image` strictly to null/absent (zero default card art).
 */
object MseSetParser {

    private val KNOWN_TOP_LEVEL_BLOCKS = setOf(
        "set info",
        "styling",
        "card",
        "keyword",
        "version control",
        "pack type",
    )

    private val MULTILINE_TEXT_KEYS = setOf(
        "rule text",
        "flavor text",
        "text",
        "notes",
        "description",
        "reminder",
        "script",
        "copyright",
        "apprentice code",
    )

    private val KEY_DECLARATION_REGEX = Regex("^([A-Za-z0-9_\\- ./]+?):(?: (.*)|)$")

    fun parseSetText(rawText: String): MseSetDocument {
        val hadBom = rawText.startsWith("\uFEFF")
        val withoutBom = if (hadBom) rawText.substring(1) else rawText
        val lineSep = if (withoutBom.contains("\r\n")) "\r\n" else "\n"
        val normalizedLines = withoutBom
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .let { list ->
                if (list.isNotEmpty() && list.last().isEmpty()) list.dropLast(1) else list
            }

        val cursor = LineCursor(normalizedLines)
        val nodes = parseNodesAtDepth(cursor, depth = 0, parentKey = null)
        return MseSetDocument(
            nodes = nodes,
            hadUtf8Bom = hadBom,
            lineSeparator = lineSep,
        )
    }

    fun serializeSetText(document: MseSetDocument): String {
        val sb = StringBuilder()
        if (document.hadUtf8Bom) {
            sb.append('\uFEFF')
        }
        writeNodes(sb, document.nodes, depth = 0, lineSep = document.lineSeparator)
        return sb.toString()
    }

    fun readArchive(
        inputStream: InputStream,
        displayName: String,
        sourceUri: String? = null,
    ): MseSetArchive {
        var setEntryName = "set"
        var setTextBytes: ByteArray? = null
        val blobs = linkedMapOf<String, ByteArray>()

        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zis.readBytes()
                    if (entry.name == "set" || entry.name.endsWith("/set")) {
                        setEntryName = entry.name
                        setTextBytes = bytes
                    } else {
                        blobs[entry.name] = bytes
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val rawText = setTextBytes?.toString(StandardCharsets.UTF_8)
            ?: throw IllegalArgumentException("Invalid .mse-set archive: missing 'set' file entry.")

        return MseSetArchive(
            displayName = displayName,
            document = parseSetText(rawText),
            setEntryName = setEntryName,
            embeddedBlobs = blobs,
            sourceUri = sourceUri,
        )
    }

    fun writeArchive(archive: MseSetArchive, outputStream: OutputStream) {
        val setBytes = serializeSetText(archive.document).toByteArray(StandardCharsets.UTF_8)
        ZipOutputStream(outputStream.buffered()).use { zos ->
            zos.putNextEntry(ZipEntry(archive.setEntryName))
            zos.write(setBytes)
            zos.closeEntry()

            archive.embeddedBlobs.forEach { (name, bytes) ->
                if (name != archive.setEntryName) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    fun toArchiveBytes(archive: MseSetArchive): ByteArray {
        val baos = ByteArrayOutputStream()
        writeArchive(archive, baos)
        return baos.toByteArray()
    }

    /**
     * Creates a valid blank `.mse-set` with one empty card and strictly NO default card art (`image` absent/null).
     */
    fun createBlankArchive(
        displayName: String = "untitled.mse-set",
        title: String = "Untitled Set",
        game: String = "magic",
        stylesheet: String = "m15-altered",
    ): MseSetArchive {
        val now = currentMseTimestamp()
        val nodes = listOf(
            MseLineNode.Entry(key = "mse version", inlineValue = "2.0.0"),
            MseLineNode.Entry(key = "game", inlineValue = game),
            MseLineNode.Entry(key = "stylesheet", inlineValue = stylesheet),
            MseLineNode.Entry(
                key = "set info",
                isBlock = true,
                children = listOf(
                    MseLineNode.Entry(key = "title", inlineValue = title),
                    MseLineNode.Entry(key = "set code", inlineValue = ""),
                ),
            ),
            MseLineNode.Entry(
                key = "styling",
                isBlock = true,
                children = listOf(
                    MseLineNode.Entry(
                        key = "$game-$stylesheet",
                        isBlock = true,
                        children = emptyList(),
                    )
                ),
            ),
            // Strictly zero default card art (`image` key omitted -> resolves to null)
            MseLineNode.Entry(
                key = "card",
                isBlock = true,
                children = listOf(
                    MseLineNode.Entry(key = "has styling", inlineValue = "false"),
                    MseLineNode.Entry(key = "notes", inlineValue = ""),
                    MseLineNode.Entry(key = "time created", inlineValue = now),
                    MseLineNode.Entry(key = "time modified", inlineValue = now),
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
            ),
        )
        return MseSetArchive(
            displayName = displayName,
            document = MseSetDocument(nodes = nodes),
            embeddedBlobs = emptyMap(),
        )
    }

    fun currentMseTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    private class LineCursor(private val lines: List<String>) {
        var index: Int = 0
        fun hasNext(): Boolean = index < lines.size
        fun peek(): String = lines[index]
        fun consume(): String = lines[index++]
    }

    private fun countLeadingTabs(line: String): Int {
        var count = 0
        while (count < line.length && line[count] == '\t') {
            count++
        }
        return count
    }

    private fun parseNodesAtDepth(
        cursor: LineCursor,
        depth: Int,
        parentKey: String?,
    ): List<MseLineNode> {
        val result = mutableListOf<MseLineNode>()
        while (cursor.hasNext()) {
            val line = cursor.peek()
            if (line.isBlank() || line.trimStart('\t').startsWith("#")) {
                val tabs = countLeadingTabs(line)
                if (line.isNotBlank() && tabs < depth) break
                result.add(MseLineNode.CommentOrBlank(cursor.consume()))
                continue
            }

            val tabs = countLeadingTabs(line)
            if (tabs < depth) break
            if (tabs > depth) {
                // Unexpected extra indentation: preserve verbatim as raw line to stay lossless
                result.add(MseLineNode.CommentOrBlank(cursor.consume()))
                continue
            }

            val content = line.substring(depth)
            val match = KEY_DECLARATION_REGEX.matchEntire(content)
            if (match == null) {
                result.add(MseLineNode.CommentOrBlank(cursor.consume()))
                continue
            }

            cursor.consume()
            val key = match.groupValues[1]
            val inlineVal = if (match.groups[2] != null) match.groupValues[2] else null

            // Check if subsequent lines are deeper indented (depth + 1 or greater)
            if (inlineVal == null && hasDeeperContent(cursor, depth)) {
                val isBlock = shouldParseAsStructuredBlock(key, depth, parentKey, cursor)
                if (isBlock) {
                    val children = parseNodesAtDepth(cursor, depth + 1, parentKey = key)
                    result.add(
                        MseLineNode.Entry(
                            key = key,
                            inlineValue = null,
                            children = children,
                            isBlock = true,
                        )
                    )
                } else {
                    val multiline = consumeMultilineValue(cursor, depth + 1)
                    result.add(
                        MseLineNode.Entry(
                            key = key,
                            inlineValue = null,
                            multilineValue = multiline,
                            isBlock = false,
                        )
                    )
                }
            } else {
                val isEmptyBlock = inlineVal == null && (
                    (depth == 0 && key in KNOWN_TOP_LEVEL_BLOCKS) ||
                        (depth == 1 && parentKey == "styling")
                    )
                result.add(
                    MseLineNode.Entry(
                        key = key,
                        inlineValue = inlineVal,
                        isBlock = isEmptyBlock,
                    )
                )
            }
        }
        return result
    }

    private fun hasDeeperContent(cursor: LineCursor, currentDepth: Int): Boolean {
        if (!cursor.hasNext()) return false
        val next = cursor.peek()
        if (next.isEmpty()) return false
        return countLeadingTabs(next) > currentDepth
    }

    private fun shouldParseAsStructuredBlock(
        key: String,
        depth: Int,
        parentKey: String?,
        cursor: LineCursor,
    ): Boolean {
        if (key in MULTILINE_TEXT_KEYS) return false
        if (depth == 0 && key in KNOWN_TOP_LEVEL_BLOCKS) return true
        if (depth == 1 && parentKey == "styling") return true
        if (depth == 1 && parentKey == "card" && key == "styling data") return true
        if (!cursor.hasNext()) return false

        val firstChild = cursor.peek()
        val tabs = countLeadingTabs(firstChild)
        if (tabs != depth + 1) return false
        val trimmed = firstChild.substring(tabs)
        val match = KEY_DECLARATION_REGEX.matchEntire(trimmed) ?: return false
        val candidateKey = match.groupValues[1]
        // MSE structured keys are lowercase words/identifiers, not capitalized card text sentences
        return candidateKey.all { it.isLowerCase() || it.isDigit() || it in setOf(' ', '_', '-', '.') }
    }

    private fun consumeMultilineValue(cursor: LineCursor, minDepth: Int): List<String> {
        val lines = mutableListOf<String>()
        while (cursor.hasNext()) {
            val raw = cursor.peek()
            val tabs = countLeadingTabs(raw)
            if (tabs < minDepth) break
            cursor.consume()
            lines.add(raw.substring(minDepth))
        }
        return lines
    }

    private fun writeNodes(
        sb: StringBuilder,
        nodes: List<MseLineNode>,
        depth: Int,
        lineSep: String,
    ) {
        val indent = "\t".repeat(depth)
        for (node in nodes) {
            when (node) {
                is MseLineNode.CommentOrBlank -> {
                    sb.append(node.rawLine).append(lineSep)
                }

                is MseLineNode.Entry -> {
                    when {
                        node.isBlock -> {
                            sb.append(indent).append(node.key).append(':').append(lineSep)
                            writeNodes(sb, node.children, depth + 1, lineSep)
                        }

                        node.multilineValue.isNotEmpty() -> {
                            sb.append(indent).append(node.key).append(':').append(lineSep)
                            val childIndent = "\t".repeat(depth + 1)
                            for (line in node.multilineValue) {
                                sb.append(childIndent).append(line).append(lineSep)
                            }
                        }

                        node.inlineValue != null -> {
                            sb.append(indent)
                                .append(node.key)
                                .append(": ")
                                .append(node.inlineValue)
                                .append(lineSep)
                        }

                        else -> {
                            sb.append(indent).append(node.key).append(':').append(lineSep)
                        }
                    }
                }
            }
        }
    }
}
