package com.example.data.upstream

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.mse.MseStyleParser
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class BetaRadarScanResult(
    val totalDiscovered: Int,
    val newlyDetectedCount: Int,
    val feedsChecked: Int,
    val feedsFailed: Int,
    val statusSummary: String,
)

/**
 * Multi-Site Beta Card Frame Radar Repository, Unique-Use Mechanic Detector,
 * Tailor-Made MSE Code Synthesizer, and Mandatory Code-Only Fan Source Trail Builder.
 *
 * Uses strictly Android SDK XML parsing (`XmlPullParser` with JVM DOM fallback for unit tests),
 * `BitmapFactory.Options.inJustDecodeBounds`, existing `OkHttpClient`, `Moshi`, and `MseStyleParser`.
 */
class BetaFrameRadarRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    private val radarRootDir: File
        get() = File(context.filesDir, "beta_frame_radar").apply { mkdirs() }

    private val downloadedCodeDir: File
        get() = File(radarRootDir, "downloaded_code").apply { mkdirs() }

    private val stateFile: File
        get() = File(radarRootDir, "radar_state.json")

    private val stateAdapter = moshi.adapter(BetaRadarPersistedState::class.java).indent("  ")

    private val imageProbeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val _persistedState = MutableStateFlow(loadStateSync())
    val persistedState: StateFlow<BetaRadarPersistedState> = _persistedState.asStateFlow()

    fun allFeeds(state: BetaRadarPersistedState = _persistedState.value): List<BetaFrameSourceFeed> =
        builtInFeeds() + state.customFeeds

    private fun loadStateSync(): BetaRadarPersistedState {
        return runCatching {
            if (!stateFile.exists()) return BetaRadarPersistedState()
            val raw = stateFile.readText(StandardCharsets.UTF_8)
            stateAdapter.fromJson(raw) ?: BetaRadarPersistedState()
        }.onFailure { err ->
            Log.w(TAG, "Failed reading persisted Beta Radar state: ${err.message}", err)
        }.getOrDefault(BetaRadarPersistedState())
    }

    private fun saveStateSync(state: BetaRadarPersistedState) {
        runCatching {
            stateFile.writeText(stateAdapter.toJson(state), StandardCharsets.UTF_8)
        }.onFailure { err ->
            Log.w(TAG, "Failed writing persisted Beta Radar state: ${err.message}", err)
        }
        _persistedState.value = state
    }

    suspend fun setAutoCheckWhenOnline(enabled: Boolean) = withContext(Dispatchers.IO) {
        saveStateSync(_persistedState.value.copy(autoCheckWhenOnline = enabled))
    }

    suspend fun markAllSeen() = withContext(Dispatchers.IO) {
        val current = _persistedState.value
        val updatedSeen = current.seenItemSignatures.toMutableMap()
        val updatedItems = current.cachedItems.map { item ->
            updatedSeen[item.id] = item.latestShaOrGuid.ifBlank { item.displayName }
            item.copy(isNewSinceLastSeen = false)
        }
        saveStateSync(
            current.copy(
                seenItemSignatures = updatedSeen,
                cachedItems = updatedItems,
            )
        )
    }

    suspend fun addCustomFeed(
        labelInput: String,
        targetInput: String,
        category: BetaFrameCategory,
    ): Result<BetaFrameSourceFeed> = withContext(Dispatchers.IO) {
        val trimmedTarget = targetInput.trim()
        if (trimmedTarget.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a GitHub owner/repo, DeviantArt username/query, or RSS URL."))
        }
        val current = _persistedState.value
        val ghPair = parseGitHubRepoInput(trimmedTarget)
        val newFeed = if (ghPair != null) {
            val (owner, repo) = ghPair
            val trust = classifyRepoOriginTrust(
                owner = owner,
                repo = repo,
                isFork = false,
                defaultTrust = if (category == BetaFrameCategory.FAN_YGO_CUSTOM) {
                    FrameOriginTrust.FAN_YGO_CUSTOM
                } else {
                    FrameOriginTrust.NON_MSE_OFFICIAL
                },
            )
            BetaFrameSourceFeed(
                id = "custom_gh_${sanitizeKey("${owner}_${repo}")}_${System.currentTimeMillis() % 10_000}",
                label = labelInput.trim().ifBlank { "$owner / $repo" },
                protocol = BetaFeedProtocol.GITHUB_MSE_PACK,
                category = category,
                originTrust = trust,
                owner = owner,
                repo = repo,
                branch = "main",
                dataSubPath = "data",
                feedUrl = "https://github.com/$owner/$repo",
                enabled = true,
                isBuiltIn = false,
            )
        } else {
            val (protocol, resolvedUrl) = buildDeviantArtRssUrl(trimmedTarget)
            val trust = if (category == BetaFrameCategory.FAN_YGO_CUSTOM) {
                FrameOriginTrust.FAN_YGO_CUSTOM
            } else {
                FrameOriginTrust.FAN_ART_STYLE
            }
            BetaFrameSourceFeed(
                id = "custom_rss_${sanitizeKey(labelInput.ifBlank { trimmedTarget }).take(24)}_${System.currentTimeMillis() % 10_000}",
                label = labelInput.trim().ifBlank { trimmedTarget.take(40) },
                protocol = protocol,
                category = category,
                originTrust = trust,
                feedUrl = resolvedUrl,
                enabled = true,
                isBuiltIn = false,
            )
        }

        saveStateSync(current.copy(customFeeds = current.customFeeds + newFeed))
        Result.success(newFeed)
    }

    suspend fun removeCustomFeed(feedId: String) = withContext(Dispatchers.IO) {
        val current = _persistedState.value
        saveStateSync(
            current.copy(
                customFeeds = current.customFeeds.filterNot { it.id == feedId },
            )
        )
    }

    /**
     * Scans all enabled verified public feeds & custom feeds concurrently with strict per-feed
     * error isolation, ETag caching, automatic GitHub fork detection, and unique-use assessment.
     */
    suspend fun runRadarScan(knownGameIds: Set<String> = emptySet()): BetaRadarScanResult =
        withContext(Dispatchers.IO) {
            val current = _persistedState.value
            val effectiveKnownGames = current.knownGameIds + knownGameIds.map { it.lowercase(Locale.US) }
            val enabledFeeds = allFeeds(current).filter { it.enabled }
            val previousItemsByFeedLabel = current.cachedItems.groupBy { it.sourceFeedLabel }

            var feedsFailed = 0
            val updatedEtags = current.etagsByFeedId.toMutableMap()

            val feedResults: List<Pair<String, List<DiscoveredBetaFrameItem>>> = coroutineScope {
                enabledFeeds.map { feed ->
                    async {
                        runCatching {
                            val previousEtag = current.etagsByFeedId[feed.id]
                            val outcome = when (feed.protocol) {
                                BetaFeedProtocol.GITHUB_MSE_PACK -> scanGitHubPackFeed(feed, previousEtag, effectiveKnownGames)
                                BetaFeedProtocol.GITHUB_TOPIC_SEARCH -> scanGitHubTopicSearchFeed(feed, previousEtag)
                                BetaFeedProtocol.DEVIANTART_RSS,
                                BetaFeedProtocol.GENERIC_RSS -> scanRssOrAtomFeed(feed, previousEtag)
                            }
                            if (!outcome.newEtag.isNullOrBlank()) {
                                synchronized(updatedEtags) {
                                    updatedEtags[feed.id] = outcome.newEtag
                                }
                            }
                            val items = if (outcome.notModified304) {
                                previousItemsByFeedLabel[feed.label].orEmpty()
                            } else {
                                outcome.items
                            }
                            feed.label to items
                        }.onFailure { err ->
                            Log.w(TAG, "Beta Radar feed '${feed.label}' failed: ${err.message}", err)
                            synchronized(updatedEtags) {
                                feedsFailed++
                            }
                        }.getOrElse {
                            feed.label to previousItemsByFeedLabel[feed.label].orEmpty()
                        }
                    }
                }.awaitAll()
            }

            val mergedById = linkedMapOf<String, DiscoveredBetaFrameItem>()
            feedResults.forEach { (_, list) ->
                list.forEach { rawItem ->
                    val existing = mergedById[rawItem.id]
                    if (existing == null || (rawItem.hasUpstreamTemplateFile && !existing.hasUpstreamTemplateFile)) {
                        mergedById[rawItem.id] = rawItem
                    }
                }
            }

            val seenMap = current.seenItemSignatures
            val prevCachedById = current.cachedItems.associateBy { it.id }

            val finalizedItems = mergedById.values.map { item ->
                val sig = item.latestShaOrGuid.ifBlank { item.displayName }
                val prevSig = seenMap[item.id]
                val isNew = prevSig == null || prevSig != sig
                val cachedPath = prevCachedById[item.id]?.cachedCodeFilePath
                    ?.takeIf { File(it).exists() }
                val forcedAlwaysBeta = item.isAlwaysBeta || item.originTrust.alwaysBeta
                item.copy(
                    isAlwaysBeta = forcedAlwaysBeta,
                    isNewSinceLastSeen = isNew,
                    cachedCodeFilePath = cachedPath,
                )
            }.sortedWith(
                compareByDescending<DiscoveredBetaFrameItem> { it.isNewSinceLastSeen }
                    .thenByDescending { it.isEligibleForUniqueNoTemplateSynthesis() }
                    .thenByDescending { it.hasUpstreamTemplateFile }
                    .thenBy { it.displayName.lowercase(Locale.US) }
            )

            val newlyDetected = finalizedItems.count { it.isNewSinceLastSeen }
            val nowMs = System.currentTimeMillis()
            val discoveredGames = finalizedItems.mapNotNull { it.gameId.takeIf(String::isNotBlank)?.lowercase(Locale.US) }

            saveStateSync(
                current.copy(
                    lastCheckedEpochMs = nowMs,
                    knownGameIds = effectiveKnownGames + discoveredGames,
                    etagsByFeedId = updatedEtags,
                    cachedItems = finalizedItems,
                )
            )

            val summary = if (feedsFailed == 0) {
                "Scanned ${enabledFeeds.size} sites • ${finalizedItems.size} frames ($newlyDetected new)."
            } else {
                "Scanned ${enabledFeeds.size} sites ($feedsFailed unavailable) • ${finalizedItems.size} frames ($newlyDetected new)."
            }

            BetaRadarScanResult(
                totalDiscovered = finalizedItems.size,
                newlyDetectedCount = newlyDetected,
                feedsChecked = enabledFeeds.size,
                feedsFailed = feedsFailed,
                statusSummary = summary,
            )
        }

    /**
     * Fetches a verbatim upstream `.mse-style` code package OR synthesizes tailor-made MSE code
     * for newly discovered unique-use no-template card frames, always embedding the mandatory
     * code-only source trail on 100% of fan-made frames.
     */
    suspend fun fetchBetaFrameCodePackage(item: DiscoveredBetaFrameItem): Result<BetaFrameCodePackage> =
        withContext(Dispatchers.IO) {
            if (item.isFanMade() && !item.hasRequiredFanSources()) {
                return@withContext Result.failure(
                    IllegalStateException(FAN_SOURCE_REQUIRED_ERROR)
                )
            }

            val assessment = item.uniqueUseAssessment ?: assessUniqueFrameUse(item)
            val fetchedAtIso = currentUtcIso()

            try {
                val pkg = if (item.hasUpstreamTemplateFile && item.styleFileRawUrl.isNotBlank()) {
                    val rawStyle = downloadUtf8Text(item.styleFileRawUrl)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Could not download upstream style file from ${item.styleFileRawUrl}")
                        )
                    val rawGame = item.gameFileRawUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        downloadUtf8Text(url)
                    }
                    buildDownloadableBetaCodeText(
                        item = item,
                        assessment = assessment,
                        rawStyleCode = rawStyle,
                        rawGameCode = rawGame,
                        wasAutoSynthesized = false,
                        fetchedAtIso = fetchedAtIso,
                    )
                } else if (item.isEligibleForUniqueNoTemplateSynthesis() || assessment.hasClearUniqueUse) {
                    val probedDims = item.previewThumbnailUrl
                        ?.takeIf { it.startsWith("http", ignoreCase = true) }
                        ?.let { probeImageDimensions(it) }
                    val (synthStyle, synthGame) = synthesizeUniqueUseBetaCode(
                        item = item,
                        assessment = assessment,
                        measuredWidthPx = probedDims?.first,
                        measuredHeightPx = probedDims?.second,
                    )
                    buildDownloadableBetaCodeText(
                        item = item,
                        assessment = assessment,
                        rawStyleCode = synthStyle,
                        rawGameCode = synthGame,
                        wasAutoSynthesized = true,
                        fetchedAtIso = fetchedAtIso,
                    )
                } else {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "No upstream .mse-style template found and item '${item.displayName}' did not match a clear unique card-frame mechanic (rejected generic art/accessory)."
                        )
                    )
                }

                // Cache downloadable code package on disk for offline inspection/re-export
                val cacheFile = File(downloadedCodeDir, "${sanitizeKey(item.id)}.mse-style-beta.txt")
                runCatching {
                    cacheFile.writeText(pkg.formattedDownloadableText, StandardCharsets.UTF_8)
                    val cur = _persistedState.value
                    val updatedItems = cur.cachedItems.map { existing ->
                        if (existing.id == item.id) existing.copy(cachedCodeFilePath = cacheFile.absolutePath)
                        else existing
                    }
                    saveStateSync(cur.copy(cachedItems = updatedItems))
                }.onFailure { err ->
                    Log.w(TAG, "Failed caching beta code package to disk: ${err.message}", err)
                }

                Result.success(pkg)
            } catch (ex: Exception) {
                Log.w(TAG, "fetchBetaFrameCodePackage failed for ${item.id}: ${ex.message}", ex)
                Result.failure(ex)
            }
        }

    suspend fun saveCodePackageToUri(
        uri: Uri,
        codePackage: BetaFrameCodePackage,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open output stream for destination file.")
            out.use { stream ->
                stream.write(codePackage.formattedDownloadableText.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed exporting Beta Frame code file to URI: ${err.message}", err)
        }
    }

    // ---- Per-Protocol Network Scanners ----

    private data class FeedFetchOutcome(
        val items: List<DiscoveredBetaFrameItem>,
        val newEtag: String?,
        val notModified304: Boolean = false,
    )

    private fun scanGitHubPackFeed(
        feed: BetaFrameSourceFeed,
        previousEtag: String?,
        knownGameIds: Set<String>,
    ): FeedFetchOutcome {
        val owner = feed.owner.trim()
        val repo = feed.repo.trim()
        if (owner.isBlank() || repo.isBlank()) {
            return FeedFetchOutcome(emptyList(), null)
        }

        // Step 1: Check repo metadata to automatically detect forks & default branch
        var isFork = false
        var effectiveBranch = feed.branch.ifBlank { "main" }
        runCatching {
            val metaReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            okHttpClient.newCall(metaReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    isFork = obj.optBoolean("fork", false)
                    val defaultBranch = obj.optString("default_branch", "")
                    if (defaultBranch.isNotBlank() && feed.branch.isBlank()) {
                        effectiveBranch = defaultBranch
                    }
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Repo metadata fork check skipped for $owner/$repo: ${err.message}")
        }

        val resolvedTrust = classifyRepoOriginTrust(
            owner = owner,
            repo = repo,
            isFork = isFork,
            defaultTrust = feed.originTrust,
        )

        // Step 2: List repository contents path with ETag conditional GET
        val cleanSubPath = feed.dataSubPath.trim('/')
        val contentsUrl = buildString {
            append("https://api.github.com/repos/$owner/$repo/contents")
            if (cleanSubPath.isNotBlank()) {
                append("/$cleanSubPath")
            }
            append("?ref=$effectiveBranch")
        }

        val reqBuilder = Request.Builder()
            .url(contentsUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
        if (!previousEtag.isNullOrBlank()) {
            reqBuilder.header("If-None-Match", previousEtag)
        }

        okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 304) {
                return FeedFetchOutcome(emptyList(), previousEtag, notModified304 = true)
            }
            if (!resp.isSuccessful) {
                error("GitHub HTTP ${resp.code} for $owner/$repo/$cleanSubPath")
            }
            val newEtag = resp.header("ETag")
            val rawBody = resp.body?.string().orEmpty()
            val array = JSONArray(rawBody)
            val discovered = mutableListOf<DiscoveredBetaFrameItem>()
            val gameFilesByGame = mutableMapOf<String, String>()

            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val name = entry.optString("name", "")
                if (name.endsWith(".mse-game", ignoreCase = true)) {
                    val gId = name.removeSuffix(".mse-game").lowercase(Locale.US)
                    val path = entry.optString("path", "$cleanSubPath/$name")
                    gameFilesByGame[gId] = "https://raw.githubusercontent.com/$owner/$repo/$effectiveBranch/$path/game"
                }
            }

            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val name = entry.optString("name", "")
                val entryType = entry.optString("type", "")
                val sha = entry.optString("sha", "")
                val htmlUrl = entry.optString("html_url", "https://github.com/$owner/$repo")
                val entryPath = entry.optString("path", "$cleanSubPath/$name")

                if (name.endsWith(".mse-style", ignoreCase = true) && entryType == "dir") {
                    val stem = name.removeSuffix(".mse-style")
                    val inferredGameId = stem.substringBefore('-').lowercase(Locale.US).ifBlank { "custom-tcg" }
                    val category = when {
                        feed.category == BetaFrameCategory.FAN_YGO_CUSTOM -> BetaFrameCategory.FAN_YGO_CUSTOM
                        inferredGameId !in knownGameIds && feed.category == BetaFrameCategory.NEW_GAME -> BetaFrameCategory.NEW_GAME
                        else -> feed.category
                    }
                    val styleRawUrl = "https://raw.githubusercontent.com/$owner/$repo/$effectiveBranch/$entryPath/style"
                    val displayName = stem.replace('-', ' ')
                        .split(' ')
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
                    val baseItem = DiscoveredBetaFrameItem(
                        id = "gh_${sanitizeKey("${owner}_${repo}_${name}")}",
                        displayName = displayName,
                        folderName = name,
                        gameId = inferredGameId,
                        category = category,
                        originTrust = resolvedTrust,
                        isAlwaysBeta = resolvedTrust.alwaysBeta,
                        sourceFeedLabel = feed.label,
                        sourceFeedUrl = "https://github.com/$owner/$repo",
                        authorCredit = owner,
                        sourceWebUrl = htmlUrl,
                        styleFileRawUrl = styleRawUrl,
                        gameFileRawUrl = gameFilesByGame[inferredGameId],
                        descriptionSnippet = "$owner/$repo ($effectiveBranch) • $name",
                        latestShaOrGuid = sha,
                        hasUpstreamTemplateFile = true,
                    )
                    discovered += baseItem.copy(uniqueUseAssessment = assessUniqueFrameUse(baseItem))
                } else if (repo.equals("DataEditorX", ignoreCase = true) &&
                    (name.contains("card", ignoreCase = true) ||
                        name.contains("frame", ignoreCase = true) ||
                        name.endsWith(".json", ignoreCase = true) ||
                        name.endsWith(".txt", ignoreCase = true) ||
                        name.endsWith(".mse-style", ignoreCase = true))
                ) {
                    val baseItem = DiscoveredBetaFrameItem(
                        id = "dex_${sanitizeKey("${owner}_${repo}_${name}")}",
                        displayName = "DataEditorX • $name",
                        folderName = name,
                        gameId = "yugioh",
                        category = if (isFork) BetaFrameCategory.FAN_YGO_CUSTOM else feed.category,
                        originTrust = resolvedTrust,
                        isAlwaysBeta = true,
                        sourceFeedLabel = feed.label,
                        sourceFeedUrl = "https://github.com/$owner/$repo",
                        authorCredit = owner,
                        sourceWebUrl = htmlUrl,
                        styleFileRawUrl = "",
                        descriptionSnippet = "DataEditorX YGO frame/data resource ($owner/$repo): $name",
                        latestShaOrGuid = sha,
                        hasUpstreamTemplateFile = false,
                    )
                    discovered += baseItem.copy(uniqueUseAssessment = assessUniqueFrameUse(baseItem))
                }
            }

            return FeedFetchOutcome(discovered.take(MAX_ITEMS_PER_FEED), newEtag)
        }
    }

    private fun scanGitHubTopicSearchFeed(
        feed: BetaFrameSourceFeed,
        previousEtag: String?,
    ): FeedFetchOutcome {
        val url = feed.feedUrl.trim()
        if (url.isBlank()) return FeedFetchOutcome(emptyList(), null)

        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
        if (!previousEtag.isNullOrBlank()) {
            reqBuilder.header("If-None-Match", previousEtag)
        }

        okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 304) {
                return FeedFetchOutcome(emptyList(), previousEtag, notModified304 = true)
            }
            if (!resp.isSuccessful) {
                error("GitHub search HTTP ${resp.code} for ${feed.label}")
            }
            val newEtag = resp.header("ETag")
            val body = resp.body?.string().orEmpty()
            val root = JSONObject(body)
            val itemsArray = root.optJSONArray("items") ?: JSONArray()
            val results = mutableListOf<DiscoveredBetaFrameItem>()

            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.optJSONObject(i) ?: continue
                val fullName = obj.optString("full_name", "")
                val ownerObj = obj.optJSONObject("owner")
                val ownerLogin = ownerObj?.optString("login", "").orEmpty()
                val repoName = obj.optString("name", fullName.substringAfter('/'))
                val htmlUrl = obj.optString("html_url", "")
                val description = obj.optString("description", "")
                val pushedAt = obj.optString("pushed_at", "")
                val isFork = obj.optBoolean("fork", false)

                val trust = classifyRepoOriginTrust(
                    owner = ownerLogin,
                    repo = repoName,
                    isFork = isFork,
                    defaultTrust = feed.originTrust,
                )
                val inferredGameId = inferGameIdFromText("$repoName $description", feed.category)
                val rawItem = DiscoveredBetaFrameItem(
                    id = "ghs_${sanitizeKey(fullName.ifBlank { repoName })}",
                    displayName = repoName.replace('-', ' ').replace('_', ' '),
                    folderName = "${sanitizeKey(repoName)}.mse-style",
                    gameId = inferredGameId,
                    category = feed.category,
                    originTrust = trust,
                    isAlwaysBeta = true,
                    sourceFeedLabel = feed.label,
                    sourceFeedUrl = url,
                    authorCredit = ownerLogin,
                    sourceWebUrl = htmlUrl,
                    descriptionSnippet = description.take(260),
                    latestShaOrGuid = pushedAt.ifBlank { fullName },
                    hasUpstreamTemplateFile = false,
                )
                results += rawItem.copy(uniqueUseAssessment = assessUniqueFrameUse(rawItem))
            }

            return FeedFetchOutcome(results, newEtag)
        }
    }

    private fun scanRssOrAtomFeed(
        feed: BetaFrameSourceFeed,
        previousEtag: String?,
    ): FeedFetchOutcome {
        val url = feed.feedUrl.trim()
        if (url.isBlank()) return FeedFetchOutcome(emptyList(), null)

        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
        if (!previousEtag.isNullOrBlank()) {
            reqBuilder.header("If-None-Match", previousEtag)
        }

        okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 304) {
                return FeedFetchOutcome(emptyList(), previousEtag, notModified304 = true)
            }
            if (!resp.isSuccessful) {
                error("RSS/Atom HTTP ${resp.code} for ${feed.label}")
            }
            val newEtag = resp.header("ETag")
            val xml = resp.body?.string().orEmpty()
            val parsed = parseRssOrAtomXml(xml, feed).take(MAX_ITEMS_PER_FEED)
            return FeedFetchOutcome(parsed, newEtag)
        }
    }

    private fun downloadUtf8Text(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            imageProbeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed downloading text from $url: ${err.message}", err)
        }.getOrNull()
    }

    private fun probeImageDimensions(imageUrl: String): Pair<Int, Int>? {
        val req = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            imageProbeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                val w = opts.outWidth
                val h = opts.outHeight
                if (w >= 260 && h >= 360 && h > w) w to h else null
            }
        }.onFailure { err ->
            Log.w(TAG, "Preview image dimension probe skipped for $imageUrl: ${err.message}")
        }.getOrNull()
    }

    companion object {
        private const val TAG = "BetaFrameRadarRepo"
        private const val USER_AGENT = "MPV-TCG-Studio-BetaRadar/1.0"
        private const val MAX_ITEMS_PER_FEED = 24

        const val FAN_SOURCE_REQUIRED_ERROR =
            "Fan-made frames require at least one valid source URL in code for permission/rules retracing"

        private val CANONICAL_OFFICIAL_MSE_REPOS = setOf(
            "g-e-n-e-v-e-n-s-i-s/magicseteditor2",
            "magicseteditorpacks/full-magic-pack",
        )

        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val WHITESPACE_REGEX = Regex("\\s+")

        /**
         * All 16 verified public feeds across MSE packs, DataEditorX, OmegaDEX fork,
         * GitHub multi-TCG repos, DeviantArt RSS, Itch.io RSS, Reddit Atom/RSS, and OpenGameArt RSS.
         */
        fun builtInFeeds(): List<BetaFrameSourceFeed> = listOf(
            BetaFrameSourceFeed(
                id = "mse_official_core",
                label = "MagicSetEditor2 (Canonical Official)",
                protocol = BetaFeedProtocol.GITHUB_MSE_PACK,
                category = BetaFrameCategory.CURRENT_GAME,
                originTrust = FrameOriginTrust.CANONICAL_OFFICIAL_MSE,
                owner = "G-e-n-e-v-e-n-s-i-S",
                repo = "MagicSetEditor2",
                branch = "main",
                dataSubPath = "data",
                feedUrl = "https://github.com/G-e-n-e-v-e-n-s-i-S/MagicSetEditor2",
            ),
            BetaFrameSourceFeed(
                id = "mse_full_magic_pack",
                label = "MagicSetEditorPacks • Full-Magic-Pack",
                protocol = BetaFeedProtocol.GITHUB_MSE_PACK,
                category = BetaFrameCategory.CURRENT_GAME,
                originTrust = FrameOriginTrust.CANONICAL_OFFICIAL_MSE,
                owner = "MagicSetEditorPacks",
                repo = "Full-Magic-Pack",
                branch = "main",
                dataSubPath = "data",
                feedUrl = "https://github.com/MagicSetEditorPacks/Full-Magic-Pack",
            ),
            BetaFrameSourceFeed(
                id = "mse_m15_magic_pack",
                label = "MagicSetEditorPacks • M15-Magic-Pack (Fork)",
                protocol = BetaFeedProtocol.GITHUB_MSE_PACK,
                category = BetaFrameCategory.CURRENT_GAME,
                originTrust = FrameOriginTrust.FORKED_REPO,
                owner = "MagicSetEditorPacks",
                repo = "M15-Magic-Pack",
                branch = "main",
                dataSubPath = "data",
                feedUrl = "https://github.com/MagicSetEditorPacks/M15-Magic-Pack",
            ),
            BetaFrameSourceFeed(
                id = "mse_full_non_magic_pack",
                label = "MagicSetEditorPacks • Full-Non-Magic-Pack",
                protocol = BetaFeedProtocol.GITHUB_MSE_PACK,
                category = BetaFrameCategory.NEW_GAME,
                originTrust = FrameOriginTrust.NON_MSE_OFFICIAL,
                owner = "MagicSetEditorPacks",
                repo = "Full-Non-Magic-Pack",
                branch = "main",
                dataSubPath = "data",
                feedUrl = "https://github.com/MagicSetEditorPacks/Full-Non-Magic-Pack",
            ),
            BetaFrameSourceFeed(
                id = "dataeditorx_upstream",
                label = "DataEditorX (247321453)",
                protocol = BetaFeedProtocol.GITHUB_MSE_PACK,
                category = BetaFrameCategory.CURRENT_GAME,
                originTrust = FrameOriginTrust.NON_MSE_OFFICIAL,
                owner = "247321453",
                repo = "DataEditorX",
                branch = "master",
                dataSubPath = "DataEditorX/data",
                feedUrl = "https://github.com/247321453/DataEditorX",
            ),
            BetaFrameSourceFeed(
                id = "dataeditorx_omegadex_fork",
                label = "OmegaDEX / DataEditorX Fork (Lyris12)",
                protocol = BetaFeedProtocol.GITHUB_MSE_PACK,
                category = BetaFrameCategory.FAN_YGO_CUSTOM,
                originTrust = FrameOriginTrust.FORKED_REPO,
                owner = "Lyris12",
                repo = "DataEditorX",
                branch = "master",
                dataSubPath = "DataEditorX/data",
                feedUrl = "https://github.com/Lyris12/DataEditorX",
            ),
            BetaFrameSourceFeed(
                id = "github_ygo_custom_edopro",
                label = "GitHub — YGO Custom & EDOPro Templates",
                protocol = BetaFeedProtocol.GITHUB_TOPIC_SEARCH,
                category = BetaFrameCategory.FAN_YGO_CUSTOM,
                originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
                feedUrl = "https://api.github.com/search/repositories?q=yugioh+mse+template+OR+topic:yugioh-card-maker+OR+topic:edopro-custom-cards&sort=updated&per_page=10",
            ),
            BetaFrameSourceFeed(
                id = "github_multi_tcg_forks",
                label = "GitHub — Multi-TCG / CardConjurer / Proxyshop / Pokémon Forks",
                protocol = BetaFeedProtocol.GITHUB_TOPIC_SEARCH,
                category = BetaFrameCategory.FAN_STYLE,
                originTrust = FrameOriginTrust.FORKED_REPO,
                feedUrl = "https://api.github.com/search/repositories?q=topic:cardconjurer+OR+topic:proxyshop+OR+topic:pokemon-card-maker+OR+tcg+card+template&sort=updated&per_page=10",
            ),
            BetaFrameSourceFeed(
                id = "deviantart_ygo_mse_templates",
                label = "DeviantArt — YGO MSE Templates",
                protocol = BetaFeedProtocol.DEVIANTART_RSS,
                category = BetaFrameCategory.FAN_YGO_CUSTOM,
                originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
                feedUrl = "https://backend.deviantart.com/rss.xml?type=deviation&q=yugioh+mse+template",
            ),
            BetaFrameSourceFeed(
                id = "deviantart_ygo_custom_monsters",
                label = "DeviantArt — Fan YGO Custom Monster Frames",
                protocol = BetaFeedProtocol.DEVIANTART_RSS,
                category = BetaFrameCategory.FAN_YGO_CUSTOM,
                originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
                feedUrl = "https://backend.deviantart.com/rss.xml?type=deviation&q=yugioh+custom+monster+card+frame+OR+template",
            ),
            BetaFrameSourceFeed(
                id = "deviantart_mtg_pokemon_tcg",
                label = "DeviantArt — MTG, Pokémon & TCG Templates",
                protocol = BetaFeedProtocol.DEVIANTART_RSS,
                category = BetaFrameCategory.FAN_STYLE,
                originTrust = FrameOriginTrust.FAN_ART_STYLE,
                feedUrl = "https://backend.deviantart.com/rss.xml?type=deviation&q=mtg+OR+pokemon+OR+tcg+card+template",
            ),
            BetaFrameSourceFeed(
                id = "itchio_card_game_assets",
                label = "Itch.io — Card Game Frame Assets",
                protocol = BetaFeedProtocol.GENERIC_RSS,
                category = BetaFrameCategory.FAN_STYLE,
                originTrust = FrameOriginTrust.FAN_ART_STYLE,
                feedUrl = "https://itch.io/game-assets/tag-card-game.xml",
            ),
            BetaFrameSourceFeed(
                id = "itchio_tcg_templates",
                label = "Itch.io — TCG Template Assets",
                protocol = BetaFeedProtocol.GENERIC_RSS,
                category = BetaFrameCategory.FAN_STYLE,
                originTrust = FrameOriginTrust.FAN_ART_STYLE,
                feedUrl = "https://itch.io/game-assets/tag-tcg.xml",
            ),
            BetaFrameSourceFeed(
                id = "reddit_customyugioh",
                label = "Reddit — r/customyugioh Templates & New Frames",
                protocol = BetaFeedProtocol.GENERIC_RSS,
                category = BetaFrameCategory.FAN_YGO_CUSTOM,
                originTrust = FrameOriginTrust.FAN_YGO_CUSTOM,
                feedUrl = "https://www.reddit.com/r/customyugioh/search.rss?q=template+OR+frame+OR+summon&restrict_sr=1&sort=new",
            ),
            BetaFrameSourceFeed(
                id = "reddit_custommagic",
                label = "Reddit — r/custommagic Frames",
                protocol = BetaFeedProtocol.GENERIC_RSS,
                category = BetaFrameCategory.FAN_STYLE,
                originTrust = FrameOriginTrust.FAN_ART_STYLE,
                feedUrl = "https://www.reddit.com/r/custommagic/search.rss?q=frame+OR+mse+OR+template&restrict_sr=1&sort=new",
            ),
            BetaFrameSourceFeed(
                id = "opengameart_latest",
                label = "OpenGameArt — Latest Assets",
                protocol = BetaFeedProtocol.GENERIC_RSS,
                category = BetaFrameCategory.FAN_STYLE,
                originTrust = FrameOriginTrust.FAN_ART_STYLE,
                feedUrl = "https://opengameart.org/latest/rss",
            ),
        )

        fun classifyRepoOriginTrust(
            owner: String,
            repo: String,
            isFork: Boolean,
            defaultTrust: FrameOriginTrust,
        ): FrameOriginTrust {
            val slug = "${owner.trim().lowercase(Locale.US)}/${repo.trim().lowercase(Locale.US)}"
            if (isFork) return FrameOriginTrust.FORKED_REPO
            if (slug in CANONICAL_OFFICIAL_MSE_REPOS) {
                return FrameOriginTrust.CANONICAL_OFFICIAL_MSE
            }
            return if (defaultTrust == FrameOriginTrust.CANONICAL_OFFICIAL_MSE) {
                FrameOriginTrust.NON_MSE_OFFICIAL
            } else {
                defaultTrust
            }
        }

        fun parseGitHubRepoInput(input: String): Pair<String, String>? {
            val trimmed = input.trim().removeSuffix("/")
            if (trimmed.isBlank()) return null
            val ghRegex = Regex("^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/.*)?$", RegexOption.IGNORE_CASE)
            ghRegex.find(trimmed)?.let { m ->
                val owner = m.groupValues[1]
                val repo = m.groupValues[2].removeSuffix(".git")
                if (owner.isNotBlank() && repo.isNotBlank()) return owner to repo
            }
            val slashRegex = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$")
            slashRegex.find(trimmed)?.let { m ->
                val owner = m.groupValues[1]
                val repo = m.groupValues[2].removeSuffix(".git")
                if (!trimmed.contains("deviantart", ignoreCase = true) &&
                    !trimmed.startsWith("gallery:", ignoreCase = true)
                ) {
                    return owner to repo
                }
            }
            return null
        }

        fun buildDeviantArtRssUrl(input: String): Pair<BetaFeedProtocol, String> {
            val trimmed = input.trim()
            val daProfileRegex = Regex("^(?:https?://)?(?:www\\.)?deviantart\\.com/([A-Za-z0-9_-]+)(?:/.*)?$", RegexOption.IGNORE_CASE)
            daProfileRegex.find(trimmed)?.let { m ->
                val user = m.groupValues[1]
                val encoded = URLEncoder.encode("gallery:$user", "UTF-8")
                return BetaFeedProtocol.DEVIANTART_RSS to "https://backend.deviantart.com/rss.xml?type=deviation&q=$encoded"
            }
            if (trimmed.startsWith("gallery:", ignoreCase = true)) {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                return BetaFeedProtocol.DEVIANTART_RSS to "https://backend.deviantart.com/rss.xml?type=deviation&q=$encoded"
            }
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val proto = if (trimmed.contains("deviantart.com", ignoreCase = true)) {
                    BetaFeedProtocol.DEVIANTART_RSS
                } else {
                    BetaFeedProtocol.GENERIC_RSS
                }
                return proto to trimmed
            }
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            return BetaFeedProtocol.DEVIANTART_RSS to "https://backend.deviantart.com/rss.xml?type=deviation&q=$encodedQuery"
        }

        /**
         * Pure Unique Card-Frame Use Detector.
         * Targets clear unique TCG / Yu-Gi-Oh! monster frame mechanics and rejects playmats,
         * card sleeves, booster packs, wallpapers, banners, memes, and generic artwork.
         */
        fun assessUniqueFrameUse(item: DiscoveredBetaFrameItem): UniqueFrameUseAssessment {
            val hay = listOf(
                item.displayName,
                item.folderName,
                item.descriptionSnippet,
            ).joinToString(" ").lowercase(Locale.US)
            val contextHay = "$hay ${item.gameId.lowercase(Locale.US)} ${item.sourceFeedLabel.lowercase(Locale.US)}"

            val explicitFrameCues = listOf(
                "card frame", "mse-style", "mse template", "card template", "blank template",
                "series 10", "series 11", "series 12", "pendulum frame", "link frame",
                "dark synchro", "link-pendulum", "link pendulum", "maximum monster", "maximum summon",
                "rush duel frame", "over-rush", "over rush", "card maker template", "blank frame",
                "frame template", "custom frame", "monster frame", "card layout", "tcg template",
                "ccg template", "proxyshop", "cardconjurer", "magic set editor",
            )
            val matchedFrameCues = explicitFrameCues.filter { hay.contains(it) }

            val nonFrameAccessoryCues = listOf(
                "playmat", "play mat", "card sleeve", "card sleeves", "sleeves",
                "card back", "cardback", "booster pack", "pack art", "deck box",
                "wallpaper", "desktop background", "banner", "commission sheet", "meme",
                "funny", "shitpost", "fanart", "fan art", "cosplay", "figurine", "plush",
            )
            val matchedNonFrame = nonFrameAccessoryCues.filter { hay.contains(it) }
            if (matchedNonFrame.isNotEmpty() && matchedFrameCues.isEmpty()) {
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = false,
                    kind = UniqueFrameUseKind.NONE_OR_GENERIC_ART,
                    mechanicSummary = "Rejected non-frame accessory/art (${matchedNonFrame.joinToString(", ")}).",
                    uniqueFieldsRequired = emptyList(),
                    matchedSignals = matchedNonFrame,
                )
            }

            val isYgoContext = item.gameId.equals("yugioh", ignoreCase = true) ||
                item.category == BetaFrameCategory.FAN_YGO_CUSTOM ||
                contextHay.contains("yugioh") || contextHay.contains("yu-gi-oh") || contextHay.contains("ygo") ||
                contextHay.contains("edopro") || contextHay.contains("dataeditorx") || contextHay.contains("rush duel") ||
                hay.contains("pendulum") || hay.contains("synchro") || hay.contains("xyz")

            // 1. YGO Dark Synchro
            if (hay.contains("dark synchro") || hay.contains("negative level") || hay.contains("dark tuner")) {
                val signals = listOf("dark synchro", "negative level", "dark tuner").filter { hay.contains(it) }
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.YGO_DARK_SYNCHRO,
                    mechanicSummary = "Yu-Gi-Oh! Dark Synchro frame with left-aligned Negative Level stars and Dark Tuner material header.",
                    uniqueFieldsRequired = listOf("dark level", "dark synchro materials", "atk", "def"),
                    matchedSignals = signals + matchedFrameCues,
                )
            }

            // 2. YGO Link-Pendulum Hybrid
            if (hay.contains("link-pendulum") || hay.contains("link pendulum") ||
                (hay.contains("link") && hay.contains("pendulum") && isYgoContext)
            ) {
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.YGO_LINK_PENDULUM,
                    mechanicSummary = "Yu-Gi-Oh! Link-Pendulum hybrid monster frame combining 8-way Link Arrows, Link Rating, and dual Pendulum Scales/Effect Box.",
                    uniqueFieldsRequired = listOf("link markers", "link rating", "pendulum left scale", "pendulum right scale", "pendulum text", "atk"),
                    matchedSignals = listOf("link", "pendulum") + matchedFrameCues,
                )
            }

            // 3. YGO Extra-Deck Pendulum Hybrids (Synchro/Xyz/Fusion/Ritual Pendulum)
            val hybridTypes = listOf("synchro", "xyz", "fusion", "ritual", "extra deck", "hybrid")
                .filter { hay.contains(it) }
            if (hay.contains("pendulum") && hybridTypes.isNotEmpty()) {
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.YGO_HYBRID_PENDULUM,
                    mechanicSummary = "Yu-Gi-Oh! Extra-Deck Pendulum hybrid (${hybridTypes.joinToString("/")}) with split Pendulum Scale strip and Extra Deck border.",
                    uniqueFieldsRequired = listOf("pendulum left scale", "pendulum right scale", "pendulum text", "hybrid extra type", "rank or level", "atk", "def"),
                    matchedSignals = (listOf("pendulum") + hybridTypes + matchedFrameCues).distinct(),
                )
            }

            // 4. YGO Rush Duel Maximum Monster
            if (hay.contains("maximum monster") || hay.contains("maximum summon") ||
                hay.contains("maximum mode") || hay.contains("maximum atk") ||
                (hay.contains("rush duel") && hay.contains("maximum"))
            ) {
                val signals = listOf("maximum monster", "maximum summon", "maximum mode", "maximum atk", "rush duel")
                    .filter { hay.contains(it) }
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.YGO_MAXIMUM_MONSTER,
                    mechanicSummary = "Yu-Gi-Oh! Rush Duel Maximum Monster 3-piece frame with MAXIMUM ATK banner, Requirement/Effect split, and Left/Center/Right slot marker.",
                    uniqueFieldsRequired = listOf("maximum atk", "rush level", "rush requirement", "rush effect", "maximum piece slot", "atk", "def"),
                    matchedSignals = signals + matchedFrameCues,
                )
            }

            // 5. YGO Rush / Over-Rush Monster
            if (hay.contains("over-rush") || hay.contains("over rush") || hay.contains("overrush") ||
                (hay.contains("rush duel") && (matchedFrameCues.isNotEmpty() || hay.contains("monster") || hay.contains("legend")))
            ) {
                val signals = listOf("over-rush", "over rush", "overrush", "rush duel", "legend").filter { hay.contains(it) }
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.YGO_RUSH_DUEL_UNIQUE,
                    mechanicSummary = "Yu-Gi-Oh! Rush Duel / Over-Rush frame with expanded artwork window, Rush Level emblem, and Requirement/Effect boxes.",
                    uniqueFieldsRequired = listOf("rush level", "rush requirement", "rush effect", "legend badge", "atk", "def"),
                    matchedSignals = signals + matchedFrameCues,
                )
            }

            // 6. YGO Novel / Custom / Anime Monster Mechanics
            val ygoNovelCues = listOf(
                "evolute", "spatial", "chrono", "domain", "armor", "relay", "deck master",
                "mirage", "cosmic", "quantum", "vortex", "eclipse", "runic", "astral", "prism",
                "trap monster", "spell monster", "new summon", "custom summon", "custom monster",
                "anime frame", "manga frame", "dark fusion", "time leap", "bigbang", "conjoint",
                "polarity", "fan summon", "custom mechanic",
            ).filter { hay.contains(it) }
            if (isYgoContext && ygoNovelCues.isNotEmpty()) {
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.YGO_NOVEL_MONSTER_MECHANIC,
                    mechanicSummary = "Yu-Gi-Oh! novel/custom monster frame (${ygoNovelCues.joinToString(", ")}) with dedicated summon gauge & custom mechanic header.",
                    uniqueFieldsRequired = listOf("custom summon gauge", "mechanic badge", "custom materials", "atk", "def"),
                    matchedSignals = (ygoNovelCues + matchedFrameCues).distinct(),
                )
            }

            // 7. MTG Unique Frame Mechanics
            val isMtgContext = item.gameId.equals("magic", ignoreCase = true) ||
                hay.contains("mtg") || hay.contains("magic") || hay.contains("custommagic") ||
                hay.contains("cardconjurer") || hay.contains("proxyshop")
            val mtgMechanicCues = listOf(
                "room", "case", "battle", "saga", "omen", "planeswalker", "split card",
                "prototype", "class enchantment", "adventure", "aftermath", "mutate", "mdfc",
            ).filter { hay.contains(it) }
            if (isMtgContext && mtgMechanicCues.isNotEmpty()) {
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.MTG_UNIQUE_MECHANIC,
                    mechanicSummary = "Magic: The Gathering unique frame mechanic (${mtgMechanicCues.joinToString(", ")}) with specialized chapter/mode zone.",
                    uniqueFieldsRequired = listOf("casting cost", "mechanic chapter or mode", "rule text", "pt"),
                    matchedSignals = (mtgMechanicCues + matchedFrameCues).distinct(),
                )
            }

            // 8. Pokémon Unique Frame Mechanics
            val isPokemonContext = item.gameId.equals("pokemon", ignoreCase = true) ||
                hay.contains("pokemon") || hay.contains("pokémon") || hay.contains("pkmn")
            val pkmnMechanicCues = listOf(
                "tera", "vstar", "vmax", "v-union", "delta species", "mega", "tag team",
                "radiant", "gx", "ace spec", "ancient", "future",
            ).filter { hay.contains(it) }
            if (isPokemonContext && pkmnMechanicCues.isNotEmpty()) {
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.POKEMON_UNIQUE_MECHANIC,
                    mechanicSummary = "Pokémon TCG unique frame mechanic (${pkmnMechanicCues.joinToString(", ")}) with dedicated HP, Special Rule box, and Energy bar.",
                    uniqueFieldsRequired = listOf("hp", "energy type", "special mechanic rule", "attacks", "weakness resistance retreat"),
                    matchedSignals = (pkmnMechanicCues + matchedFrameCues).distinct(),
                )
            }

            // 9. Universal New / Unique Custom TCG Frame (requires BOTH explicit frame cue + stat/mechanic cue)
            val statOrMechanicCues = listOf(
                "atk", "def", "hp", "mana", "cost", "power", "toughness", "level", "rank",
                "gauge", "shield", "energy", "resource", "ability", "effect", "mechanic",
                "summon", "stat", "tcg", "ccg", "card game", "monster", "spell", "creature",
            ).filter { hay.contains(it) }
            if (matchedFrameCues.isNotEmpty() && statOrMechanicCues.isNotEmpty()) {
                return UniqueFrameUseAssessment(
                    hasClearUniqueUse = true,
                    kind = UniqueFrameUseKind.UNIVERSAL_UNIQUE_TCG_FRAME,
                    mechanicSummary = "Custom TCG card frame template with structured card stats & rules layout.",
                    uniqueFieldsRequired = listOf("cost", "mechanic stat", "type", "rule text", "power"),
                    matchedSignals = (matchedFrameCues + statOrMechanicCues).distinct().take(8),
                )
            }

            return UniqueFrameUseAssessment(
                hasClearUniqueUse = false,
                kind = UniqueFrameUseKind.NONE_OR_GENERIC_ART,
                mechanicSummary = "No clear unique TCG card-frame mechanic detected.",
                uniqueFieldsRequired = emptyList(),
                matchedSignals = matchedFrameCues,
            )
        }

        /**
         * Synthesizes valid, tailor-made MSE `style` code + matching `game` field snippet for
         * newly discovered no-template frames with a clear unique mechanic.
         */
        fun synthesizeUniqueUseBetaCode(
            item: DiscoveredBetaFrameItem,
            assessment: UniqueFrameUseAssessment,
            measuredWidthPx: Int? = null,
            measuredHeightPx: Int? = null,
        ): Pair<String, String> {
            val safeShort = item.displayName.replace("\n", " ").trim().take(48).ifBlank { assessment.kind.label }
            val gameId = assessment.kind.targetGameId.ifBlank { item.gameId.ifBlank { "custom-tcg" } }
            val isYgo = gameId == "yugioh"
            val isPkmn = gameId == "pokemon"

            val (cardW, cardH, cardDpi) = when {
                isYgo -> Triple(421, 614, 150)
                isPkmn -> Triple(420, 588, 150)
                measuredWidthPx != null && measuredHeightPx != null && measuredHeightPx > measuredWidthPx ->
                    Triple(measuredWidthPx, measuredHeightPx, 150)
                else -> Triple(375, 523, 150)
            }

            val styleCode = when (assessment.kind) {
                UniqueFrameUseKind.YGO_DARK_SYNCHRO -> """
                    mse version: 2.0.0
                    game: yugioh
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized Dark Synchro]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 32
                    		top: 28
                    		width: 312
                    		height: 36
                    		z index: 2
                    		font:
                    			name: Matrix II
                    			size: 20
                    			color: rgb(245,245,245)
                    	attribute:
                    		left: 350
                    		top: 26
                    		width: 40
                    		height: 40
                    		z index: 2
                    	dark level:
                    		left: 34
                    		top: 72
                    		width: 352
                    		height: 28
                    		z index: 2
                    		alignment: middle left
                    	image:
                    		left: 49
                    		top: 112
                    		width: 322
                    		height: 322
                    		z index: 1
                    	type:
                    		left: 34
                    		top: 446
                    		width: 353
                    		height: 22
                    		z index: 2
                    		font:
                    			name: ITC Stone Serif
                    			size: 10
                    			color: rgb(25,25,25)
                    	dark synchro materials:
                    		left: 34
                    		top: 468
                    		width: 353
                    		height: 20
                    		z index: 2
                    	rule text:
                    		left: 34
                    		top: 488
                    		width: 353
                    		height: 76
                    		z index: 2
                    		font:
                    			name: ITC Stone Serif
                    			size: 9
                    			color: rgb(20,20,20)
                    	atk:
                    		left: 256
                    		top: 568
                    		width: 58
                    		height: 18
                    		z index: 2
                    	def:
                    		left: 328
                    		top: 568
                    		width: 58
                    		height: 18
                    		z index: 2
                """.trimIndent()

                UniqueFrameUseKind.YGO_LINK_PENDULUM -> """
                    mse version: 2.0.0
                    game: yugioh
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized Link-Pendulum]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 32
                    		top: 28
                    		width: 312
                    		height: 36
                    		z index: 2
                    		font:
                    			name: Matrix II
                    			size: 20
                    			color: rgb(255,255,255)
                    	attribute:
                    		left: 350
                    		top: 26
                    		width: 40
                    		height: 40
                    		z index: 2
                    	link markers:
                    		left: 28
                    		top: 90
                    		width: 365
                    		height: 300
                    		z index: 3
                    	image:
                    		left: 40
                    		top: 102
                    		width: 341
                    		height: 274
                    		z index: 1
                    	pendulum left scale:
                    		left: 28
                    		top: 382
                    		width: 36
                    		height: 62
                    		z index: 3
                    	pendulum right scale:
                    		left: 357
                    		top: 382
                    		width: 36
                    		height: 62
                    		z index: 3
                    	pendulum text:
                    		left: 68
                    		top: 382
                    		width: 285
                    		height: 64
                    		z index: 2
                    	type:
                    		left: 34
                    		top: 452
                    		width: 353
                    		height: 22
                    		z index: 2
                    	rule text:
                    		left: 34
                    		top: 476
                    		width: 353
                    		height: 88
                    		z index: 2
                    	atk:
                    		left: 242
                    		top: 568
                    		width: 62
                    		height: 18
                    		z index: 2
                    	link rating:
                    		left: 318
                    		top: 568
                    		width: 68
                    		height: 18
                    		z index: 2
                """.trimIndent()

                UniqueFrameUseKind.YGO_HYBRID_PENDULUM -> """
                    mse version: 2.0.0
                    game: yugioh
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized Extra-Deck Pendulum]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 32
                    		top: 28
                    		width: 312
                    		height: 36
                    		z index: 2
                    	attribute:
                    		left: 350
                    		top: 26
                    		width: 40
                    		height: 40
                    		z index: 2
                    	rank or level:
                    		left: 34
                    		top: 72
                    		width: 352
                    		height: 24
                    		z index: 2
                    	image:
                    		left: 38
                    		top: 102
                    		width: 345
                    		height: 274
                    		z index: 1
                    	pendulum left scale:
                    		left: 28
                    		top: 382
                    		width: 36
                    		height: 62
                    		z index: 3
                    	pendulum right scale:
                    		left: 357
                    		top: 382
                    		width: 36
                    		height: 62
                    		z index: 3
                    	pendulum text:
                    		left: 68
                    		top: 382
                    		width: 285
                    		height: 64
                    		z index: 2
                    	hybrid extra type:
                    		left: 34
                    		top: 452
                    		width: 353
                    		height: 22
                    		z index: 2
                    	rule text:
                    		left: 34
                    		top: 476
                    		width: 353
                    		height: 88
                    		z index: 2
                    	atk:
                    		left: 256
                    		top: 568
                    		width: 58
                    		height: 18
                    		z index: 2
                    	def:
                    		left: 328
                    		top: 568
                    		width: 58
                    		height: 18
                    		z index: 2
                """.trimIndent()

                UniqueFrameUseKind.YGO_MAXIMUM_MONSTER -> """
                    mse version: 2.0.0
                    game: yugioh
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized Rush Maximum]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 28
                    		top: 26
                    		width: 318
                    		height: 34
                    		z index: 2
                    	attribute:
                    		left: 352
                    		top: 24
                    		width: 42
                    		height: 42
                    		z index: 2
                    	image:
                    		left: 26
                    		top: 72
                    		width: 369
                    		height: 348
                    		z index: 1
                    	rush level:
                    		left: 28
                    		top: 392
                    		width: 64
                    		height: 36
                    		z index: 3
                    	maximum piece slot:
                    		left: 98
                    		top: 396
                    		width: 96
                    		height: 24
                    		z index: 3
                    	maximum atk:
                    		left: 202
                    		top: 392
                    		width: 192
                    		height: 30
                    		z index: 3
                    	type:
                    		left: 30
                    		top: 432
                    		width: 360
                    		height: 22
                    		z index: 2
                    	rush requirement:
                    		left: 30
                    		top: 458
                    		width: 360
                    		height: 48
                    		z index: 2
                    	rush effect:
                    		left: 30
                    		top: 510
                    		width: 360
                    		height: 56
                    		z index: 2
                    	rule text:
                    		left: 30
                    		top: 458
                    		width: 360
                    		height: 108
                    		z index: 1
                    	atk:
                    		left: 256
                    		top: 572
                    		width: 58
                    		height: 20
                    		z index: 2
                    	def:
                    		left: 328
                    		top: 572
                    		width: 58
                    		height: 20
                    		z index: 2
                """.trimIndent()

                UniqueFrameUseKind.YGO_RUSH_DUEL_UNIQUE -> """
                    mse version: 2.0.0
                    game: yugioh
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized Rush / Over-Rush]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 28
                    		top: 26
                    		width: 318
                    		height: 34
                    		z index: 2
                    	attribute:
                    		left: 352
                    		top: 24
                    		width: 42
                    		height: 42
                    		z index: 2
                    	legend badge:
                    		left: 28
                    		top: 74
                    		width: 84
                    		height: 24
                    		z index: 3
                    	image:
                    		left: 26
                    		top: 72
                    		width: 369
                    		height: 352
                    		z index: 1
                    	rush level:
                    		left: 28
                    		top: 396
                    		width: 68
                    		height: 34
                    		z index: 3
                    	type:
                    		left: 30
                    		top: 436
                    		width: 360
                    		height: 22
                    		z index: 2
                    	rush requirement:
                    		left: 30
                    		top: 462
                    		width: 360
                    		height: 46
                    		z index: 2
                    	rush effect:
                    		left: 30
                    		top: 512
                    		width: 360
                    		height: 54
                    		z index: 2
                    	rule text:
                    		left: 30
                    		top: 462
                    		width: 360
                    		height: 104
                    		z index: 1
                    	atk:
                    		left: 256
                    		top: 572
                    		width: 58
                    		height: 20
                    		z index: 2
                    	def:
                    		left: 328
                    		top: 572
                    		width: 58
                    		height: 20
                    		z index: 2
                """.trimIndent()

                UniqueFrameUseKind.YGO_NOVEL_MONSTER_MECHANIC -> """
                    mse version: 2.0.0
                    game: yugioh
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized Custom YGO Mechanic]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 32
                    		top: 28
                    		width: 312
                    		height: 36
                    		z index: 2
                    	attribute:
                    		left: 350
                    		top: 26
                    		width: 40
                    		height: 40
                    		z index: 2
                    	custom summon gauge:
                    		left: 34
                    		top: 70
                    		width: 240
                    		height: 26
                    		z index: 2
                    	mechanic badge:
                    		left: 282
                    		top: 70
                    		width: 104
                    		height: 26
                    		z index: 3
                    	image:
                    		left: 49
                    		top: 110
                    		width: 322
                    		height: 322
                    		z index: 1
                    	type:
                    		left: 34
                    		top: 444
                    		width: 353
                    		height: 22
                    		z index: 2
                    	custom materials:
                    		left: 34
                    		top: 468
                    		width: 353
                    		height: 20
                    		z index: 2
                    	rule text:
                    		left: 34
                    		top: 490
                    		width: 353
                    		height: 74
                    		z index: 2
                    	atk:
                    		left: 256
                    		top: 568
                    		width: 58
                    		height: 18
                    		z index: 2
                    	def:
                    		left: 328
                    		top: 568
                    		width: 58
                    		height: 18
                    		z index: 2
                """.trimIndent()

                UniqueFrameUseKind.MTG_UNIQUE_MECHANIC -> """
                    mse version: 2.0.0
                    game: magic
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized MTG Mechanic]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 30
                    		top: 26
                    		width: 240
                    		height: 28
                    		z index: 2
                    	casting cost:
                    		left: 274
                    		top: 26
                    		width: 72
                    		height: 28
                    		z index: 2
                    	image:
                    		left: 29
                    		top: 60
                    		width: 316
                    		height: 224
                    		z index: 1
                    	type:
                    		left: 30
                    		top: 290
                    		width: 314
                    		height: 26
                    		z index: 2
                    	mechanic chapter or mode:
                    		left: 30
                    		top: 320
                    		width: 314
                    		height: 34
                    		z index: 2
                    	rule text:
                    		left: 30
                    		top: 358
                    		width: 314
                    		height: 120
                    		z index: 2
                    	pt:
                    		left: 286
                    		top: 468
                    		width: 60
                    		height: 28
                    		z index: 3
                """.trimIndent()

                UniqueFrameUseKind.POKEMON_UNIQUE_MECHANIC -> """
                    mse version: 2.0.0
                    game: pokemon
                    short name: $safeShort (Beta)
                    full name: $safeShort [BETA Auto-Synthesized Pokémon Mechanic]
                    card width: $cardW
                    card height: $cardH
                    card dpi: $cardDpi
                    card style:
                    	name:
                    		left: 30
                    		top: 26
                    		width: 240
                    		height: 30
                    		z index: 2
                    	hp:
                    		left: 276
                    		top: 26
                    		width: 72
                    		height: 30
                    		z index: 2
                    	energy type:
                    		left: 354
                    		top: 24
                    		width: 38
                    		height: 38
                    		z index: 2
                    	image:
                    		left: 34
                    		top: 66
                    		width: 352
                    		height: 230
                    		z index: 1
                    	special mechanic rule:
                    		left: 34
                    		top: 304
                    		width: 352
                    		height: 54
                    		z index: 2
                    	rule text:
                    		left: 34
                    		top: 364
                    		width: 352
                    		height: 146
                    		z index: 2
                    	weakness resistance retreat:
                    		left: 34
                    		top: 520
                    		width: 352
                    		height: 36
                    		z index: 2
                """.trimIndent()

                else -> {
                    val scaleX = cardW / 375f
                    val scaleY = cardH / 523f
                    fun sx(v: Int) = (v * scaleX).toInt().coerceAtLeast(1)
                    fun sy(v: Int) = (v * scaleY).toInt().coerceAtLeast(1)
                    """
                        mse version: 2.0.0
                        game: $gameId
                        short name: $safeShort (Beta)
                        full name: $safeShort [BETA Auto-Synthesized Custom TCG Frame]
                        card width: $cardW
                        card height: $cardH
                        card dpi: $cardDpi
                        card style:
                        	name:
                        		left: ${sx(28)}
                        		top: ${sy(24)}
                        		width: ${sx(238)}
                        		height: ${sy(30)}
                        		z index: 2
                        	cost:
                        		left: ${sx(272)}
                        		top: ${sy(24)}
                        		width: ${sx(75)}
                        		height: ${sy(30)}
                        		z index: 2
                        	image:
                        		left: ${sx(28)}
                        		top: ${sy(62)}
                        		width: ${sx(319)}
                        		height: ${sy(224)}
                        		z index: 1
                        	type:
                        		left: ${sx(28)}
                        		top: ${sy(292)}
                        		width: ${sx(220)}
                        		height: ${sy(26)}
                        		z index: 2
                        	mechanic stat:
                        		left: ${sx(254)}
                        		top: ${sy(292)}
                        		width: ${sx(93)}
                        		height: ${sy(26)}
                        		z index: 2
                        	rule text:
                        		left: ${sx(28)}
                        		top: ${sy(326)}
                        		width: ${sx(319)}
                        		height: ${sy(142)}
                        		z index: 2
                        	power:
                        		left: ${sx(282)}
                        		top: ${sy(474)}
                        		width: ${sx(65)}
                        		height: ${sy(28)}
                        		z index: 3
                    """.trimIndent()
                }
            }

            val gameSnippet = buildString {
                appendLine("mse version: 2.0.0")
                appendLine("# Auto-synthesized custom mechanic game field snippet for ${assessment.kind.label}")
                appendLine("short name: $safeShort")
                appendLine("full name: $safeShort (${assessment.kind.label})")
                assessment.uniqueFieldsRequired.forEach { fieldName ->
                    appendLine("card field:")
                    appendLine("\ttype: text")
                    appendLine("\tname: $fieldName")
                    appendLine("\tdescription: Unique mechanic field for ${assessment.kind.label}")
                }
            }.trimEnd()

            return styleCode to gameSnippet
        }

        /**
         * Prepends compact inline `#` MSE code comments with the fan-made source trail directly
         * into `rawStyleCode` so creator permission & rules retracing travels inside the code itself.
         */
        fun embedFanSourceTrailComments(
            item: DiscoveredBetaFrameItem,
            assessment: UniqueFrameUseAssessment,
            rawStyleCode: String,
        ): String {
            if (!item.isFanMade()) return rawStyleCode
            val urls = item.collectedSourceUrls()
            val creator = item.authorCredit.trim().ifBlank { "Unknown / See Source URL(s)" }
            val cardTypeDesc = if (assessment.hasClearUniqueUse) {
                "${assessment.kind.label} (${item.gameId.ifBlank { assessment.kind.targetGameId }})"
            } else {
                "${item.category.label} (${item.gameId.ifBlank { "custom" }})"
            }

            val header = buildString {
                appendLine("# [BETA • FAN-MADE SOURCE TRAIL — CODE-ONLY FOR PERMISSION & RULES RETRACING]")
                appendLine("# Card Type: $cardTypeDesc")
                appendLine("# Creator (Permission): $creator")
                appendLine("# Origin Classification: ${item.effectiveStatusBadge()}")
                appendLine("# Source Feed: ${item.sourceFeedLabel}")
                urls.forEach { url ->
                    appendLine("# Rules/Source URL: $url")
                }
            }.trimEnd()

            return if (rawStyleCode.contains("# [BETA • FAN-MADE SOURCE TRAIL")) {
                rawStyleCode
            } else {
                "$header\n$rawStyleCode"
            }
        }

        /**
         * Builds the complete downloadable `.mse-style-beta.txt` package, enforcing the mandatory
         * code-only source trail on 100% of fan-made frames (verbatim upstream AND auto-synthesized).
         */
        fun buildDownloadableBetaCodeText(
            item: DiscoveredBetaFrameItem,
            assessment: UniqueFrameUseAssessment,
            rawStyleCode: String,
            rawGameCode: String?,
            wasAutoSynthesized: Boolean,
            fetchedAtIso: String = currentUtcIso(),
        ): BetaFrameCodePackage {
            if (item.isFanMade() && !item.hasRequiredFanSources()) {
                throw IllegalStateException(FAN_SOURCE_REQUIRED_ERROR)
            }

            val linkedUrls = item.collectedSourceUrls()
            val styleCodeWithTrail = embedFanSourceTrailComments(item, assessment, rawStyleCode)
            val parsed = MseStyleParser.parseStyleText(styleCodeWithTrail, item.folderName.ifBlank { item.id })
            val geometrySummary = "${parsed.cardWidthPx}×${parsed.cardHeightPx} px @ ${parsed.cardDpi} DPI • " +
                "${parsed.hitboxes.size} hitboxes • " +
                if (parsed.hasValidCompositeGeometry()) "valid geometry" else "scripted/partial geometry"

            val formatted = buildString {
                appendLine("======================================================================")
                appendLine("TCG STUDIO — BETA CARD FRAME CODE PACKAGE")
                appendLine("======================================================================")
                appendLine("Frame Name: ${item.displayName}")
                appendLine("Status Badge: ${item.effectiveStatusBadge()}")
                appendLine("Category: ${item.category.label} | Game ID: ${item.gameId}")
                appendLine("Source Feed: ${item.sourceFeedLabel}")
                appendLine("Code Mode: ${if (wasAutoSynthesized) "AUTO-SYNTHESIZED FOR UNIQUE MECHANIC (NO UPSTREAM TEMPLATE)" else "VERBATIM UPSTREAM MSE TEMPLATE"}")
                appendLine("Parsed Geometry: $geometrySummary")
                appendLine("Fetched At (UTC): $fetchedAtIso")
                if (assessment.hasClearUniqueUse) {
                    appendLine("Detected Unique Mechanic: ${assessment.kind.label}")
                    appendLine("Mechanic Summary: ${assessment.mechanicSummary}")
                    if (assessment.uniqueFieldsRequired.isNotEmpty()) {
                        appendLine("Unique Fields: ${assessment.uniqueFieldsRequired.joinToString(", ")}")
                    }
                }
                appendLine()

                if (item.isFanMade()) {
                    appendLine("=== FAN-MADE SOURCE TRAIL (PERMISSION & RULES RETRACING) ===")
                    appendLine("Card Type / Detected Mechanic: ${if (assessment.hasClearUniqueUse) assessment.kind.label else item.category.label} (${item.gameId})")
                    appendLine("Creator / Author (For Permission Contact): ${item.authorCredit.trim().ifBlank { "See Source URL(s) below" }}")
                    appendLine("Origin Classification: ${item.effectiveStatusBadge()}")
                    appendLine("Linked Source URLs (${linkedUrls.size}):")
                    linkedUrls.forEachIndexed { idx, url ->
                        appendLine("  ${idx + 1}. $url")
                    }
                    appendLine("=== END FAN-MADE SOURCE TRAIL ===")
                    appendLine()
                }

                appendLine("=== BEGIN MSE STYLE CODE (style) ===")
                appendLine(styleCodeWithTrail.trimEnd())
                appendLine("=== END MSE STYLE CODE ===")

                if (!rawGameCode.isNullOrBlank()) {
                    appendLine()
                    appendLine("=== BEGIN CUSTOM MECHANIC GAME FIELDS (game snippet) ===")
                    appendLine(rawGameCode.trimEnd())
                    appendLine("=== END CUSTOM MECHANIC GAME FIELDS ===")
                }
            }

            return BetaFrameCodePackage(
                item = item,
                fetchedAtIso = fetchedAtIso,
                wasAutoSynthesizedForUniqueUse = wasAutoSynthesized,
                uniqueUseAssessment = assessment,
                linkedSourceUrls = linkedUrls,
                rawStyleCode = styleCodeWithTrail,
                rawGameCode = rawGameCode,
                parsedGeometrySummary = geometrySummary,
                formattedDownloadableText = formatted,
            )
        }

        /**
         * Parses RSS 2.0 (DeviantArt, Itch.io, OpenGameArt) and Atom 1.0 (Reddit) feeds using
         * `XmlPullParser` when available, with a seamless JDK DOM fallback for pure JVM unit tests.
         */
        fun parseRssOrAtomXml(
            xmlString: String,
            feed: BetaFrameSourceFeed,
        ): List<DiscoveredBetaFrameItem> {
            val cleanedXml = xmlString.trim().removePrefix("\uFEFF")
            if (cleanedXml.isBlank()) return emptyList()

            val pullParsed = tryParseWithXmlPullParser(cleanedXml, feed)
            if (pullParsed != null) return pullParsed

            return parseWithDomFallback(cleanedXml, feed)
        }

        private fun tryParseWithXmlPullParser(
            xmlString: String,
            feed: BetaFrameSourceFeed,
        ): List<DiscoveredBetaFrameItem>? {
            val parser = runCatching {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                factory.newPullParser()
            }.getOrNull() ?: return null

            return runCatching {
                parser.setInput(StringReader(xmlString))
                val results = mutableListOf<DiscoveredBetaFrameItem>()
                var inRecord = false
                var inAuthorBlock = false
                var title = ""
                var link = ""
                var guid = ""
                var description = ""
                var author = ""
                var thumbnail: String? = null
                var pubDate = ""

                fun flushRecord() {
                    if (title.isBlank() && link.isBlank()) return
                    results += buildRssDiscoveredItem(
                        feed = feed,
                        title = title,
                        link = link,
                        guid = guid,
                        description = description,
                        author = author,
                        thumbnail = thumbnail,
                        pubDate = pubDate,
                    )
                }

                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val rawName = parser.name.orEmpty().lowercase(Locale.US)
                            val localName = rawName.substringAfter(':')
                            when {
                                localName == "item" || localName == "entry" -> {
                                    inRecord = true
                                    inAuthorBlock = false
                                    title = ""
                                    link = ""
                                    guid = ""
                                    description = ""
                                    author = ""
                                    thumbnail = null
                                    pubDate = ""
                                }

                                inRecord && localName == "title" -> {
                                    title = safeNextText(parser).trim()
                                }

                                inRecord && localName == "link" -> {
                                    val hrefAttr = parser.getAttributeValue(null, "href").orEmpty().trim()
                                    val relAttr = parser.getAttributeValue(null, "rel").orEmpty().trim()
                                    val textVal = safeNextText(parser).trim()
                                    if (hrefAttr.isNotBlank() && (relAttr.isBlank() || relAttr == "alternate" || link.isBlank())) {
                                        link = hrefAttr
                                    } else if (textVal.isNotBlank() && link.isBlank()) {
                                        link = textVal
                                    }
                                }

                                inRecord && (localName == "guid" || localName == "id") -> {
                                    guid = safeNextText(parser).trim()
                                }

                                inRecord && (rawName == "media:content" || rawName == "media:thumbnail" || localName == "enclosure") -> {
                                    val mediaUrl = parser.getAttributeValue(null, "url").orEmpty().trim()
                                    val medium = parser.getAttributeValue(null, "medium").orEmpty()
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    val looksImage = medium.equals("image", true) ||
                                        type.startsWith("image/", true) ||
                                        rawName == "media:thumbnail" ||
                                        mediaUrl.endsWith(".png", true) ||
                                        mediaUrl.endsWith(".jpg", true) ||
                                        mediaUrl.endsWith(".jpeg", true) ||
                                        mediaUrl.endsWith(".webp", true)
                                    if (mediaUrl.startsWith("http", ignoreCase = true) && looksImage) {
                                        if (thumbnail == null || rawName == "media:content") {
                                            thumbnail = mediaUrl
                                        }
                                    }
                                }

                                inRecord && (localName == "description" || localName == "summary" || (localName == "content" && rawName != "media:content")) -> {
                                    val bodyText = safeNextText(parser)
                                    if (description.isBlank() || bodyText.length > description.length) {
                                        description = stripHtml(bodyText)
                                    }
                                }

                                inRecord && (rawName == "dc:creator" || rawName == "media:credit" || localName == "creator") -> {
                                    val credit = safeNextText(parser).trim()
                                    if (credit.isNotBlank() && author.isBlank()) {
                                        author = credit
                                    }
                                }

                                inRecord && localName == "author" -> {
                                    inAuthorBlock = true
                                }

                                inRecord && inAuthorBlock && localName == "name" -> {
                                    val nameVal = safeNextText(parser).trim()
                                    if (nameVal.isNotBlank() && author.isBlank()) {
                                        author = nameVal
                                    }
                                }

                                inRecord && (localName == "pubdate" || localName == "updated" || localName == "published") -> {
                                    val dt = safeNextText(parser).trim()
                                    if (dt.isNotBlank() && pubDate.isBlank()) pubDate = dt
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            val localName = parser.name.orEmpty().lowercase(Locale.US).substringAfter(':')
                            if (localName == "author") {
                                inAuthorBlock = false
                            } else if (localName == "item" || localName == "entry") {
                                flushRecord()
                                inRecord = false
                            }
                        }
                    }
                    event = parser.next()
                }
                results
            }.onFailure { err ->
                Log.w(TAG, "XmlPullParser failed for ${feed.label}, falling back to DOM: ${err.message}")
            }.getOrNull()
        }

        private fun safeNextText(parser: XmlPullParser): String {
            return runCatching { parser.nextText() }.getOrDefault("")
        }

        private fun parseWithDomFallback(
            xmlString: String,
            feed: BetaFrameSourceFeed,
        ): List<DiscoveredBetaFrameItem> {
            return runCatching {
                val dbf = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }
                val doc = dbf.newDocumentBuilder().parse(
                    ByteArrayInputStream(xmlString.toByteArray(StandardCharsets.UTF_8))
                )
                val results = mutableListOf<DiscoveredBetaFrameItem>()
                val rssNodes = doc.getElementsByTagName("item")
                val atomNodes = doc.getElementsByTagName("entry")
                val recordElements = buildList<Element> {
                    for (i in 0 until rssNodes.length) {
                        (rssNodes.item(i) as? Element)?.let(::add)
                    }
                    for (i in 0 until atomNodes.length) {
                        (atomNodes.item(i) as? Element)?.let(::add)
                    }
                }

                for (el in recordElements) {
                    var title = ""
                    var link = ""
                    var guid = ""
                    var description = ""
                    var author = ""
                    var thumbnail: String? = null
                    var pubDate = ""

                    val children = el.childNodes
                    for (j in 0 until children.length) {
                        val node = children.item(j)
                        if (node.nodeType != Node.ELEMENT_NODE) continue
                        val child = node as Element
                        val rawName = child.tagName.lowercase(Locale.US)
                        val local = rawName.substringAfter(':')
                        when {
                            local == "title" -> title = child.textContent.orEmpty().trim()
                            local == "link" -> {
                                val href = child.getAttribute("href").trim()
                                val text = child.textContent.orEmpty().trim()
                                if (href.isNotBlank() && link.isBlank()) link = href
                                else if (text.isNotBlank() && link.isBlank()) link = text
                            }
                            local == "guid" || local == "id" -> guid = child.textContent.orEmpty().trim()
                            rawName == "media:content" || rawName == "media:thumbnail" || local == "enclosure" -> {
                                val urlAttr = child.getAttribute("url").trim()
                                if (urlAttr.startsWith("http", ignoreCase = true)) {
                                    if (thumbnail == null || rawName == "media:content") thumbnail = urlAttr
                                }
                            }
                            local == "description" || local == "summary" || (local == "content" && rawName != "media:content") -> {
                                val stripped = stripHtml(child.textContent.orEmpty())
                                if (stripped.length > description.length) description = stripped
                            }
                            rawName == "dc:creator" || rawName == "media:credit" || local == "creator" -> {
                                val t = child.textContent.orEmpty().trim()
                                if (t.isNotBlank() && author.isBlank()) author = t
                            }
                            local == "author" -> {
                                val nameNodes = child.getElementsByTagName("name")
                                val candidate = if (nameNodes.length > 0) {
                                    nameNodes.item(0)?.textContent.orEmpty().trim()
                                } else {
                                    child.textContent.orEmpty().trim()
                                }
                                if (candidate.isNotBlank() && author.isBlank()) author = candidate
                            }
                            local == "pubdate" || local == "updated" || local == "published" -> {
                                val dt = child.textContent.orEmpty().trim()
                                if (dt.isNotBlank() && pubDate.isBlank()) pubDate = dt
                            }
                        }
                    }

                    if (title.isNotBlank() || link.isNotBlank()) {
                        results += buildRssDiscoveredItem(
                            feed = feed,
                            title = title,
                            link = link,
                            guid = guid,
                            description = description,
                            author = author,
                            thumbnail = thumbnail,
                            pubDate = pubDate,
                        )
                    }
                }
                results
            }.onFailure { err ->
                Log.w(TAG, "DOM RSS/Atom fallback failed for ${feed.label}: ${err.message}")
            }.getOrDefault(emptyList())
        }

        private fun buildRssDiscoveredItem(
            feed: BetaFrameSourceFeed,
            title: String,
            link: String,
            guid: String,
            description: String,
            author: String,
            thumbnail: String?,
            pubDate: String,
        ): DiscoveredBetaFrameItem {
            val cleanTitle = stripHtml(title).ifBlank { "Untitled Fan Frame" }
            val cleanAuthor = stripHtml(author).removePrefix("/u/").trim()
            val cleanDesc = stripHtml(description).take(280)
            val inferredGame = inferGameIdFromText("$cleanTitle $cleanDesc", feed.category)
            val stableId = "rss_${sanitizeKey(feed.id)}_${sanitizeKey(guid.ifBlank { link.ifBlank { cleanTitle } }).take(44)}"

            val base = DiscoveredBetaFrameItem(
                id = stableId,
                displayName = cleanTitle,
                folderName = "${sanitizeKey(cleanTitle).take(36)}.mse-style",
                gameId = inferredGame,
                category = feed.category,
                originTrust = feed.originTrust,
                isAlwaysBeta = true,
                sourceFeedLabel = feed.label,
                sourceFeedUrl = feed.feedUrl,
                authorCredit = cleanAuthor,
                sourceWebUrl = link.ifBlank { feed.feedUrl },
                previewThumbnailUrl = thumbnail,
                descriptionSnippet = cleanDesc,
                latestShaOrGuid = guid.ifBlank { pubDate.ifBlank { link } },
                hasUpstreamTemplateFile = false,
            )
            return base.copy(uniqueUseAssessment = assessUniqueFrameUse(base))
        }

        private fun inferGameIdFromText(text: String, category: BetaFrameCategory): String {
            val low = text.lowercase(Locale.US)
            return when {
                category == BetaFrameCategory.FAN_YGO_CUSTOM ||
                    low.contains("yugioh") || low.contains("yu-gi-oh") || low.contains("ygo") ||
                    low.contains("pendulum") || low.contains("synchro") || low.contains("rush duel") ||
                    low.contains("edopro") || low.contains("dataeditorx") -> "yugioh"
                low.contains("pokemon") || low.contains("pokémon") || low.contains("pkmn") -> "pokemon"
                low.contains("mtg") || low.contains("magic") || low.contains("planeswalker") ||
                    low.contains("cardconjurer") || low.contains("proxyshop") -> "magic"
                else -> "custom-tcg"
            }
        }

        private fun stripHtml(raw: String): String =
            raw.replace(HTML_TAG_REGEX, " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace(WHITESPACE_REGEX, " ")
                .trim()

        internal fun sanitizeKey(raw: String): String =
            raw.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9._-]+"), "_")
                .trim('_')
                .ifBlank { "beta_frame" }

        private fun currentUtcIso(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date())
        }
    }
}
