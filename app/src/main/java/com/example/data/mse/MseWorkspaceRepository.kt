package com.example.data.mse

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the active local `.mse-set` workspace archive and optional loaded `.mse-style` spec.
 * Persists changes losslessly to app internal storage (`filesDir/workspace/active.mse-set`)
 * and supports full Android SAF open/export.
 */
class MseWorkspaceRepository(private val context: Context) {

    private val workspaceDir: File
        get() = File(context.filesDir, "workspace").apply { mkdirs() }

    private val activeSetFile: File
        get() = File(workspaceDir, "active.mse-set")

    private val _archive = MutableStateFlow(loadOrCreateInitialArchive())
    val archive: StateFlow<MseSetArchive> = _archive.asStateFlow()

    private val _activeCardIndex = MutableStateFlow(0)
    val activeCardIndex: StateFlow<Int> = _activeCardIndex.asStateFlow()

    private val _styleSpec = MutableStateFlow(MseStyleParser.defaultStandardGeometrySpec())
    val styleSpec: StateFlow<MseStyleSpec> = _styleSpec.asStateFlow()

    private fun loadOrCreateInitialArchive(): MseSetArchive {
        if (activeSetFile.exists() && activeSetFile.length() > 0L) {
            runCatching {
                activeSetFile.inputStream().use { input ->
                    return MseSetParser.readArchive(
                        inputStream = input,
                        displayName = "active.mse-set",
                    )
                }
            }
        }
        val blank = MseSetParser.createBlankArchive(
            displayName = "untitled.mse-set",
            title = "Untitled Set",
        )
        persistArchiveSync(blank)
        return blank
    }

    private fun persistArchiveSync(archive: MseSetArchive) {
        runCatching {
            val tmp = File(workspaceDir, "active.mse-set.tmp")
            tmp.outputStream().use { out ->
                MseSetParser.writeArchive(archive, out)
            }
            if (tmp.exists()) {
                tmp.renameTo(activeSetFile)
            }
        }
    }

    suspend fun createNewBlankSet(
        title: String = "Untitled Set",
        game: String = "magic",
        stylesheet: String = "m15-altered",
    ): MseSetArchive = withContext(Dispatchers.IO) {
        val blank = MseSetParser.createBlankArchive(
            displayName = "${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}.mse-set",
            title = title,
            game = game,
            stylesheet = stylesheet,
        )
        persistArchiveSync(blank)
        _archive.value = blank
        _activeCardIndex.value = 0
        blank
    }

    suspend fun openSetFromUri(uri: Uri): Result<MseSetArchive> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "imported.mse-set"
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected .mse-set stream.")
            val loaded = stream.use { input ->
                MseSetParser.readArchive(
                    inputStream = input,
                    displayName = displayName,
                    sourceUri = uri.toString(),
                )
            }
            persistArchiveSync(loaded)
            _archive.value = loaded
            _activeCardIndex.value = 0
            loaded
        }
    }

    suspend fun exportSetToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Unable to open output stream for export.")
            out.use { stream ->
                MseSetParser.writeArchive(_archive.value, stream)
            }
        }
    }

    suspend fun loadStyleFromUri(uri: Uri): Result<MseStyleSpec> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "custom.mse-style"
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected .mse-style stream.")
            val parsed = stream.use { input ->
                MseStyleParser.parseStyleInput(input, sourceLabel = displayName)
            }
            _styleSpec.value = parsed
            parsed
        }
    }

    fun updateStyleSpecFromBridge(
        stylesheetId: String,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cardDpi: Int,
        hitboxes: List<MseHitbox>,
        missingFonts: List<String>,
    ) {
        val current = _styleSpec.value
        val mergedFonts = (current.referencedFonts + missingFonts + hitboxes.mapNotNull { it.fontFamily })
            .distinct()
        _styleSpec.value = current.copy(
            stylesheetId = stylesheetId.ifBlank { current.stylesheetId },
            cardWidthPx = if (cardWidthPx > 0) cardWidthPx else current.cardWidthPx,
            cardHeightPx = if (cardHeightPx > 0) cardHeightPx else current.cardHeightPx,
            cardDpi = if (cardDpi > 0) cardDpi else current.cardDpi,
            hitboxes = if (hitboxes.isNotEmpty()) hitboxes.sortedBy { it.zIndex } else current.hitboxes,
            referencedFonts = mergedFonts,
            sourceLabel = "MSE Bridge ($stylesheetId)",
        )
    }

    fun selectCard(index: Int) {
        val count = _archive.value.document.cards.size
        if (count == 0) {
            _activeCardIndex.value = 0
        } else {
            _activeCardIndex.value = index.coerceIn(0, count - 1)
        }
    }

    suspend fun addBlankCard(): Int = withContext(Dispatchers.IO) {
        val current = _archive.value
        val updatedDoc = current.document.withAppendedBlankCard(MseSetParser.currentMseTimestamp())
        val updated = current.copy(document = updatedDoc)
        persistArchiveSync(updated)
        _archive.value = updated
        val newIndex = (updatedDoc.cards.size - 1).coerceAtLeast(0)
        _activeCardIndex.value = newIndex
        newIndex
    }

    suspend fun updateCardField(cardIndex: Int, fieldKey: String, newValue: String?): MseSetArchive =
        withContext(Dispatchers.IO) {
            val current = _archive.value
            val updatedDoc = current.document
                .withCardFieldUpdated(cardIndex, fieldKey, newValue)
                .withCardFieldUpdated(cardIndex, "time modified", MseSetParser.currentMseTimestamp())
            val updated = current.copy(document = updatedDoc)
            persistArchiveSync(updated)
            _archive.value = updated
            updated
        }

    /**
     * Embeds user-cropped image PNG/JPEG bytes into the `.mse-set` ZIP archive and binds the card's `image` key.
     */
    suspend fun embedCardArt(cardIndex: Int, imageBytes: ByteArray): MseSetArchive =
        withContext(Dispatchers.IO) {
            val current = _archive.value
            val existingImageKey = current.document.cards.getOrNull(cardIndex)?.image
            val entryName = existingImageKey ?: nextAvailableImageEntryName(current.embeddedBlobs.keys)
            val updatedBlobs = current.embeddedBlobs.toMutableMap().apply {
                put(entryName, imageBytes)
            }
            val updatedDoc = current.document
                .withCardFieldUpdated(cardIndex, "image", entryName)
                .withCardFieldUpdated(cardIndex, "time modified", MseSetParser.currentMseTimestamp())
            val updated = current.copy(
                document = updatedDoc,
                embeddedBlobs = updatedBlobs,
            )
            persistArchiveSync(updated)
            _archive.value = updated
            updated
        }

    /**
     * Strictly returns the card's `image` field to `null`/blank and prunes the unreferenced blob.
     */
    suspend fun clearCardArt(cardIndex: Int): MseSetArchive = withContext(Dispatchers.IO) {
        val current = _archive.value
        val oldImageName = current.document.cards.getOrNull(cardIndex)?.image
        val updatedDoc = current.document
            .withCardFieldUpdated(cardIndex, "image", null)
            .withCardFieldUpdated(cardIndex, "time modified", MseSetParser.currentMseTimestamp())

        val stillReferenced = oldImageName != null &&
            updatedDoc.cards.any { it.image == oldImageName }

        val updatedBlobs = if (oldImageName != null && !stillReferenced) {
            current.embeddedBlobs - oldImageName
        } else {
            current.embeddedBlobs
        }

        val updated = current.copy(
            document = updatedDoc,
            embeddedBlobs = updatedBlobs,
        )
        persistArchiveSync(updated)
        _archive.value = updated
        updated
    }

    suspend fun applyFontOverride(fontFamily: String): MseSetArchive = withContext(Dispatchers.IO) {
        val current = _archive.value
        val sheetKey = "${current.document.game}-${current.document.stylesheet}"
        val updatedDoc = current.document.withStylingFontOverride(sheetKey, fontFamily)
        val updated = current.copy(document = updatedDoc)
        persistArchiveSync(updated)
        _archive.value = updated
        updated
    }

    suspend fun serializeCurrentArchiveBytes(): ByteArray = withContext(Dispatchers.IO) {
        MseSetParser.toArchiveBytes(_archive.value)
    }

    fun rawSetTextPreview(): String = MseSetParser.serializeSetText(_archive.value.document)

    private fun nextAvailableImageEntryName(existingNames: Set<String>): String {
        var i = 1
        while (existingNames.contains("image$i")) {
            i++
        }
        return "image$i"
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
