package com.example.data.mse

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.comparison.CardOrigin
import com.example.domain.comparison.ComparableCardRecord
import com.example.domain.comparison.ComparableSet
import com.example.domain.comparison.SetComparisonEngine
import com.example.domain.comparison.SplitOffKind
import com.example.domain.comparison.SplitOffSetManifest
import com.example.domain.frameworks.FrameworkMseScaffoldGenerator
import com.example.domain.frameworks.UniversalCardFramework
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

private data class WorkspaceSnapshot(
    val archive: MseSetArchive,
    val activeCardIndex: Int,
)

/**
 * Manages open `.mse-set` archives, real persisted Recent Sets, real Undo/Redo edit history,
 * unsaved-change tracking, and active `.mse-style` geometry specs.
 *
 * Cold-start contract: `archive` starts strictly `null` so the app launches into the authentic
 * Magic Set Editor Welcome Window.
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

    private val splitOffManifestAdapter = moshi.adapter(SplitOffSetManifest::class.java).indent("  ")

    private val _archive = MutableStateFlow<MseSetArchive?>(null)
    val archive: StateFlow<MseSetArchive?> = _archive.asStateFlow()

    private val _splitOffManifest = MutableStateFlow<SplitOffSetManifest?>(null)
    val splitOffManifest: StateFlow<SplitOffSetManifest?> = _splitOffManifest.asStateFlow()

    private val _activeCardIndex = MutableStateFlow(0)
    val activeCardIndex: StateFlow<Int> = _activeCardIndex.asStateFlow()

    private val _styleSpec = MutableStateFlow(MseStyleParser.defaultStandardGeometrySpec())
    val styleSpec: StateFlow<MseStyleSpec> = _styleSpec.asStateFlow()

    private val _recentSets = MutableStateFlow<List<RecentSetRecord>>(loadRecentSetsSync())
    val recentSets: StateFlow<List<RecentSetRecord>> = _recentSets.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private fun extractSplitOffManifest(archive: MseSetArchive?): SplitOffSetManifest? {
        val bytes = archive?.embeddedBlobs?.get(SetComparisonEngine.SPLIT_OFF_MANIFEST_BLOB_NAME)
            ?: return null
        return runCatching {
            splitOffManifestAdapter.fromJson(bytes.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun setActiveArchiveInternal(archive: MseSetArchive?) {
        _archive.value = archive
        _splitOffManifest.value = extractSplitOffManifest(archive)
    }

    fun isCardProtectedCanon(cardIndex: Int): Boolean {
        val manifest = _splitOffManifest.value ?: return false
        if (!manifest.preserveAllOriginalOfficialCards) return false
        val card = _archive.value?.document?.cards?.getOrNull(cardIndex) ?: return false
        return card.isCanonOriginal
    }

    private val undoStack = ArrayDeque<WorkspaceSnapshot>()
    private val redoStack = ArrayDeque<WorkspaceSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun pushUndoSnapshot() {
        val current = _archive.value ?: return
        undoStack.addLast(WorkspaceSnapshot(current, _activeCardIndex.value))
        while (undoStack.size > MAX_HISTORY_DEPTH) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        syncHistoryFlags()
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        syncHistoryFlags()
    }

    private fun syncHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo(): Boolean {
        val current = _archive.value ?: return false
        if (undoStack.isEmpty()) return false
        val previous = undoStack.removeLast()
        redoStack.addLast(WorkspaceSnapshot(current, _activeCardIndex.value))
        setActiveArchiveInternal(previous.archive)
        val maxIdx = (previous.archive.document.cards.size - 1).coerceAtLeast(0)
        _activeCardIndex.value = previous.activeCardIndex.coerceIn(0, maxIdx)
        _hasUnsavedChanges.value = true
        syncHistoryFlags()
        return true
    }

    fun redo(): Boolean {
        val current = _archive.value ?: return false
        if (redoStack.isEmpty()) return false
        val next = redoStack.removeLast()
        undoStack.addLast(WorkspaceSnapshot(current, _activeCardIndex.value))
        setActiveArchiveInternal(next.archive)
        val maxIdx = (next.archive.document.cards.size - 1).coerceAtLeast(0)
        _activeCardIndex.value = next.activeCardIndex.coerceIn(0, maxIdx)
        _hasUnsavedChanges.value = true
        syncHistoryFlags()
        return true
    }

    private fun loadRecentSetsSync(): List<RecentSetRecord> {
        if (!recentSetsFile.exists() || recentSetsFile.length() == 0L) return emptyList()
        return runCatching {
            val loaded = recentListAdapter.fromJson(recentSetsFile.readText()).orEmpty()
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
        discoveredStyle: DiscoveredStyle? = null,
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
        clearHistory()
        setActiveArchiveInternal(persisted)
        _activeCardIndex.value = 0
        _hasUnsavedChanges.value = false
        recordInRecentSets(persisted)

        if (discoveredStyle != null) {
            applyDiscoveredStyleSpec(discoveredStyle)
        }
        persisted
    }

    suspend fun createNewSetFromFramework(
        title: String,
        framework: UniversalCardFramework,
        matchedDiscoveredStyle: DiscoveredStyle? = null,
    ): MseSetArchive = withContext(Dispatchers.IO) {
        val gameSlug = FrameworkMseScaffoldGenerator.sanitizeMseId(framework.upstreamMseGameId ?: framework.id)
        val styleSlug = matchedDiscoveredStyle?.stylesheetId
            ?: FrameworkMseScaffoldGenerator.sanitizeMseId(framework.upstreamMseStyleId ?: "standard")
        val setId = "set_${UUID.randomUUID().toString().take(8)}"
        val safeTitle = title.trim().ifEmpty { "${framework.effectiveShortName} Custom Set" }
        val fileName = "${safeTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "untitled" }}.mse-set"
        val blank = MseSetParser.createBlankArchive(
            displayName = fileName,
            title = safeTitle,
            game = gameSlug,
            stylesheet = styleSlug,
        ).copy(id = setId)

        val persisted = writeArchiveToLocalFile(blank)
        clearHistory()
        setActiveArchiveInternal(persisted)
        _activeCardIndex.value = 0
        _hasUnsavedChanges.value = false
        recordInRecentSets(persisted)

        if (matchedDiscoveredStyle != null) {
            applyDiscoveredStyleSpec(matchedDiscoveredStyle)
        } else {
            _styleSpec.value = FrameworkMseScaffoldGenerator.toStructuredStyleSpec(framework)
        }
        persisted
    }

    /**
     * Forks any pulled Official Set into a Fan-Fiction / Custom Split-Off `.mse-set`.
     *
     * MANDATORY CANON LORE PRESERVATION INVARIANT:
     * - Copies 100% of [officialSet.cards] intact as `CANON_ORIGINAL`.
     * - Strictly leaves every card's `image` == null (zero copyrighted official art copied).
     * - Embeds `split_off_manifest.json` losslessly inside `.mse-set` ZIP (`preserveAllOriginalOfficialCards = true`, `upstreamSourceLock = false`).
     */
    suspend fun createFanFictionSplitOffSet(
        officialSet: ComparableSet,
        splitOffTitle: String,
        splitOffKind: SplitOffKind,
        divergenceNotes: String,
        targetFramework: UniversalCardFramework? = null,
        discoveredStyle: DiscoveredStyle? = null,
    ): MseSetArchive = withContext(Dispatchers.IO) {
        val setId = "splitoff_${UUID.randomUUID().toString().take(8)}"
        val safeTitle = splitOffTitle.trim().ifEmpty { "${officialSet.setName} — ${splitOffKind.displayLabel}" }
        val fileName = "${safeTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "splitoff" }}.mse-set"
        val gameSlug = discoveredStyle?.gameId
            ?: targetFramework?.let { FrameworkMseScaffoldGenerator.sanitizeMseId(it.upstreamMseGameId ?: it.id) }
            ?: officialSet.gameId.ifBlank { "magic" }
        val styleSlug = discoveredStyle?.stylesheetId
            ?: targetFramework?.let { FrameworkMseScaffoldGenerator.sanitizeMseId(it.upstreamMseStyleId ?: "standard") }
            ?: "m15-altered"
        val nowIso = MseSetParser.currentMseTimestamp()

        val baseArchive = MseSetParser.createBlankArchive(
            displayName = fileName,
            title = safeTitle,
            game = gameSlug,
            stylesheet = styleSlug,
        )
        // Replace the single empty starter card with 100% preserved CANON_ORIGINAL cards
        var doc = if (officialSet.cards.isNotEmpty()) {
            baseArchive.document.withDeletedCard(0)
        } else {
            baseArchive.document
        }
        doc = doc.withSetInfoUpdated(
            doc.setInfo.copy(
                title = safeTitle,
                description = buildString {
                    append("Fan-Fiction Split-Off from: ${officialSet.setName} (${officialSet.cards.size}/${officialSet.cards.size} Canon Cards Preserved).")
                    if (divergenceNotes.isNotBlank()) {
                        append("\n\nDivergence Notes:\n")
                        append(divergenceNotes.trim())
                    }
                },
            )
        )
        officialSet.cards.forEach { officialCard ->
            val fields = SetComparisonEngine.createReferenceBaselineFields(
                officialCard = officialCard,
                origin = CardOrigin.CANON_ORIGINAL,
            )
            doc = doc.withAppendedCardFromFields(
                fields = fields,
                timestampIso = nowIso,
                preserveExistingImageField = false,
            )
        }

        val manifest = SplitOffSetManifest(
            splitOffId = setId,
            splitOffTitle = safeTitle,
            splitOffKind = splitOffKind.wireValue,
            parentOfficialSetId = officialSet.setId,
            parentOfficialSetName = officialSet.setName,
            parentOfficialSource = officialSet.source,
            parentGameId = officialSet.gameId,
            preserveAllOriginalOfficialCards = true,
            preservedCanonCardCount = officialSet.cards.size,
            canonDivergenceNotes = divergenceNotes.trim(),
            upstreamSourceLock = false,
            frameworkId = targetFramework?.id,
            createdAt = nowIso,
        )
        val manifestBytes = splitOffManifestAdapter.toJson(manifest).toByteArray(Charsets.UTF_8)
        val archiveWithManifest = baseArchive.copy(
            id = setId,
            document = doc,
            embeddedBlobs = baseArchive.embeddedBlobs + (SetComparisonEngine.SPLIT_OFF_MANIFEST_BLOB_NAME to manifestBytes),
        )

        val persisted = writeArchiveToLocalFile(archiveWithManifest)
        clearHistory()
        setActiveArchiveInternal(persisted)
        _activeCardIndex.value = 0
        _hasUnsavedChanges.value = false
        recordInRecentSets(persisted)

        when {
            discoveredStyle != null -> applyDiscoveredStyleSpec(discoveredStyle)
            targetFramework != null -> _styleSpec.value = FrameworkMseScaffoldGenerator.toStructuredStyleSpec(targetFramework)
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
            clearHistory()
            setActiveArchiveInternal(persisted)
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
                clearHistory()
                setActiveArchiveInternal(loaded)
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
        clearHistory()
        setActiveArchiveInternal(null)
        _activeCardIndex.value = 0
        _hasUnsavedChanges.value = false
    }

    fun applyFrameworkStyleSpec(framework: UniversalCardFramework) {
        _styleSpec.value = FrameworkMseScaffoldGenerator.toStructuredStyleSpec(framework)
    }

    suspend fun loadStyleFromUri(uri: Uri): Result<MseStyleSpec> =
        loadStyleWithBytesFromUri(uri).map { it.first }

    suspend fun loadStyleWithBytesFromUri(uri: Uri): Result<Pair<MseStyleSpec, ByteArray>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = resolveDisplayName(uri) ?: "custom.mse-style"
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open selected .mse-style stream.")
                val rawBytes = stream.use { it.readBytes() }
                val parsed = rawBytes.inputStream().use { input ->
                    MseStyleParser.parseStyleInput(input, sourceLabel = displayName)
                }
                _styleSpec.value = parsed
                parsed to rawBytes
            }
        }

    fun applyDiscoveredStyleSpec(style: DiscoveredStyle) {
        val spec = style.toStyleSpec()
        _styleSpec.value = if (spec.hitboxes.isNotEmpty()) {
            spec
        } else {
            // Preserve valid parsed dimensions while keeping fallback geometry only for matching game
            spec.copy(
                hitboxes = if (style.gameId.equals("magic", ignoreCase = true)) {
                    scaleHitboxes(
                        MseStyleParser.defaultStandardGeometrySpec(),
                        targetW = style.cardWidthPx,
                        targetH = style.cardHeightPx,
                    )
                } else {
                    emptyList()
                }
            )
        }
    }

    suspend fun switchStylesheet(style: DiscoveredStyle): MseSetArchive? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
        pushUndoSnapshot()
        val updatedDoc = current.document.withTopLevelScalar("stylesheet", style.stylesheetId)
        val updated = current.copy(document = updatedDoc)
        _archive.value = updated
        _hasUnsavedChanges.value = true
        applyDiscoveredStyleSpec(style)
        updated
    }

    /**
     * Applies a parsed MSE [MseStyleSpec] (e.g., from the Beta Frame Radar code viewer)
     * to the active workspace without modifying card art (`image` remains untouched/null).
     */
    suspend fun applyRawStyleSpec(
        spec: MseStyleSpec,
        stylesheetId: String? = null,
    ): MseSetArchive? = withContext(Dispatchers.IO) {
        _styleSpec.value = spec
        val current = _archive.value ?: return@withContext null
        val targetSheet = stylesheetId?.trim().takeUnless { it.isNullOrBlank() } ?: spec.stylesheetId
        if (targetSheet.isBlank()) return@withContext current
        pushUndoSnapshot()
        val updatedDoc = current.document.withTopLevelScalar("stylesheet", targetSheet)
        val updated = current.copy(document = updatedDoc)
        _archive.value = updated
        _hasUnsavedChanges.value = true
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
        pushUndoSnapshot()
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
        pushUndoSnapshot()
        val updatedDoc = current.document.withDuplicatedCard(cardIndex, MseSetParser.currentMseTimestamp())
        _archive.value = current.copy(document = updatedDoc)
        _hasUnsavedChanges.value = true
        val newIdx = (cardIndex + 1).coerceIn(0, (updatedDoc.cards.size - 1).coerceAtLeast(0))
        _activeCardIndex.value = newIdx
        newIdx
    }

    suspend fun deleteCard(cardIndex: Int): Int = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext 0
        // MANDATORY CANON LORE PRESERVATION GUARD: never delete a preserved CANON_ORIGINAL card.
        if (isCardProtectedCanon(cardIndex)) {
            return@withContext CANON_PROTECTED_SENTINEL
        }
        pushUndoSnapshot()
        val afterDelete = current.document.withDeletedCard(cardIndex)
        val finalDoc = if (afterDelete.cards.isEmpty()) {
            afterDelete.withAppendedBlankCard(MseSetParser.currentMseTimestamp())
        } else {
            afterDelete
        }
        setActiveArchiveInternal(current.copy(document = finalDoc))
        _hasUnsavedChanges.value = true
        val newIdx = cardIndex.coerceIn(0, (finalDoc.cards.size - 1).coerceAtLeast(0))
        _activeCardIndex.value = newIdx
        newIdx
    }

    /**
     * 1-tap "Branch Alternate Lore Variant": creates a NEW editable `FAN_ALTERNATE_VARIANT` card
     * linked to the untouched `CANON_ORIGINAL` card, keeping strictly `image == null`.
     */
    suspend fun branchAlternateLoreVariant(canonCardIndex: Int): Int = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext 0
        val source = current.document.cards.getOrNull(canonCardIndex) ?: return@withContext 0
        pushUndoSnapshot()
        val fields = LinkedHashMap(source.allEditableFields)
        fields.remove("image") // STRICT: never copy art automatically
        val baseName = source.name.ifBlank { "Card #${canonCardIndex + 1}" }
        fields["name"] = if (baseName.endsWith("(Alternate)")) baseName else "$baseName (Alternate)"
        fields[SetComparisonEngine.MSE_FIELD_CARD_ORIGIN] = CardOrigin.FAN_ALTERNATE_VARIANT.wireValue
        val canonId = source.canonSourceId ?: source.field("passcode").ifBlank { "mse_card_${canonCardIndex + 1}" }
        fields[SetComparisonEngine.MSE_FIELD_LINKED_CANON_ID] = canonId
        fields.remove(SetComparisonEngine.MSE_FIELD_CANON_SOURCE_ID)

        val updatedDoc = current.document.withAppendedCardFromFields(
            fields = fields,
            timestampIso = MseSetParser.currentMseTimestamp(),
            insertAfterCardIndex = canonCardIndex,
            preserveExistingImageField = false,
        )
        setActiveArchiveInternal(current.copy(document = updatedDoc))
        _hasUnsavedChanges.value = true
        val newIdx = (canonCardIndex + 1).coerceIn(0, (updatedDoc.cards.size - 1).coerceAtLeast(0))
        _activeCardIndex.value = newIdx
        newIdx
    }

    /**
     * Appends a metadata-only copy of an official card baseline (`image == null`).
     */
    suspend fun copyOfficialReferenceBaselineCard(
        officialCard: ComparableCardRecord,
        origin: CardOrigin = CardOrigin.FAN_ALTERNATE_VARIANT,
    ): Int = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext -1
        pushUndoSnapshot()
        val fields = SetComparisonEngine.createReferenceBaselineFields(officialCard, origin)
        val updatedDoc = current.document.withAppendedCardFromFields(
            fields = fields,
            timestampIso = MseSetParser.currentMseTimestamp(),
            preserveExistingImageField = false,
        )
        setActiveArchiveInternal(current.copy(document = updatedDoc))
        _hasUnsavedChanges.value = true
        val newIdx = (updatedDoc.cards.size - 1).coerceAtLeast(0)
        _activeCardIndex.value = newIdx
        newIdx
    }

    /**
     * Merges cards from an external `.mse-set` file into the currently open set (or Split-Off Set)
     * as [importOrigin] (`ORIGINAL_OC_CARD` or `FAN_ALTERNATE_VARIANT`) without modifying any
     * existing `CANON_ORIGINAL` cards. Preserves embedded art blobs ONLY for imported cards that
     * already carried artwork; artless cards remain strictly `image == null`.
     */
    suspend fun mergeExternalMseSetIntoActive(
        uri: Uri,
        importOrigin: CardOrigin = CardOrigin.ORIGINAL_OC_CARD,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val current = _archive.value ?: throw IllegalStateException("No active set open to merge into.")
            val displayName = resolveDisplayName(uri) ?: "external.mse-set"
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected external .mse-set.")
            val importedArchive = stream.use { MseSetParser.readArchive(it, displayName = displayName) }
            val importedCards = importedArchive.document.cards
            require(importedCards.isNotEmpty()) { "Selected .mse-set contains no cards." }

            pushUndoSnapshot()
            var doc = current.document
            val blobs = current.embeddedBlobs.toMutableMap()
            val nowIso = MseSetParser.currentMseTimestamp()
            val safeOrigin = if (importOrigin == CardOrigin.CANON_ORIGINAL) {
                CardOrigin.ORIGINAL_OC_CARD
            } else {
                importOrigin
            }

            importedCards.forEach { card ->
                val fields = LinkedHashMap(card.allEditableFields)
                fields[SetComparisonEngine.MSE_FIELD_CARD_ORIGIN] = safeOrigin.wireValue
                fields.remove(SetComparisonEngine.MSE_FIELD_CANON_SOURCE_ID)
                val oldImgKey = card.image
                var preserveImg = false
                if (!oldImgKey.isNullOrBlank()) {
                    val imgBytes = importedArchive.embeddedBlobs[oldImgKey]
                    if (imgBytes != null) {
                        val newImgKey = nextAvailableImageEntryName(blobs.keys)
                        blobs[newImgKey] = imgBytes
                        fields["image"] = newImgKey
                        preserveImg = true
                    } else {
                        fields.remove("image")
                    }
                } else {
                    fields.remove("image")
                }
                doc = doc.withAppendedCardFromFields(
                    fields = fields,
                    timestampIso = nowIso,
                    preserveExistingImageField = preserveImg,
                )
            }

            setActiveArchiveInternal(current.copy(document = doc, embeddedBlobs = blobs))
            _hasUnsavedChanges.value = true
            importedCards.size
        }
    }

    /**
     * Applies interactive cross-game / Split-Off field remapping and optional target style switch
     * while strictly keeping `CANON_ORIGINAL` cards intact and `image` untouched.
     */
    suspend fun applyDynamicFieldMapping(
        fieldMapping: Map<String, String>,
        targetFramework: UniversalCardFramework? = null,
        discoveredStyle: DiscoveredStyle? = null,
    ): MseSetArchive? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
        pushUndoSnapshot()
        var doc = current.document.withRemappedCardFields(fieldMapping, skipCanonOriginals = true)
        if (discoveredStyle != null) {
            doc = doc.withTopLevelScalar("game", discoveredStyle.gameId)
                .withTopLevelScalar("stylesheet", discoveredStyle.stylesheetId)
            applyDiscoveredStyleSpec(discoveredStyle)
        } else if (targetFramework != null) {
            doc = doc.withTopLevelScalar(
                "game",
                FrameworkMseScaffoldGenerator.sanitizeMseId(targetFramework.upstreamMseGameId ?: targetFramework.id),
            ).withTopLevelScalar(
                "stylesheet",
                FrameworkMseScaffoldGenerator.sanitizeMseId(targetFramework.upstreamMseStyleId ?: "standard"),
            )
            applyFrameworkStyleSpec(targetFramework)
        }
        val updated = current.copy(document = doc)
        setActiveArchiveInternal(updated)
        _hasUnsavedChanges.value = true
        updated
    }

    suspend fun updateCardFields(cardIndex: Int, updates: Map<String, String?>): MseSetArchive? =
        withContext(Dispatchers.IO) {
            val current = _archive.value ?: return@withContext null
            if (updates.isEmpty()) return@withContext current
            // MANDATORY CANON LORE PRESERVATION GUARD: do not overwrite CANON_ORIGINAL cards directly
            if (isCardProtectedCanon(cardIndex)) {
                return@withContext null
            }
            pushUndoSnapshot()
            var doc = current.document
            updates.forEach { (fieldKey, newValue) ->
                doc = doc.withCardFieldUpdated(cardIndex, fieldKey, newValue)
            }
            doc = doc.withCardFieldUpdated(cardIndex, "time modified", MseSetParser.currentMseTimestamp())
            val updated = current.copy(document = doc)
            setActiveArchiveInternal(updated)
            _hasUnsavedChanges.value = true
            updated
        }

    suspend fun updateCardField(cardIndex: Int, fieldKey: String, newValue: String?): MseSetArchive? =
        updateCardFields(cardIndex, mapOf(fieldKey to newValue))

    suspend fun updateSetInfo(info: MseSetInfo): MseSetArchive? = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext null
        pushUndoSnapshot()
        val updated = current.copy(document = current.document.withSetInfoUpdated(info))
        _archive.value = updated
        _hasUnsavedChanges.value = true
        updated
    }

    suspend fun addKeyword(keyword: String, match: String, reminder: String, rules: String) =
        withContext(Dispatchers.IO) {
            val current = _archive.value ?: return@withContext
            pushUndoSnapshot()
            val updatedDoc = current.document.withAddedKeyword(keyword, match, reminder, rules)
            _archive.value = current.copy(document = updatedDoc)
            _hasUnsavedChanges.value = true
        }

    suspend fun updateKeyword(index: Int, keyword: String, match: String, reminder: String, rules: String) =
        withContext(Dispatchers.IO) {
            val current = _archive.value ?: return@withContext
            pushUndoSnapshot()
            val updatedDoc = current.document.withUpdatedKeyword(index, keyword, match, reminder, rules)
            _archive.value = current.copy(document = updatedDoc)
            _hasUnsavedChanges.value = true
        }

    suspend fun deleteKeyword(index: Int) = withContext(Dispatchers.IO) {
        val current = _archive.value ?: return@withContext
        pushUndoSnapshot()
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
            pushUndoSnapshot()
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
        pushUndoSnapshot()
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
        pushUndoSnapshot()
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

    private fun scaleHitboxes(source: MseStyleSpec, targetW: Int, targetH: Int): List<MseHitbox> {
        if (source.cardWidthPx <= 0 || source.cardHeightPx <= 0) return source.hitboxes
        val sx = targetW.toFloat() / source.cardWidthPx.toFloat()
        val sy = targetH.toFloat() / source.cardHeightPx.toFloat()
        return source.hitboxes.map { h ->
            h.copy(
                left = h.left * sx,
                top = h.top * sy,
                width = h.width * sx,
                height = h.height * sy,
            )
        }
    }

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

    companion object {
        private const val MAX_HISTORY_DEPTH = 50
        const val CANON_PROTECTED_SENTINEL = -1
    }
}
