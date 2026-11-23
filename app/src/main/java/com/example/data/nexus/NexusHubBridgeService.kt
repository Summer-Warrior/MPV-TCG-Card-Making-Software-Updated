package com.example.data.nexus

import android.util.Base64
import com.example.domain.nexus.MutualLinkStatus
import com.example.domain.nexus.NexusClientCodestringIndex
import com.example.domain.nexus.NexusHubChangelogDiff
import com.example.domain.nexus.NexusRepoLinkManifest
import com.example.domain.nexus.NexusSchemaValidator
import com.example.domain.nexus.RemoteHubLinkManifest
import com.example.domain.nexus.SchemaValidationResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/**
 * Live GitHub REST API service for the Bidirectional Sync Bridge between
 * MPV TCG Card Making Software <-> Nexus Master Hub.
 *
 * Uses GitHub API header `X-GitHub-Api-Version: 2026-03-10` and `Accept: application/vnd.github+json`.
 * Never hardcodes tokens and never logs or persists PATs without explicit user opt-in.
 */
class NexusHubBridgeService(
    private val githubOkHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    companion object {
        const val GITHUB_API_BASE = "https://api.github.com"
        const val GITHUB_API_VERSION = "2026-03-10"
        const val GITHUB_ACCEPT = "application/vnd.github+json"
        const val USER_AGENT = "MPV-TCG-Card-Maker/5.0"
        private const val LINK_MANIFEST_PATH = "nexus-repo-link.json"
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val contentResponseAdapter = moshi.adapter(GitHubContentFileResponse::class.java)
    private val putRequestAdapter = moshi.adapter(GitHubPutContentRequest::class.java)
    private val putResponseAdapter = moshi.adapter(GitHubPutContentResponse::class.java)
    private val repoLinkAdapter = moshi.adapter(NexusRepoLinkManifest::class.java)
    private val remoteHubLinkAdapter = moshi.adapter(RemoteHubLinkManifest::class.java)

    suspend fun verifyMutualLinkAndPullIndex(
        localManifest: NexusRepoLinkManifest,
        currentAppliedIndex: NexusClientCodestringIndex,
        fineGrainedPat: String?,
    ): HubHandshakeResult = withContext(Dispatchers.IO) {
        when (val validation = NexusSchemaValidator.validateRepoLinkManifest(localManifest)) {
            is SchemaValidationResult.Invalid -> {
                return@withContext HubHandshakeResult(
                    status = MutualLinkStatus.UNLINKED,
                    detail = "Invalid local manifest: ${validation.errors.joinToString(" ")}",
                    thisRepoHasManifest = false,
                    hubRepoHasManifest = false,
                    hubPointsBackToThisRepo = false,
                    pulledIndex = null,
                    changelogDiff = null,
                    checkedAtIso = currentUtcIso(),
                )
            }
            is SchemaValidationResult.Valid -> Unit
        }

        // 1. Check thisRepo/nexus-repo-link.json on GitHub
        val thisRepoFile = fetchGitHubFileUtf8(
            repoSlug = localManifest.thisRepo,
            branch = localManifest.thisBranch,
            path = LINK_MANIFEST_PATH,
            pat = fineGrainedPat,
        )
        val parsedThisRemote = (thisRepoFile as? GitHubFileFetch.Found)
            ?.contentUtf8
            ?.let { raw -> runCatching { repoLinkAdapter.fromJson(raw) }.getOrNull() }
        val thisPointsToHub = parsedThisRemote?.linkedHubRepo
            ?.equals(localManifest.linkedHubRepo, ignoreCase = true) == true

        // 2. Check linkedHubRepo/nexus-repo-link.json on GitHub
        val hubLinkFile = fetchGitHubFileUtf8(
            repoSlug = localManifest.linkedHubRepo,
            branch = localManifest.linkedHubBranch,
            path = LINK_MANIFEST_PATH,
            pat = fineGrainedPat,
        )
        val parsedHubLink = (hubLinkFile as? GitHubFileFetch.Found)
            ?.contentUtf8
            ?.let { raw -> runCatching { remoteHubLinkAdapter.fromJson(raw) }.getOrNull() }

        val hubPointsBack = parsedHubLink != null && (
            parsedHubLink.linkedCardMakerRepo.equals(localManifest.thisRepo, ignoreCase = true) ||
                parsedHubLink.linkedRepo.equals(localManifest.thisRepo, ignoreCase = true) ||
                parsedHubLink.linkedRepos.any { it.equals(localManifest.thisRepo, ignoreCase = true) }
            )

        // 3. Pull nexus-bridge/client-codestring-index.json from linkedHubRepo
        val hubIndexFetch = fetchGitHubFileUtf8(
            repoSlug = localManifest.linkedHubRepo,
            branch = localManifest.linkedHubBranch,
            path = localManifest.hubIndexPath,
            pat = fineGrainedPat,
        )

        var pulledIndex: NexusClientCodestringIndex? = null
        var diff: NexusHubChangelogDiff? = null
        var indexNote = ""

        when (hubIndexFetch) {
            is GitHubFileFetch.Found -> {
                when (val parsed = NexusSchemaValidator.parseAndValidateIndex(moshi, hubIndexFetch.contentUtf8)) {
                    is SchemaValidationResult.Valid -> {
                        pulledIndex = parsed.value
                        diff = NexusSchemaValidator.computeChangelogDiff(currentAppliedIndex, parsed.value)
                        indexNote = if (diff.hasChanges) {
                            "Pulled Hub index rev ${parsed.value.indexRevision} (${diff.totalChangeCount} change(s) ready to review)."
                        } else {
                            "Pulled Hub index rev ${parsed.value.indexRevision} (matches local applied index)."
                        }
                    }
                    is SchemaValidationResult.Invalid -> {
                        indexNote = "Hub index failed runtime schema validation: ${parsed.errors.joinToString("; ")}"
                    }
                }
            }
            is GitHubFileFetch.NotFound -> {
                indexNote = "Hub index not yet published at ${localManifest.linkedHubRepo}/${localManifest.hubIndexPath} (HTTP 404)."
            }
            is GitHubFileFetch.Error -> {
                indexNote = "Could not fetch Hub index: ${hubIndexFetch.message}"
            }
        }

        // 4. Compute canonical MutualLinkStatus
        val hasLocalToHub = thisPointsToHub || localManifest.linkedHubRepo.isNotBlank()
        val bothRepoManifestsExist = (thisRepoFile is GitHubFileFetch.Found) && (hubLinkFile is GitHubFileFetch.Found)
        val status = when {
            thisRepoFile is GitHubFileFetch.Error && hubLinkFile is GitHubFileFetch.Error ->
                MutualLinkStatus.UNLINKED

            !thisPointsToHub && !hubPointsBack ->
                MutualLinkStatus.UNLINKED

            (thisPointsToHub && !hubPointsBack) || (!thisPointsToHub && hubPointsBack) ->
                MutualLinkStatus.ONE_WAY_ONLY

            bothRepoManifestsExist && thisPointsToHub && hubPointsBack -> {
                val schemaMismatch = !parsedHubLink?.bridgeSchemaVersion.isNullOrBlank() &&
                    parsedHubLink?.bridgeSchemaVersion != localManifest.bridgeSchemaVersion
                val indexOutOfSync = diff?.hasChanges == true ||
                    (!parsedHubLink?.indexRevision.isNullOrBlank() &&
                        parsedHubLink?.indexRevision != currentAppliedIndex.indexRevision)
                if (schemaMismatch || indexOutOfSync) {
                    MutualLinkStatus.LINKED_OUT_OF_SYNC
                } else {
                    MutualLinkStatus.LINKED_AND_CURRENT
                }
            }

            hasLocalToHub && hubPointsBack -> MutualLinkStatus.LINKED_OUT_OF_SYNC
            else -> MutualLinkStatus.UNLINKED
        }

        val statusSummary = buildString {
            append(
                when (status) {
                    MutualLinkStatus.UNLINKED ->
                        "Neither repo confirms a mutual nexus-repo-link.json handshake yet."
                    MutualLinkStatus.ONE_WAY_ONLY ->
                        if (thisPointsToHub) {
                            "${localManifest.thisRepo} points to ${localManifest.linkedHubRepo}, but the Hub does not link back yet."
                        } else {
                            "${localManifest.linkedHubRepo} points to this repo, but ${localManifest.thisRepo}/nexus-repo-link.json is not committed yet."
                        }
                    MutualLinkStatus.LINKED_OUT_OF_SYNC ->
                        "Mutual link verified — updates or schema sync pending between repos."
                    MutualLinkStatus.LINKED_AND_CURRENT ->
                        "Mutual link verified on both repositories and client index is current."
                }
            )
            if (indexNote.isNotBlank()) {
                append(" ")
                append(indexNote)
            }
        }

        HubHandshakeResult(
            status = status,
            detail = statusSummary,
            thisRepoHasManifest = thisRepoFile is GitHubFileFetch.Found,
            hubRepoHasManifest = hubLinkFile is GitHubFileFetch.Found,
            hubPointsBackToThisRepo = hubPointsBack,
            pulledIndex = pulledIndex,
            changelogDiff = diff,
            checkedAtIso = currentUtcIso(),
        )
    }

    /**
     * Direct GitHub Contents API commit (GET-SHA + Base64 PUT) with automatic 409/422 conflict retry.
     * Caller MUST enforce mutual link verification and non-locked client firewall before calling.
     */
    suspend fun commitFileToGitHub(
        repoSlug: String,
        branch: String,
        repoFilePath: String,
        contentUtf8: String,
        commitMessage: String,
        fineGrainedPat: String,
    ): GitHubCommitResult = withContext(Dispatchers.IO) {
        val cleanPat = fineGrainedPat.trim()
        if (cleanPat.isEmpty()) {
            return@withContext GitHubCommitResult.Failure(
                httpCode = null,
                message = "A Fine-Grained GitHub PAT (Contents: Read & Write) is required for Direct Commit.",
            )
        }
        if (!NexusSchemaValidator.validateRepoSlug(repoSlug) || !NexusSchemaValidator.isSafeRepoPath(repoFilePath)) {
            return@withContext GitHubCommitResult.Failure(
                httpCode = null,
                message = "Invalid repository slug '$repoSlug' or file path '$repoFilePath'.",
            )
        }

        val encodedBase64 = Base64.encodeToString(contentUtf8.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        var recoveredFromConflict = false

        repeat(2) { attempt ->
            val currentSha = when (val existing = fetchGitHubFileUtf8(repoSlug, branch, repoFilePath, cleanPat)) {
                is GitHubFileFetch.Found -> existing.sha
                is GitHubFileFetch.NotFound -> null
                is GitHubFileFetch.Error -> {
                    return@withContext GitHubCommitResult.Failure(
                        httpCode = existing.httpCode,
                        message = "Pre-commit SHA lookup failed: ${existing.message}",
                    )
                }
            }

            val putBody = GitHubPutContentRequest(
                message = commitMessage,
                content = encodedBase64,
                branch = branch,
                sha = currentSha,
            )
            val url = buildContentsUrl(repoSlug, repoFilePath, branch = null)
            val request = Request.Builder()
                .url(url)
                .header("Accept", GITHUB_ACCEPT)
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $cleanPat")
                .put(putRequestAdapter.toJson(putBody).toRequestBody(jsonMediaType))
                .build()

            try {
                githubOkHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = runCatching { putResponseAdapter.fromJson(responseBody) }.getOrNull()
                        return@withContext GitHubCommitResult.Success(
                            commitSha = parsed?.commit?.sha.orEmpty(),
                            htmlUrl = parsed?.content?.htmlUrl.orEmpty(),
                            recoveredFromConflict = recoveredFromConflict,
                        )
                    }

                    // Automatic 409 / 422 stale-SHA conflict recovery on first attempt
                    if ((response.code == 409 || response.code == 422) && attempt == 0) {
                        recoveredFromConflict = true
                        return@repeat
                    }

                    return@withContext GitHubCommitResult.Failure(
                        httpCode = response.code,
                        message = "GitHub commit rejected (HTTP ${response.code}): ${responseBody.take(280)}",
                    )
                }
            } catch (io: IOException) {
                return@withContext GitHubCommitResult.Failure(
                    httpCode = null,
                    message = "Network error during GitHub commit: ${io.message ?: io::class.java.simpleName}",
                )
            }
        }

        GitHubCommitResult.Failure(
            httpCode = 409,
            message = "GitHub commit conflict persisted after automatic SHA refresh retry.",
        )
    }

    fun generateWorkflowYaml(manifest: NexusRepoLinkManifest): String = buildString {
        append("name: MPV <-> Nexus Master Hub Bidirectional Sync\n\n")
        append("on:\n")
        append("  workflow_dispatch:\n")
        append("  schedule:\n")
        append("    - cron: \"0 */12 * * *\"\n")
        append("  push:\n")
        append("    branches:\n")
        append("      - ${manifest.thisBranch}\n")
        append("    paths:\n")
        append("      - \"nexus-repo-link.json\"\n")
        append("      - \"${manifest.exportedPacksDir.trimEnd('/')}/**\"\n\n")
        append("permissions:\n")
        append("  contents: write\n\n")
        append("jobs:\n")
        append("  sync-nexus-hub:\n")
        append("    runs-on: ubuntu-latest\n")
        append("    steps:\n")
        append("      - uses: actions/checkout@v4\n")
        append("      - name: Pull Latest Hub Client Index\n")
        append("        env:\n")
        append("          GH_API_VERSION: \"$GITHUB_API_VERSION\"\n")
        append("        run: |\n")
        append("          mkdir -p nexus-bridge\n")
        append("          curl -fsSL \\\n")
        append("            -H \"Accept: $GITHUB_ACCEPT\" \\\n")
        append("            -H \"X-GitHub-Api-Version: \$GH_API_VERSION\" \\\n")
        append("            \"https://raw.githubusercontent.com/${manifest.linkedHubRepo}/${manifest.linkedHubBranch}/${manifest.hubIndexPath}\" \\\n")
        append("            -o nexus-bridge/client-codestring-index.snapshot.json\n")
        append("      - name: Commit Snapshot If Updated\n")
        append("        run: |\n")
        append("          if [ -n \"\$(git status --porcelain nexus-bridge/)\" ]; then\n")
        append("            git config user.name \"github-actions[bot]\"\n")
        append("            git config user.email \"41898282+github-actions[bot]@users.noreply.github.com\"\n")
        append("            git add nexus-bridge/\n")
        append("            git commit -m \"chore(nexus-bridge): sync client-codestring-index from ${manifest.linkedHubRepo}\"\n")
        append("            git push\n")
        append("          fi\n")
    }

    private fun fetchGitHubFileUtf8(
        repoSlug: String,
        branch: String,
        path: String,
        pat: String?,
    ): GitHubFileFetch {
        val url = buildContentsUrl(repoSlug, path, branch)
        val builder = Request.Builder()
            .url(url)
            .header("Accept", GITHUB_ACCEPT)
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", USER_AGENT)
            .get()
        if (!pat.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${pat.trim()}")
        }

        return try {
            githubOkHttpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> GitHubFileFetch.NotFound
                    !response.isSuccessful -> GitHubFileFetch.Error(
                        httpCode = response.code,
                        message = "HTTP ${response.code} for $repoSlug/$path",
                    )
                    else -> {
                        val dto = contentResponseAdapter.fromJson(body)
                            ?: return GitHubFileFetch.Error(
                                httpCode = response.code,
                                message = "Unable to decode GitHub contents payload for $path",
                            )
                        val cleanedB64 = dto.content.orEmpty().replace("\n", "").replace("\r", "")
                        val decoded = String(Base64.decode(cleanedB64, Base64.DEFAULT), Charsets.UTF_8)
                        GitHubFileFetch.Found(
                            sha = dto.sha.orEmpty(),
                            contentUtf8 = decoded,
                        )
                    }
                }
            }
        } catch (io: IOException) {
            GitHubFileFetch.Error(
                httpCode = null,
                message = io.message ?: io::class.java.simpleName,
            )
        } catch (ex: IllegalArgumentException) {
            GitHubFileFetch.Error(
                httpCode = null,
                message = "Base64 decode error: ${ex.message}",
            )
        }
    }

    private fun buildContentsUrl(repoSlug: String, path: String, branch: String?): String {
        val cleanPath = path.trim().trimStart('/')
        val encodedPath = cleanPath.split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
        val base = "$GITHUB_API_BASE/repos/${repoSlug.trim()}/contents/$encodedPath"
        return if (!branch.isNullOrBlank()) {
            "$base?ref=${URLEncoder.encode(branch.trim(), Charsets.UTF_8.name())}"
        } else {
            base
        }
    }

    private fun currentUtcIso(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return formatter.format(java.util.Date())
    }
}

data class HubHandshakeResult(
    val status: MutualLinkStatus,
    val detail: String,
    val thisRepoHasManifest: Boolean,
    val hubRepoHasManifest: Boolean,
    val hubPointsBackToThisRepo: Boolean,
    val pulledIndex: NexusClientCodestringIndex?,
    val changelogDiff: NexusHubChangelogDiff?,
    val checkedAtIso: String,
)

sealed class GitHubCommitResult {
    data class Success(
        val commitSha: String,
        val htmlUrl: String,
        val recoveredFromConflict: Boolean,
    ) : GitHubCommitResult()

    data class Failure(
        val httpCode: Int?,
        val message: String,
    ) : GitHubCommitResult()
}

private sealed class GitHubFileFetch {
    data class Found(val sha: String, val contentUtf8: String) : GitHubFileFetch()
    data object NotFound : GitHubFileFetch()
    data class Error(val httpCode: Int?, val message: String) : GitHubFileFetch()
}

@JsonClass(generateAdapter = true)
internal data class GitHubContentFileResponse(
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "content") val content: String? = null,
    @Json(name = "encoding") val encoding: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubPutContentRequest(
    @Json(name = "message") val message: String,
    @Json(name = "content") val content: String,
    @Json(name = "branch") val branch: String,
    @Json(name = "sha") val sha: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubPutContentResponse(
    @Json(name = "content") val content: GitHubPutContentMeta? = null,
    @Json(name = "commit") val commit: GitHubPutCommitMeta? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubPutContentMeta(
    @Json(name = "html_url") val htmlUrl: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubPutCommitMeta(
    @Json(name = "sha") val sha: String? = null,
)
