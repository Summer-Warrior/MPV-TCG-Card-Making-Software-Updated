package com.example.data.mse

import android.content.Context
import com.example.data.bridge.BridgeTemplateDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@JsonClass(generateAdapter = true)
internal data class GitHubContentItemDto(
    @Json(name = "name") val name: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "download_url") val downloadUrl: String? = null,
)

/**
 * Dynamically discovers EVERY `.mse-game`, `.mse-style`, `.mse-include`, and MSE pack font
 * across `MagicSetEditorPacks` using the Zero-Rate-Limit Discovery Strategy:
 *
 * 1. Calls `GET https://api.github.com/repos/MagicSetEditorPacks/{repo}/contents/data`
 *    (strictly 1 ETag-cached API call per enabled repo).
 * 2. Fetches tiny plain-text `game` and `style` files directly from unmetered
 *    `https://raw.githubusercontent.com/MagicSetEditorPacks/{repo}/{branch}/data/{folder}/{game|style}`
 *    with bounded concurrency (`Semaphore(10)`).
 * 3. Persists the entire parsed catalog immediately to local disk JSON cache so subsequent launches
 *    load instantaneously offline.
 * 4. Scans `Magic - Fonts` and `Other - Fonts` via the same 1-call ETag-cached contents endpoint.
 * 5. Resolves real upstream blank base-frame PNG/JPG variants for any `.mse-style` for offline preview.
 */
class MseTemplateCatalogRepository(
    private val context: Context,
    private val moshi: Moshi,
    private val okHttpClient: OkHttpClient,
) {
    private val cacheFile: File
        get() = File(context.filesDir, "mse_template_catalog_v1.json")

    private val cacheAdapter = moshi.adapter(CatalogDiskCache::class.java)
    private val contentListAdapter = moshi.adapter<List<GitHubContentItemDto>>(
        Types.newParameterizedType(List::class.java, GitHubContentItemDto::class.java)
    )

    private val syncMutex = Mutex()
    private val cacheWriteMutex = Mutex()
    private val rawFetchSemaphore = Semaphore(10)

    private var diskCache: CatalogDiskCache = loadDiskCacheSync()
    private var bridgeTemplatesCache: List<BridgeTemplateDto> = emptyList()

    private val _games = MutableStateFlow<List<DiscoveredGame>>(emptyList())
    val games: StateFlow<List<DiscoveredGame>> = _games.asStateFlow()

    private val _styles = MutableStateFlow<List<DiscoveredStyle>>(emptyList())
    val styles: StateFlow<List<DiscoveredStyle>> = _styles.asStateFlow()

    private val _packFonts = MutableStateFlow<List<DiscoveredPackFont>>(emptyList())
    val packFonts: StateFlow<List<DiscoveredPackFont>> = _packFonts.asStateFlow()

    private val _syncState = MutableStateFlow(
        CatalogSyncState(
            syncedGamesCount = diskCache.games.size,
            syncedStylesCount = diskCache.styles.size,
            syncedFontsCount = diskCache.packFonts.size,
            betaEnabled = diskCache.betaEnabled,
            outdatedEnabled = diskCache.outdatedEnabled,
        )
    )
    val syncState: StateFlow<CatalogSyncState> = _syncState.asStateFlow()

    init {
        publishMergedFlows()
    }

    /**
     * Merges locally installed templates reported by the Termux MSE Bridge (`GET /templates`)
     * on top of the dynamically indexed MagicSetEditorPacks catalog.
     */
    fun mergeBridgeTemplates(bridgeTemplates: List<BridgeTemplateDto>) {
        bridgeTemplatesCache = bridgeTemplates
        publishMergedFlows()
    }

    /**
     * Enables/disables opt-in `Beta-Update-Pack` or `Outdated-Templates` pack channels and triggers sync.
     */
    suspend fun setOptInPackEnabled(filter: CatalogPackFilter, enabled: Boolean) {
        val updated = when (filter) {
            CatalogPackFilter.BETA -> diskCache.copy(betaEnabled = enabled)
            CatalogPackFilter.OUTDATED -> diskCache.copy(outdatedEnabled = enabled)
            else -> return
        }
        persistDiskCache(updated)
        publishMergedFlows()
        if (enabled) {
            syncCatalog(forceRefresh = false)
        }
    }

    /**
     * Runs incremental, zero-rate-limit indexing across all enabled MagicSetEditorPacks repos.
     * Skips network sync on launch if fresh cache exists unless [forceRefresh] is true.
     */
    suspend fun syncCatalog(forceRefresh: Boolean = false) {
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                val enabledPacks = CatalogPackConfig.ALL_PACKS.filter { pack ->
                    when (pack.filterCategory) {
                        CatalogPackFilter.BETA -> diskCache.betaEnabled
                        CatalogPackFilter.OUTDATED -> diskCache.outdatedEnabled
                        else -> pack.defaultEnabled
                    }
                }

                // Determine which packs actually need a check
                val packsToCheck = if (forceRefresh || diskCache.games.isEmpty() || diskCache.styles.isEmpty()) {
                    enabledPacks
                } else {
                    val existingRepos = diskCache.styles.map { it.packRepo }.toSet() +
                        diskCache.games.map { it.packRepo }.toSet()
                    enabledPacks.filter { it.repo !in existingRepos }
                }

                if (packsToCheck.isEmpty()) {
                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        progressFraction = 1f,
                        statusMessage = "Catalog up to date",
                        lastError = null,
                        syncedGamesCount = _games.value.size,
                        syncedStylesCount = _styles.value.size,
                        syncedFontsCount = _packFonts.value.size,
                    )
                    return@withContext
                }

                _syncState.value = _syncState.value.copy(
                    isSyncing = true,
                    progressFraction = 0.05f,
                    statusMessage = "Indexing Magic & Non-Magic packs…",
                    lastError = null,
                )

                var workingCache = diskCache
                var lastErr: String? = null

                for ((packIdx, pack) in packsToCheck.withIndex()) {
                    val baseProgress = packIdx.toFloat() / packsToCheck.size.coerceAtLeast(1)
                    val packSpan = 1f / packsToCheck.size.coerceAtLeast(1)

                    _syncState.value = _syncState.value.copy(
                        progressFraction = (baseProgress + packSpan * 0.08f).coerceIn(0.05f, 0.96f),
                        statusMessage = "Scanning ${pack.label}…",
                    )

                    val dataEtagKey = "${pack.repo}:data"
                    val previousEtag = if (forceRefresh) null else workingCache.repoEtags[dataEtagKey]

                    when (
                        val dirFetch = fetchGitHubDirectory(
                            repo = pack.repo,
                            path = "data",
                            previousEtag = previousEtag,
                        )
                    ) {
                        is DirectoryFetchResult.NotModified -> {
                            // 304 Not Modified — keep existing indexed entries for this repo
                        }

                        is DirectoryFetchResult.Success -> {
                            val dirs = dirFetch.items.filter { it.type == "dir" }
                            val gameDirs = dirs.filter { it.name.endsWith(".mse-game", ignoreCase = true) }
                            val styleDirs = dirs.filter { it.name.endsWith(".mse-style", ignoreCase = true) }
                            val includeDirs = dirs.filter { it.name.endsWith(".mse-include", ignoreCase = true) }

                            val totalRawFiles = (gameDirs.size + styleDirs.size).coerceAtLeast(1)
                            val completedCounter = AtomicInteger(0)

                            val (parsedGames, parsedStyles) = coroutineScope {
                                val gameDeferreds = gameDirs.map { item ->
                                    async {
                                        rawFetchSemaphore.withPermit {
                                            val parsed = fetchAndParseGame(pack, item.name)
                                            val done = completedCounter.incrementAndGet()
                                            if (done % 6 == 0 || done == totalRawFiles) {
                                                _syncState.value = _syncState.value.copy(
                                                    progressFraction = (baseProgress + packSpan * (0.15f + 0.75f * (done.toFloat() / totalRawFiles)))
                                                        .coerceIn(0.05f, 0.96f),
                                                    statusMessage = "Indexing ${pack.label} ($done/$totalRawFiles)…",
                                                )
                                            }
                                            parsed
                                        }
                                    }
                                }

                                val styleDeferreds = styleDirs.map { item ->
                                    async {
                                        rawFetchSemaphore.withPermit {
                                            val parsed = fetchAndParseStyle(pack, item.name)
                                            val done = completedCounter.incrementAndGet()
                                            if (done % 8 == 0 || done == totalRawFiles) {
                                                _syncState.value = _syncState.value.copy(
                                                    progressFraction = (baseProgress + packSpan * (0.15f + 0.75f * (done.toFloat() / totalRawFiles)))
                                                        .coerceIn(0.05f, 0.96f),
                                                    statusMessage = "Indexing ${pack.label} ($done/$totalRawFiles)…",
                                                )
                                            }
                                            parsed
                                        }
                                    }
                                }

                                Pair(
                                    gameDeferreds.awaitAll().filterNotNull(),
                                    styleDeferreds.awaitAll().filterNotNull(),
                                )
                            }

                            val newIncludes = includeDirs.map {
                                DiscoveredInclude(
                                    folderName = it.name,
                                    packRepo = pack.repo,
                                    packBranch = pack.branch,
                                )
                            }

                            val mergedGames = workingCache.games.filterNot { it.packRepo == pack.repo } + parsedGames
                            val mergedStyles = workingCache.styles.filterNot { it.packRepo == pack.repo } + parsedStyles
                            val mergedIncludes = workingCache.includes.filterNot { it.packRepo == pack.repo } + newIncludes
                            val mergedEtags = workingCache.repoEtags.toMutableMap().apply {
                                dirFetch.etag?.let { put(dataEtagKey, it) }
                            }

                            workingCache = workingCache.copy(
                                games = mergedGames,
                                styles = mergedStyles,
                                includes = mergedIncludes,
                                repoEtags = mergedEtags,
                                lastSyncedEpochMs = System.currentTimeMillis(),
                            )
                            persistDiskCache(workingCache)
                            publishMergedFlows()
                        }

                        is DirectoryFetchResult.Error -> {
                            lastErr = "${pack.label}: ${dirFetch.message}"
                        }
                    }

                    // Scan MSE pack fonts (`Magic - Fonts`, `Other - Fonts`) via 1 cached API call
                    pack.fontFolder?.let { fontFolder ->
                        val fontEtagKey = "${pack.repo}:$fontFolder"
                        val prevFontEtag = if (forceRefresh) null else workingCache.repoEtags[fontEtagKey]
                        when (
                            val fontDir = fetchGitHubDirectory(
                                repo = pack.repo,
                                path = fontFolder,
                                previousEtag = prevFontEtag,
                            )
                        ) {
                            is DirectoryFetchResult.Success -> {
                                val foundFonts = fontDir.items
                                    .filter { it.type == "file" }
                                    .filter {
                                        val lower = it.name.lowercase()
                                        lower.endsWith(".ttf") || lower.endsWith(".otf")
                                    }
                                    .map { fileItem ->
                                        val family = inferFontFamilyName(fileItem.name)
                                        val rawUrl = fileItem.downloadUrl?.takeIf { it.isNotBlank() }
                                            ?: MseTemplateCatalog.buildRawRepoFileUrl(
                                                repo = pack.repo,
                                                branch = pack.branch,
                                                repoRelativePath = "$fontFolder/${fileItem.name}",
                                            )
                                        DiscoveredPackFont(
                                            familyName = family,
                                            fileName = fileItem.name,
                                            downloadUrl = rawUrl,
                                            packRepo = pack.repo,
                                            fontFolder = fontFolder,
                                        )
                                    }
                                val mergedFonts = workingCache.packFonts.filterNot { it.packRepo == pack.repo } + foundFonts
                                val mergedEtags = workingCache.repoEtags.toMutableMap().apply {
                                    fontDir.etag?.let { put(fontEtagKey, it) }
                                }
                                workingCache = workingCache.copy(
                                    packFonts = mergedFonts,
                                    repoEtags = mergedEtags,
                                )
                                persistDiskCache(workingCache)
                                publishMergedFlows()
                            }

                            is DirectoryFetchResult.NotModified -> Unit
                            is DirectoryFetchResult.Error -> Unit
                        }
                    }
                }

                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    progressFraction = 1f,
                    statusMessage = if (lastErr == null) "All Games & Frames Synced" else "Synced with partial offline cache",
                    lastError = lastErr,
                    syncedGamesCount = _games.value.size,
                    syncedStylesCount = _styles.value.size,
                    syncedFontsCount = _packFonts.value.size,
                    betaEnabled = workingCache.betaEnabled,
                    outdatedEnabled = workingCache.outdatedEnabled,
                )
            }
        }
    }

    /**
     * Finds the matching [DiscoveredStyle] for an open set's `game` and `stylesheet`.
     */
    fun findStyle(gameId: String, stylesheetId: String): DiscoveredStyle? {
        val normGame = gameId.trim().lowercase()
        val normSheet = stylesheetId.trim().lowercase()
            .removePrefix("$normGame-")
            .removeSuffix(".mse-style")
        val all = _styles.value
        return all.firstOrNull {
            it.gameId.equals(normGame, ignoreCase = true) &&
                (it.stylesheetId.equals(normSheet, ignoreCase = true) ||
                    it.folderName.equals("$normGame-$normSheet.mse-style", ignoreCase = true) ||
                    it.folderName.equals("$normSheet.mse-style", ignoreCase = true))
        } ?: all.firstOrNull {
            it.stylesheetId.equals(normSheet, ignoreCase = true)
        }
    }

    /**
     * Resolves real upstream blank base-frame PNG/JPG variants for an active `.mse-style`
     * for the Offline Real Upstream Base-Frame Compositor (ZERO fake Compose-drawn frames).
     *
     * Strategy:
     * 1. Uses parsed `baseFrameChoices` & `defaultFrameImage` directly from the `.mse-style` file if present.
     * 2. If scripted (`{ card_background() }`) or empty, performs at most 1 cached `contents` lookup on
     *    the active style folder and (if needed) its primary `depends on:` `.mse-include`/`.mse-style`
     *    package, filtering strictly for real blank frame PNG/JPGs (excluding masks/symbols/icons/samples).
     */
    suspend fun resolveOfflineFrameVariants(style: DiscoveredStyle): List<FrameVariantOption> =
        withContext(Dispatchers.IO) {
            if (style.packRepo.isBlank() || style.isFromBridge) {
                return@withContext emptyList()
            }

            val variants = linkedMapOf<String, FrameVariantOption>()

            // 1. Direct choice images & default image parsed from the `.mse-style/style` file
            style.baseFrameChoices.forEach { (choiceKey, imgFile) ->
                if (!isExcludedFrameFilename(imgFile)) {
                    val cleanKey = choiceKey.trim().lowercase()
                    val url = resolveAssetUrlForStyle(style, imgFile)
                    variants[cleanKey] = FrameVariantOption(
                        key = cleanKey,
                        label = prettifyVariantLabel(choiceKey),
                        imageUrl = url,
                        swatchColorHex = inferSwatchHex(choiceKey),
                    )
                }
            }

            style.defaultFrameImage?.let { defaultImg ->
                if (variants.isEmpty() && !isExcludedFrameFilename(defaultImg)) {
                    val url = resolveAssetUrlForStyle(style, defaultImg)
                    variants["default"] = FrameVariantOption(
                        key = "default",
                        label = "Default",
                        imageUrl = url,
                        swatchColorHex = 0xFF8B929E,
                    )
                }
            }

            // 2. If scripted or still needing color/frame variants, inspect cached folder contents
            if (variants.isEmpty() || style.usesScriptedBackground) {
                val styleImages = fetchOrLoadFolderImages(
                    repo = style.packRepo,
                    branch = style.packBranch,
                    folderName = style.folderName,
                )
                addDiscoveredFolderFrames(
                    into = variants,
                    repo = style.packRepo,
                    branch = style.packBranch,
                    folderName = style.folderName,
                    gameId = style.gameId,
                    files = styleImages,
                )

                // If active style folder delegates frames to a `depends on:` include/style package, check primary dependency
                if (variants.isEmpty()) {
                    val depCandidates = buildList {
                        style.dependsOn.forEach { dep ->
                            if (dep.endsWith(".mse-style") || dep.endsWith(".mse-include")) {
                                add(dep)
                            }
                        }
                        // Common Magic fallback include when `card_background()` is inherited
                        if (style.gameId.equals("magic", ignoreCase = true)) {
                            add("magic-m15-altered.mse-style")
                            add("magic-new.mse-style")
                        }
                    }.distinct().take(2)

                    for (depFolder in depCandidates) {
                        val depRepo = findRepoForFolder(depFolder) ?: style.packRepo
                        val depBranch = findBranchForRepo(depRepo)
                        val depImages = fetchOrLoadFolderImages(
                            repo = depRepo,
                            branch = depBranch,
                            folderName = depFolder,
                        )
                        addDiscoveredFolderFrames(
                            into = variants,
                            repo = depRepo,
                            branch = depBranch,
                            folderName = depFolder,
                            gameId = style.gameId,
                            files = depImages,
                        )
                        if (variants.isNotEmpty()) break
                    }
                }
            }

            sortFrameVariants(variants.values.toList(), style.gameId)
        }

    private suspend fun fetchAndParseGame(pack: CatalogPackConfig, folderName: String): DiscoveredGame? {
        val rawUrl = MseTemplateCatalog.buildRawDataUrl(pack.repo, pack.branch, folderName, "game")
        val rawText = fetchRawText(rawUrl) ?: return null
        val meta = MseStyleParser.parseGameText(rawText, folderName)
        val iconUrl = meta.iconFile?.let {
            MseTemplateCatalog.buildRawDataUrl(pack.repo, pack.branch, folderName, it)
        }
        return DiscoveredGame(
            gameId = meta.gameId,
            folderName = folderName,
            fullName = meta.fullName,
            shortName = meta.shortName,
            iconFile = meta.iconFile,
            iconUrl = iconUrl,
            positionHint = meta.positionHint,
            packRepo = pack.repo,
            packBranch = pack.branch,
            packLabel = pack.label,
        )
    }

    private suspend fun fetchAndParseStyle(pack: CatalogPackConfig, folderName: String): DiscoveredStyle? {
        val rawUrl = MseTemplateCatalog.buildRawDataUrl(pack.repo, pack.branch, folderName, "style")
        val rawText = fetchRawText(rawUrl) ?: return null
        val spec = MseStyleParser.parseStyleText(rawText, sourceLabel = folderName)
        val iconUrl = spec.iconFile?.let {
            MseTemplateCatalog.buildRawDataUrl(pack.repo, pack.branch, folderName, it)
        }
        return DiscoveredStyle(
            stylesheetId = spec.stylesheetId,
            folderName = folderName,
            gameId = spec.game,
            fullName = spec.fullName,
            shortName = spec.shortName,
            iconFile = spec.iconFile,
            iconUrl = iconUrl,
            positionHint = spec.positionHint,
            packRepo = pack.repo,
            packBranch = pack.branch,
            packLabel = pack.label,
            cardWidthPx = spec.cardWidthPx,
            cardHeightPx = spec.cardHeightPx,
            cardDpi = spec.cardDpi,
            dependsOn = spec.dependsOn,
            referencedFonts = spec.referencedFonts,
            rawStyleUrl = rawUrl,
            baseFrameChoices = spec.baseFrameChoices,
            defaultFrameImage = spec.defaultFrameImage,
            usesScriptedBackground = spec.usesScriptedBackground,
            hitboxes = spec.hitboxes.map { MseHitboxRecord.fromDomain(it) },
        )
    }

    private suspend fun fetchOrLoadFolderImages(
        repo: String,
        branch: String,
        folderName: String,
    ): List<String> {
        val cacheKey = "$repo/$folderName"
        diskCache.folderAssetCache[cacheKey]?.let { cached ->
            if (cached.imageFiles.isNotEmpty()) {
                return cached.imageFiles
            }
        }

        val dirResult = fetchGitHubDirectory(
            repo = repo,
            path = "data/$folderName",
            previousEtag = diskCache.folderAssetCache[cacheKey]?.etag,
        )

        return when (dirResult) {
            is DirectoryFetchResult.Success -> {
                val images = dirResult.items
                    .filter { it.type == "file" }
                    .map { it.name }
                    .filter { isImageFile(it) }
                val entry = FolderAssetCacheEntry(
                    repo = repo,
                    branch = branch,
                    folderName = folderName,
                    etag = dirResult.etag,
                    imageFiles = images,
                )
                val updatedMap = diskCache.folderAssetCache.toMutableMap().apply {
                    put(cacheKey, entry)
                }
                persistDiskCache(diskCache.copy(folderAssetCache = updatedMap))
                images
            }

            is DirectoryFetchResult.NotModified -> {
                diskCache.folderAssetCache[cacheKey]?.imageFiles.orEmpty()
            }

            is DirectoryFetchResult.Error -> emptyList()
        }
    }

    private fun addDiscoveredFolderFrames(
        into: LinkedHashMap<String, FrameVariantOption>,
        repo: String,
        branch: String,
        folderName: String,
        gameId: String,
        files: List<String>,
    ) {
        val candidates = files.filter { !isExcludedFrameFilename(it) }
        if (candidates.isEmpty()) return

        if (gameId.equals("magic", ignoreCase = true)) {
            val magicMap = listOf(
                Triple("colorless", "Colorless", listOf("ccard.jpg", "ccard.png", "c.png", "colorless.png", "colorless.jpg")),
                Triple("white", "W", listOf("wcard.jpg", "wcard.png", "w.png", "white.png", "white.jpg")),
                Triple("blue", "U", listOf("ucard.jpg", "ucard.png", "u.png", "blue.png", "blue.jpg")),
                Triple("black", "B", listOf("bcard.jpg", "bcard.png", "b.png", "black.png", "black.jpg")),
                Triple("red", "R", listOf("rcard.jpg", "rcard.png", "r.png", "red.png", "red.jpg")),
                Triple("green", "G", listOf("gcard.jpg", "gcard.png", "g.png", "green.png", "green.jpg")),
                Triple("multicolor", "Gold", listOf("mcard.jpg", "mcard.png", "m.png", "gold.png", "gold.jpg", "multi.png")),
                Triple("artifact", "Artifact", listOf("acard.jpg", "acard.png", "a.png", "artifact.png", "artifact.jpg")),
                Triple("land", "Land", listOf("lcard.jpg", "lcard.png", "l.png", "land.png", "land.jpg")),
            )
            for ((key, label, patterns) in magicMap) {
                val matched = candidates.firstOrNull { file ->
                    val low = file.lowercase()
                    patterns.any { low == it } ||
                        (low.endsWith("card.jpg") && low.startsWith(patterns.first().take(1))) ||
                        (low.endsWith("card.png") && low.startsWith(patterns.first().take(1)))
                }
                if (matched != null && !into.containsKey(key)) {
                    into[key] = FrameVariantOption(
                        key = key,
                        label = label,
                        imageUrl = MseTemplateCatalog.buildRawDataUrl(repo, branch, folderName, matched),
                        swatchColorHex = inferSwatchHex(key),
                    )
                }
            }
        }

        // Generic frame discovery for Non-Magic games (or Magic styles with custom filenames like card.png / frame.png)
        if (into.isEmpty()) {
            val prioritized = candidates.sortedByDescending { scoreFrameCandidate(it) }.take(12)
            prioritized.forEach { file ->
                val baseName = file.substringBeforeLast('.')
                val key = baseName.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                if (!into.containsKey(key)) {
                    into[key] = FrameVariantOption(
                        key = key,
                        label = prettifyVariantLabel(baseName),
                        imageUrl = MseTemplateCatalog.buildRawDataUrl(repo, branch, folderName, file),
                        swatchColorHex = inferSwatchHex(baseName),
                    )
                }
            }
        }
    }

    private fun resolveAssetUrlForStyle(style: DiscoveredStyle, rawRef: String): String {
        val clean = rawRef.trim().trim('"')
        if (clean.startsWith("/")) {
            val targetPkg = clean.trimStart('/').substringBefore('/')
            val repo = findRepoForFolder(targetPkg) ?: style.packRepo
            val branch = findBranchForRepo(repo)
            return MseTemplateCatalog.buildRawDataUrl(repo, branch, style.folderName, clean)
        }
        return MseTemplateCatalog.buildRawDataUrl(style.packRepo, style.packBranch, style.folderName, clean)
    }

    private fun findRepoForFolder(folderName: String): String? {
        diskCache.styles.firstOrNull { it.folderName.equals(folderName, ignoreCase = true) }?.let { return it.packRepo }
        diskCache.includes.firstOrNull { it.folderName.equals(folderName, ignoreCase = true) }?.let { return it.packRepo }
        diskCache.games.firstOrNull { it.folderName.equals(folderName, ignoreCase = true) }?.let { return it.packRepo }
        return null
    }

    private fun findBranchForRepo(repo: String): String =
        CatalogPackConfig.ALL_PACKS.firstOrNull { it.repo.equals(repo, ignoreCase = true) }?.branch ?: "main"

    private fun fetchGitHubDirectory(
        repo: String,
        path: String,
        previousEtag: String?,
    ): DirectoryFetchResult {
        val encodedPath = MseTemplateCatalog.encodePathSegments(path)
        val url = "https://api.github.com/repos/${CatalogPackConfig.ORG}/$repo/contents/$encodedPath"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TCG-Studio-Android")
        if (!previousEtag.isNullOrBlank()) {
            reqBuilder.header("If-None-Match", previousEtag)
        }
        return runCatching {
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                when (resp.code) {
                    304 -> DirectoryFetchResult.NotModified
                    in 200..299 -> {
                        val body = resp.body?.string().orEmpty()
                        val items = contentListAdapter.fromJson(body).orEmpty()
                        DirectoryFetchResult.Success(items = items, etag = resp.header("ETag"))
                    }

                    else -> DirectoryFetchResult.Error("HTTP ${resp.code}")
                }
            }
        }.getOrElse { err ->
            DirectoryFetchResult.Error(err.message ?: "Network error")
        }
    }

    private fun fetchRawText(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "TCG-Studio-Android")
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull()
    }

    private fun publishMergedFlows() {
        val enabledRepos = CatalogPackConfig.ALL_PACKS.filter { pack ->
            when (pack.filterCategory) {
                CatalogPackFilter.BETA -> diskCache.betaEnabled
                CatalogPackFilter.OUTDATED -> diskCache.outdatedEnabled
                else -> pack.defaultEnabled
            }
        }.map { it.repo }.toSet()

        val activeStyles = diskCache.styles.filter { it.packRepo in enabledRepos }.toMutableList()
        val activeGames = diskCache.games.filter { it.packRepo in enabledRepos }.toMutableList()

        // Merge local MSE Bridge templates (`GET /templates`)
        val styleKeys = activeStyles.map { "${it.gameId.lowercase()}::${it.stylesheetId.lowercase()}" }.toMutableSet()
        val gameIds = activeGames.map { it.gameId.lowercase() }.toMutableSet()

        for (dto in bridgeTemplatesCache) {
            val gId = dto.game.trim().lowercase().ifEmpty { "custom" }
            if (gId !in gameIds) {
                gameIds.add(gId)
                activeGames.add(
                    DiscoveredGame(
                        gameId = gId,
                        folderName = "$gId.mse-game",
                        fullName = MseTemplateCatalog.displayGameName(gId),
                        shortName = MseTemplateCatalog.displayGameName(gId),
                        packRepo = "",
                        packLabel = dto.sourceSplit.ifBlank { "MSE Bridge" },
                        isFromBridge = true,
                    )
                )
            }
            val cleanStyleId = dto.id.removePrefix("$gId-").removeSuffix(".mse-style")
            val sKey = "$gId::${cleanStyleId.lowercase()}"
            if (sKey !in styleKeys) {
                styleKeys.add(sKey)
                activeStyles.add(
                    DiscoveredStyle(
                        stylesheetId = cleanStyleId,
                        folderName = dto.id,
                        gameId = gId,
                        fullName = dto.shortName.ifBlank { cleanStyleId },
                        shortName = dto.shortName.ifBlank { cleanStyleId },
                        packRepo = "",
                        packLabel = if (dto.isCustom) "Custom Template" else dto.sourceSplit.ifBlank { "MSE Bridge" },
                        isFromBridge = true,
                    )
                )
            }
        }

        // Synthesize any game referenced by styles (e.g., Beta-Update-Pack styles referencing `magic`)
        val gamesById = linkedMapOf<String, DiscoveredGame>()
        activeGames
            .sortedWith(compareBy<DiscoveredGame> { it.positionHint }.thenBy { it.displayName.lowercase() })
            .forEach { g ->
                val key = g.gameId.lowercase()
                val existing = gamesById[key]
                if (existing == null || (existing.iconUrl == null && g.iconUrl != null)) {
                    gamesById[key] = g
                }
            }

        activeStyles.forEach { s ->
            val key = s.gameId.lowercase()
            if (!gamesById.containsKey(key)) {
                gamesById[key] = DiscoveredGame(
                    gameId = key,
                    folderName = "$key.mse-game",
                    fullName = MseTemplateCatalog.displayGameName(key),
                    shortName = MseTemplateCatalog.displayGameName(key),
                    iconUrl = s.iconUrl,
                    packRepo = s.packRepo,
                    packBranch = s.packBranch,
                    packLabel = s.packLabel,
                )
            }
        }

        // Deduplicate styles by (gameId, folderName, packRepo) and sort authentically like MSE
        val dedupedStyles = activeStyles
            .distinctBy { "${it.packRepo}::${it.folderName.lowercase()}" }
            .sortedWith(
                compareBy<DiscoveredStyle> { it.positionHint }
                    .thenBy { it.displayName.lowercase() }
            )

        val finalGames = gamesById.values.sortedWith(
            compareBy<DiscoveredGame> { if (it.gameId == "magic") 0 else 1 }
                .thenBy { it.positionHint }
                .thenBy { it.displayName.lowercase() }
        )

        _games.value = finalGames
        _styles.value = dedupedStyles
        _packFonts.value = diskCache.packFonts
            .filter { it.packRepo in enabledRepos }
            .distinctBy { it.familyName.lowercase() }
            .sortedBy { it.familyName.lowercase() }

        _syncState.value = _syncState.value.copy(
            syncedGamesCount = finalGames.size,
            syncedStylesCount = dedupedStyles.size,
            syncedFontsCount = _packFonts.value.size,
            betaEnabled = diskCache.betaEnabled,
            outdatedEnabled = diskCache.outdatedEnabled,
        )
    }

    private fun loadDiskCacheSync(): CatalogDiskCache {
        if (!cacheFile.exists() || cacheFile.length() == 0L) return CatalogDiskCache()
        return runCatching {
            cacheAdapter.fromJson(cacheFile.readText()) ?: CatalogDiskCache()
        }.getOrDefault(CatalogDiskCache())
    }

    private suspend fun persistDiskCache(updated: CatalogDiskCache) {
        cacheWriteMutex.withLock {
            diskCache = updated
            runCatching {
                val tmp = File(context.filesDir, "mse_template_catalog_v1.tmp")
                tmp.writeText(cacheAdapter.toJson(updated))
                if (tmp.exists()) {
                    tmp.renameTo(cacheFile)
                }
            }
        }
    }

    private fun isImageFile(name: String): Boolean {
        val low = name.lowercase()
        return low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".webp")
    }

    private fun isExcludedFrameFilename(name: String): Boolean {
        val low = name.lowercase()
        return low.contains("mask") ||
            low.contains("symbol") ||
            low.contains("icon") ||
            low.contains("card-sample") ||
            low.contains("sample") ||
            low.contains("pt") && !low.contains("empty") && !low.contains("script") ||
            low.contains("crown") ||
            low.contains("watermark") ||
            low.contains("mana") ||
            low.contains("rarity") ||
            low.contains("stamp") ||
            low.contains("holofoil") ||
            low.contains("shadow") ||
            low.contains("glow") ||
            low.contains("pip") ||
            low.contains("arrow") ||
            low.contains("logo") ||
            low.contains("back") && !low.contains("background")
    }

    private fun scoreFrameCandidate(fileName: String): Int {
        val low = fileName.lowercase()
        var score = 0
        if (low.contains("card")) score += 50
        if (low.contains("frame")) score += 45
        if (low.contains("bg") || low.contains("background")) score += 40
        if (low.contains("base") || low.contains("blank")) score += 35
        if (low.endsWith(".png")) score += 10
        if (low.endsWith(".jpg") || low.endsWith(".jpeg")) score += 8
        return score
    }

    private fun sortFrameVariants(
        list: List<FrameVariantOption>,
        gameId: String,
    ): List<FrameVariantOption> {
        if (!gameId.equals("magic", ignoreCase = true)) return list
        val order = listOf("colorless", "white", "blue", "black", "red", "green", "multicolor", "artifact", "land")
        return list.sortedBy { opt ->
            val idx = order.indexOf(opt.key.lowercase())
            if (idx >= 0) idx else 100
        }
    }

    private fun prettifyVariantLabel(raw: String): String {
        val clean = raw.trim()
            .removeSuffix("card")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        return when (clean.lowercase()) {
            "w", "white" -> "W"
            "u", "blue" -> "U"
            "b", "black" -> "B"
            "r", "red" -> "R"
            "g", "green" -> "G"
            "c", "colorless" -> "Colorless"
            "m", "multi", "multicolor", "gold" -> "Gold"
            "a", "artifact" -> "Artifact"
            "l", "land" -> "Land"
            else -> clean.split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
                .ifBlank { "Frame" }
        }
    }

    private fun inferSwatchHex(raw: String): Long? {
        return when (raw.lowercase()) {
            "w", "white", "light" -> 0xFFF3E9C9
            "u", "blue", "water" -> 0xFF3E88C7
            "b", "black", "dark" -> 0xFF453D4F
            "r", "red", "fire" -> 0xFFC9513E
            "g", "green", "earth", "grass" -> 0xFF3C8A57
            "m", "gold", "multicolor", "hybrid" -> 0xFFD4A84B
            "a", "artifact", "metal", "steel" -> 0xFF8B99A8
            "l", "land" -> 0xFF9C8468
            "c", "colorless" -> 0xFFAAB0BC
            else -> null
        }
    }

    private fun inferFontFamilyName(fileName: String): String {
        return fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { fileName }
    }

    private sealed class DirectoryFetchResult {
        data class Success(val items: List<GitHubContentItemDto>, val etag: String?) : DirectoryFetchResult()
        data object NotModified : DirectoryFetchResult()
        data class Error(val message: String) : DirectoryFetchResult()
    }
}
