package com.example.data.mse

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@JsonClass(generateAdapter = true)
data class RecentSetRecord(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "game") val game: String,
    @Json(name = "stylesheet") val stylesheet: String,
    @Json(name = "card_count") val cardCount: Int,
    @Json(name = "local_file_path") val localFilePath: String,
    @Json(name = "source_uri") val sourceUri: String? = null,
    @Json(name = "last_opened_at") val lastOpenedAt: String,
)

/**
 * Manages open `.mse-set` archives, real persisted Recent Sets, unsaved-change tracking,
 * and active `.mse-style` geometry specs.
 *
 * Cold-start contract: `archive` starts strictly `null` so the app launches into the authentic
 * Magic Set Editor Welcome Window (never cold-starting into an Untitled Set wireframe).
 */
class MseWorkspaceRepository(
    private val context: Context,
    moshi: Moshi,
) {
    private val setsDir: File
        get() = File(context.filesDir, "sets").apply { mkdirs() }

    private val recentSetsFile: File
        get() = File(context.filesDir, "recent_sets.json")

    private val recentListAdapter = moshi.adapter<List<RecentSetRecord>>(
        Types.newParameterizedType(List::class.java, RecentSetRecord::class.java)
    ).indent("  ")

    private val _archive = MutableStateFlow<MseSetArchive?>(null)
    val archive: StateFlow<MseSetArchive?> = _archive.asStateFlow()

    private val _activeCardIndex = MutableStateFlow(0)
    val activeCardIndex: StateFlow<Int> = _activeCardIndex.asStateFlow()

    private val _styleSpec = MutableStateFlow(MseStyleParser.defaultStandardGeometrySpec())
    val styleSpec: StateFlow<MseStyleSpec> = _styleSpec.asStateFlow()

    private val _recentSets = MutableStateFlow<List<RecentSetRecord>>(loadRecentSetsSync())
    val recentSets: StateFlow<List<RecentSetRecord>> = _recentSets.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private fun loadRecentSetsSync(): List<RecentSetRecord> {
        if (!recentSetsFile.exists() || recentSetsFile.length() == 0L) return emptyList()
        return runCatching {
            val loaded = recentListAdapter.fromJson(recentSetsFile.readText()).orEmpty()
            // Keep only entries whose real local `.mse-set` file exists
            loaded.filter { File(it.localFilePath).exists() }
        }.getOrDefault(emptyList())
    }

    private fun saveRecentSetsSync(list: List<RecentSetRecord>) {
        runCatching {
            recentSetsFile.writeText(recentListAdapter.toJson(list))
        }
        _recentSets.value = list
    }

    private fun recordInRecentSets(archive: MseSetArchive) {
        val path = archive.localFilePath ?: return
        val now = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date())
        val record = RecentSetRecord(
            id = archive.id,
            title = archive.document.setTitle,
            displayName = archive.displayName,
            game = archive.document.game,
            stylesheet = archive.document.stylesheet,
            cardCount = archive.document.cards.size,
            localFilePath = path,
            sourceUri = archive.sourceUri,
            lastOpenedAt = now,
        )
        val updated = listOf(record) + _recentSets.value.filterNot {
            it.id == archive.id || it.localFilePath == path
        }
        saveRecentSetsSync(updated.take(12))
    }

    private fun writeArchiveToLocalFile(archive: MseSetArchive): MseSetArchive {
        val targetFile = archive.localFilePath?.let(::File)
            ?: File(setsDir, "${archive.id}.mse-set")
        val tmp = File(setsDir, "${archive.id}.tmp")
        tmp.outputStream().use { out ->
            MseSetParser.writeArchive(archive, out)
        }
        if (tmp.exists()) {
            tmp.renameTo(targetFile)
        }
        return archive.copy(localFilePath = targetFile.absolutePath)
    }

    suspend fun createNewBlankSet(
        title: String,
        game: String,
        stylesheet: String,
        styleOption: MseStyleOption? = null,
    ): MseSetArchive = withContext(Dispatchers.IO) {
        val setId = "set_${UUID.randomUUID().toString().take(8)}"
        val safeTitle = title.trim().ifEmpty { "Untitled Set" }
        val fileName = "${safeTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "untitled" }}.mse-set"
        val blank = MseSetParser.createBlankArchive(
            displayName = fileName,
            title = safeTitle,
            game = game,
            stylesheet = stylesheet,
        ).copy(id = setId)

        val persisted = writeArchiveToLocalFile(blank)
        _archive.value = persisted
        _activeCardIndex.value = 0
        _hasUnsavedChanges.value = false
        recordInRecentSets(persisted)

        if (styleOption != null) {
            _styleSpec.value = _styleSpec.value.copy(
                stylesheetId = styleOption.stylesheetId,
                game = styleOption.gameId,
                shortName = styleOption.displayName,
                fullName = "${MseTemplateCatalog.displayGameName(styleOption.gameId)} — ${styleOption.displayName}",
                cardWidthPx = styleOption.cardWidthPx,
                cardHeightPx = styleOption.cardHeightPx,
                cardDpi = styleOption.cardDpi,
                sourceLabel = styleOption.packSplit,
            )
        }
        persisted
    }

    suspend fun openSetFromUri(uri: Uri): Result<MseSetArchive> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "imported.mse-set"
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected .mse-set stream.")
            val setId = "set_${UUID.randomUUID().toString().take(8)}"
            val loaded = stream.use { input ->
                MseSetParser.readArchive(
                    inputStream = input,
                    displayName = displayName,
                    sourceUri = uri.toString(),
                )
            }.copy(id = setId)

            val persisted = writeArchiveToLocalFile(loaded)
            _archive.value = persisted
            _activeCardIndex.value = 0
            _hasUnsavedChanges.value = false
            recordInRecentSets(persisted)
            persisted
        }
    }

    suspend fun openRecentSet(record: RecentSetRecord): Result<MseSetArchive> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(record.localFilePath)
                if (!file.exists()) {
                    saveRecentSetsSync(_recentSets.value.filterNot { it.id == record.id })
                    throw IllegalStateException("Saved set file no longer exists on device.")
                }
                val loaded = file.inputStream().use { input ->
                    MseSetParser.readArchive(
                        inputStream = input,
                        displayName = record.displayName,
                        sourceUri = record.sourceUri,
                    )
                }.copy(
                    id = record.id,
                    localFilePath = file.absolutePath,
                )
                _archive.value = loaded
                _activeCardIndex.value = 0
                _hasUnsavedChanges.value = false
                recordInRecentSets(loaded)
                loaded
            }
        }

    suspend fun saveActiveSet(): Result<MseSetArchive> = withContext(Dispatchers.IO) {
        runCatching {
            val current = _archive.value ?: throw IllegalStateException("No active set to save.")
            val persisted = writeArchiveToLocalFile(current)
            // If opened from a writable SAF URI, attempt write-back safely
            persisted.sourceUri?.let { uriStr ->
                runCatching {
                    context.contentResolver.openOutputStream(Uri.parse(uriStr), "wt")?.use { out ->
                        MseSetParser.writeArchive(persisted, out)
                    }
                }
            }
            _archive.value = persisted
            _hasUnsavedChanges.value = false
            recordInRecentSets(persisted)
            persisted
        }
    }

    suspend fun exportSetToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = _archive.value ?: throw IllegalStateException("No active set open.")
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Unable to open output stream for export.")
            out.use { stream ->
                MseSetParser.writeArchive(current, stream)
            }
            val updated = writeArchiveToLocalFile(current.copy(sourceUri = uri.toString()))
            _archive.value = updated
            _hasUnsavedChanges.value = false
            recordInRecentSets(updated)
        }
    }

    fun closeActiveSet() {
        _archive.value = null
        _activeCardIndex.value = 0
        _hasUnsavedChanges.value = false
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

    suspend fun switchStylesheet(option: MseStyleOption): MseSetArchive? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
        val updatedDoc = current.document.withTopLevelScalar("stylesheet", option.stylesheetId)
        val updated = current.copy(document = updatedDoc)
        _archive.value = updated
        _hasUnsavedChanges.value = true
        _styleSpec.value = _styleSpec.value.copy(
            stylesheetId = option.stylesheetId,
            game = option.gameId,
            shortName = option.displayName,
            fullName = "${MseTemplateCatalog.displayGameName(option.gameId)} — ${option.displayName}",
            cardWidthPx = option.cardWidthPx,
            cardHeightPx = option.cardHeightPx,
            cardDpi = option.cardDpi,
            sourceLabel = option.packSplit,
        )
        updated
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
        val count = _archive.value?.document?.cards?.size ?: 0
        _activeCardIndex.value = if (count == 0) 0 else index.coerceIn(0, count - 1)
    }

    suspend fun addBlankCard(): Int = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext 0
        val updatedDoc = current.document.withAppendedBlankCard(MseSetParser.currentMseTimestamp())
        val updated = current.copy(document = updatedDoc)
        _archive.value = updated
        _hasUnsavedChanges.value = true
        val newIndex = (updatedDoc.cards.size - 1).coerceAtLeast(0)
        _activeCardIndex.value = newIndex
        newIndex
    }

    suspend fun duplicateCard(cardIndex: Int): Int = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext 0
        val updatedDoc = current.document.withDuplicatedCard(cardIndex, MseSetParser.currentMseTimestamp())
        _archive.value = current.copy(document = updatedDoc)
        _hasUnsavedChanges.value = true
        val newIdx = (cardIndex + 1).coerceIn(0, (updatedDoc.cards.size - 1).coerceAtLeast(0))
        _activeCardIndex.value = newIdx
        newIdx
    }

    suspend fun deleteCard(cardIndex: Int): Int = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext 0
        val afterDelete = current.document.withDeletedCard(cardIndex)
        val finalDoc = if (afterDelete.cards.isEmpty()) {
            afterDelete.withAppendedBlankCard(MseSetParser.currentMseTimestamp())
        } else {
            afterDelete
        }
        _archive.value = current.copy(document = finalDoc)
        _hasUnsavedChanges.value = true
        val newIdx = cardIndex.coerceIn(0, (finalDoc.cards.size - 1).coerceAtLeast(0))
        _activeCardIndex.value = newIdx
        newIdx
    }

    suspend fun updateCardField(cardIndex: Int, fieldKey: String, newValue: String?): MseSetArchive? =
        withContext(Dispatchers.IO) {
            val current = _archive.value ?: return@withContext null
            val updatedDoc = current.document
                .withCardFieldUpdated(cardIndex, fieldKey, newValue)
                .withCardFieldUpdated(cardIndex, "time modified", MseSetParser.currentMseTimestamp())
            val updated = current.copy(document = updatedDoc)
            _archive.value = updated
            _hasUnsavedChanges.value = true
            updated
        }

    suspend fun updateSetInfo(info: MseSetInfo): MseSetArchive? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
        val updated = current.copy(document = current.document.withSetInfoUpdated(info))
        _archive.value = updated
        _hasUnsavedChanges.value = true
        updated
    }

    suspend fun addKeyword(keyword: String, match: String, reminder: String, rules: String) =
        withContext(Dispatchers.IO) {
            val current = _archive.value ?: return@withContext
            val updatedDoc = current.document.withAddedKeyword(keyword, match, reminder, rules)
            _archive.value = current.copy(document = updatedDoc)
            _hasUnsavedChanges.value = true
        }

    suspend fun updateKeyword(index: Int, keyword: String, match: String, reminder: String, rules: String) =
        withContext(Dispatchers.IO) {
            val current = _archive.value ?: return@withContext
            val updatedDoc = current.document.withUpdatedKeyword(index, keyword, match, reminder, rules)
            _archive.value = current.copy(document = updatedDoc)
            _hasUnsavedChanges.value = true
        }

    suspend fun deleteKeyword(index: Int) = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext
        val updatedDoc = current.document.withDeletedKeyword(index)
        _archive.value = current.copy(document = updatedDoc)
        _hasUnsavedChanges.value = true
    }

    /**
     * Embeds user-cropped image PNG bytes into the `.mse-set` ZIP archive and binds the card's `image` key.
     */
    suspend fun embedCardArt(cardIndex: Int, imageBytes: ByteArray): MseSetArchive? =
        withContext(Dispatchers.IO) {
            val current = _archive.value ?: return@withContext null
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
            _archive.value = updated
            _hasUnsavedChanges.value = true
            updated
        }

    /**
     * Strictly returns the card's `image` field to `null`/blank and prunes the unreferenced blob.
     */
    suspend fun clearCardArt(cardIndex: Int): MseSetArchive? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
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
        _archive.value = updated
        _hasUnsavedChanges.value = true
        updated
    }

    suspend fun applyFontOverride(fontFamily: String): MseSetArchive? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
        val sheetKey = "${current.document.game}-${current.document.stylesheet}"
        val updatedDoc = current.document.withStylingFontOverride(sheetKey, fontFamily)
        val updated = current.copy(document = updatedDoc)
        _archive.value = updated
        _hasUnsavedChanges.value = true
        updated
    }

    suspend fun serializeCurrentArchiveBytes(): ByteArray? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
        MseSetParser.toArchiveBytes(current)
    }

    fun rawSetTextPreview(): String =
        _archive.value?.let { MseSetParser.serializeSetText(it.document) }.orEmpty()

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
