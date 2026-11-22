package com.example.data.upstream

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@JsonClass(generateAdapter = true)
internal data class GitHubCommitApiResponse(
    @Json(name = "sha") val sha: String = "",
    @Json(name = "commit") val commit: GitHubCommitInner? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubCommitInner(
    @Json(name = "message") val message: String = "",
    @Json(name = "author") val author: GitHubCommitAuthor? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubCommitAuthor(
    @Json(name = "name") val name: String = "",
    @Json(name = "date") val date: String = "",
)

/**
 * Real GitHub Public Update Watcher:
 * 1. Polls GitHub Public REST API (`GET /repos/{owner}/{repo}/commits/{branch}`) with `If-None-Match` (ETag)
 *    conditional headers so unchanged repos return `304 Not Modified` without burning API rate limits.
 * 2. Automatically falls back to the public Atom commit feed (`https://github.com/{owner}/{repo}/commits/{branch}.atom`)
 *    if the REST API returns 403/429 rate-limit responses.
 */
class GitHubUpdateWatcher(
    private val okHttpClient: OkHttpClient,
    moshi: Moshi,
) {
    private val commitAdapter = moshi.adapter(GitHubCommitApiResponse::class.java)

    private val atomEntryRegex = Regex("<entry>([\\s\\S]*?)</entry>")
    private val atomIdRegex = Regex("<id>[^<]*?([0-9a-f]{40})</id>")
    private val atomTitleRegex = Regex("<title>([\\s\\S]*?)</title>")
    private val atomUpdatedRegex = Regex("<updated>([^<]+)</updated>")
    private val atomAuthorRegex = Regex("<author>[\\s\\S]*?<name>([^<]+)</name>")

    suspend fun checkSplit(
        splitId: String,
        owner: String,
        repo: String,
        branch: String,
        pinnedSha: String?,
        previousEtag: String?,
    ): GitHubCommitCheckResult = withContext(Dispatchers.IO) {
        val ownerRepo = "$owner/$repo"
        val apiUrl = "https://api.github.com/repos/$owner/$repo/commits/$branch"

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TCGStudio-Android/1.0")
        if (!previousEtag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", previousEtag)
        }

        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val etag = response.header("ETag") ?: previousEtag
                when (response.code) {
                    304 -> {
                        return@withContext GitHubCommitCheckResult(
                            splitId = splitId,
                            ownerRepo = ownerRepo,
                            branch = branch,
                            status = UpdateCheckState.NOT_MODIFIED_304,
                            latestSha = pinnedSha,
                            etag = etag,
                            sourceMethod = "GitHub REST (304 ETag)",
                            detail = "No new commits since last ETag check.",
                        )
                    }

                    in 200..299 -> {
                        val body = response.body?.string().orEmpty()
                        val parsed = commitAdapter.fromJson(body)
                        val sha = parsed?.sha?.takeIf { it.isNotBlank() }
                        val msg = parsed?.commit?.message?.lineSequence()?.firstOrNull().orEmpty()
                        val author = parsed?.commit?.author?.name
                        val date = parsed?.commit?.author?.date
                        val isSame = !pinnedSha.isNullOrBlank() && sha != null &&
                            sha.startsWith(pinnedSha, ignoreCase = true)
                        return@withContext GitHubCommitCheckResult(
                            splitId = splitId,
                            ownerRepo = ownerRepo,
                            branch = branch,
                            status = if (isSame) UpdateCheckState.UP_TO_DATE else UpdateCheckState.UPDATE_AVAILABLE,
                            latestSha = sha,
                            commitMessage = msg,
                            author = author,
                            commitDate = date,
                            etag = etag,
                            sourceMethod = "GitHub REST (200 ETag)",
                            detail = if (isSame) "Pinned SHA matches HEAD." else "New upstream commit available.",
                        )
                    }

                    403, 429 -> {
                        // Rate-limited on public REST API -> automatically fallback to public Atom feed
                        return@withContext checkViaAtomFeed(
                            splitId = splitId,
                            owner = owner,
                            repo = repo,
                            branch = branch,
                            pinnedSha = pinnedSha,
                            previousEtag = previousEtag,
                        )
                    }

                    else -> {
                        return@withContext GitHubCommitCheckResult(
                            splitId = splitId,
                            ownerRepo = ownerRepo,
                            branch = branch,
                            status = UpdateCheckState.ERROR,
                            etag = previousEtag,
                            sourceMethod = "GitHub REST",
                            detail = "HTTP ${response.code} checking $ownerRepo@$branch",
                        )
                    }
                }
            }
        } catch (ex: Exception) {
            return@withContext checkViaAtomFeed(
                splitId = splitId,
                owner = owner,
                repo = repo,
                branch = branch,
                pinnedSha = pinnedSha,
                previousEtag = previousEtag,
                fallbackReason = ex.message,
            )
        }
    }

    private fun checkViaAtomFeed(
        splitId: String,
        owner: String,
        repo: String,
        branch: String,
        pinnedSha: String?,
        previousEtag: String?,
        fallbackReason: String? = null,
    ): GitHubCommitCheckResult {
        val ownerRepo = "$owner/$repo"
        val atomUrl = "https://github.com/$owner/$repo/commits/$branch.atom"
        val req = Request.Builder()
            .url(atomUrl)
            .header("User-Agent", "TCGStudio-Android/1.0")
            .get()
            .build()

        return try {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return GitHubCommitCheckResult(
                        splitId = splitId,
                        ownerRepo = ownerRepo,
                        branch = branch,
                        status = UpdateCheckState.ERROR,
                        etag = previousEtag,
                        sourceMethod = "Atom Feed",
                        detail = "Atom HTTP ${resp.code}${fallbackReason?.let { " ($it)" }.orEmpty()}",
                    )
                }
                val xml = resp.body?.string().orEmpty()
                val firstEntry = atomEntryRegex.find(xml)?.groupValues?.getOrNull(1)
                val sha = firstEntry?.let { atomIdRegex.find(it)?.groupValues?.getOrNull(1) }
                val title = firstEntry?.let { atomTitleRegex.find(it)?.groupValues?.getOrNull(1)?.trim() }
                val updated = firstEntry?.let { atomUpdatedRegex.find(it)?.groupValues?.getOrNull(1) }
                val author = firstEntry?.let { atomAuthorRegex.find(it)?.groupValues?.getOrNull(1) }
                val isSame = !pinnedSha.isNullOrBlank() && sha != null &&
                    sha.startsWith(pinnedSha, ignoreCase = true)

                GitHubCommitCheckResult(
                    splitId = splitId,
                    ownerRepo = ownerRepo,
                    branch = branch,
                    status = if (isSame) UpdateCheckState.UP_TO_DATE else UpdateCheckState.UPDATE_AVAILABLE,
                    latestSha = sha,
                    commitMessage = title,
                    author = author,
                    commitDate = updated,
                    etag = resp.header("ETag") ?: previousEtag,
                    sourceMethod = "GitHub Public Atom Feed",
                    detail = if (isSame) "Pinned SHA matches Atom HEAD." else "Checked via public Atom feed.",
                )
            }
        } catch (ex: Exception) {
            GitHubCommitCheckResult(
                splitId = splitId,
                ownerRepo = ownerRepo,
                branch = branch,
                status = UpdateCheckState.ERROR,
                etag = previousEtag,
                sourceMethod = "Network Error",
                detail = ex.message ?: "Unable to reach GitHub",
            )
        }
    }
}
