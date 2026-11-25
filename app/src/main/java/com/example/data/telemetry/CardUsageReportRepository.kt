package com.example.data.telemetry

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.domain.telemetry.CardUsageAnalyzer
import com.example.domain.telemetry.CustomCardUsageReport
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UsagePullSummary(
    val pulledReportCount: Int,
    val totalStoredReports: Int,
    val checkedRepos: List<String>,
    val detail: String,
)

/**
 * Persists, deduplicates (by `reportId`), and ingests real Custom Card Usage & Interaction
 * Telemetry Reports from:
 * 1. Direct Private Play Store Intent / Deep-Link handoff from `com.aistudio.nexustcg.vxqrmk`
 * 2. Bidirectional GitHub Bridge (`nexus-bridge/usage-reports/` in `Tcg-game-engine-test` & `Project-MPV-RP-Master-Hub`)
 * 3. Local Android SAF JSON file import
 *
 * ZERO mock telemetry data — reports only appear from real ingestion channels.
 */
class CardUsageReportRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    private val telemetryDir: File
        get() = File(context.filesDir, "telemetry").apply { mkdirs() }

    private val reportsFile: File
        get() = File(telemetryDir, "usage_reports.json")

    private val etagsFile: File
        get() = File(telemetryDir, "usage_etags.json")

    private val singleReportAdapter = moshi.adapter(CustomCardUsageReport::class.java)
    private val reportListAdapter = moshi.adapter<List<CustomCardUsageReport>>(
        Types.newParameterizedType(List::class.java, CustomCardUsageReport::class.java)
    ).indent("  ")
    private val mapStringAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    private val _reports = MutableStateFlow<List<CustomCardUsageReport>>(loadStoredReportsSync())
    val reports: StateFlow<List<CustomCardUsageReport>> = _reports.asStateFlow()

    private fun loadStoredReportsSync(): List<CustomCardUsageReport> {
        if (!reportsFile.exists() || reportsFile.length() == 0L) return emptyList()
        return runCatching {
            reportListAdapter.fromJson(reportsFile.readText()).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun persistReportsSync(list: List<CustomCardUsageReport>) {
        val deduplicated = CardUsageAnalyzer.mergeAndDeduplicate(emptyList(), list)
        runCatching {
            reportsFile.writeText(reportListAdapter.toJson(deduplicated))
        }
        _reports.value = deduplicated
    }

    private fun loadEtagsSync(): MutableMap<String, String> {
        if (!etagsFile.exists()) return mutableMapOf()
        return runCatching {
            mapStringAdapter.fromJson(etagsFile.readText())?.toMutableMap() ?: mutableMapOf()
        }.getOrDefault(mutableMapOf())
    }

    private fun saveEtagsSync(map: Map<String, String>) {
        runCatching {
            etagsFile.writeText(mapStringAdapter.toJson(map))
        }
    }

    /**
     * Parses a single [CustomCardUsageReport] JSON object or JSON array of reports,
     * deduplicates by `reportId`, and persists locally.
     */
    suspend fun importReportJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.isNotEmpty()) { "Telemetry report JSON is empty." }
            val parsedList = if (trimmed.startsWith("[")) {
                reportListAdapter.fromJson(trimmed).orEmpty()
            } else {
                listOfNotNull(singleReportAdapter.fromJson(trimmed))
            }.filter { it.reportId.isNotBlank() }

            require(parsedList.isNotEmpty()) {
                "JSON did not match CustomCardUsageReport schema (missing reportId)."
            }
            val merged = CardUsageAnalyzer.mergeAndDeduplicate(_reports.value, parsedList)
            persistReportsSync(merged)
            parsedList.size
        }
    }

    suspend fun importReportFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val rawJson = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("Unable to read selected telemetry JSON file.")
            importReportJson(rawJson).getOrThrow()
        }
    }

    /**
     * Pulls real JSON telemetry reports from `nexus-bridge/usage-reports/` on the linked
     * Game Engine and Master Hub GitHub repositories using GitHub REST API (`2026-03-10`) + ETag caching.
     */
    suspend fun pullReportsFromGitHub(
        repoBranches: List<Pair<String, String>> = listOf(
            "Summer-Warrior/Tcg-game-engine-test" to "main",
            "Summer-Warrior/Project-MPV-RP-Master-Hub" to "main",
        ),
        githubPat: String = "",
    ): Result<UsagePullSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val etags = loadEtagsSync()
            val newlyPulled = mutableListOf<CustomCardUsageReport>()
            val notes = mutableListOf<String>()
            val checked = mutableListOf<String>()

            for ((repoSlug, branch) in repoBranches) {
                val safeSlug = repoSlug.trim()
                if (!safeSlug.contains('/')) continue
                checked += safeSlug
                val dirUrl = "https://api.github.com/repos/$safeSlug/contents/nexus-bridge/usage-reports?ref=$branch"
                val dirReqBuilder = Request.Builder()
                    .url(dirUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                if (githubPat.isNotBlank()) {
                    dirReqBuilder.header("Authorization", "Bearer ${githubPat.trim()}")
                }
                etags[dirUrl]?.let { dirReqBuilder.header("If-None-Match", it) }

                okHttpClient.newCall(dirReqBuilder.build()).execute().use { response ->
                    when (response.code) {
                        304 -> notes += "$safeSlug: Up to date (304)"
                        404 -> notes += "$safeSlug: No usage-reports folder yet"
                        in 200..299 -> {
                            response.header("ETag")?.let { etags[dirUrl] = it }
                            val body = response.body?.string().orEmpty()
                            val entries = JSONArray(body)
                            var repoCount = 0
                            for (i in 0 until entries.length()) {
                                val item = entries.optJSONObject(i) ?: continue
                                val fileName = item.optString("name", "")
                                if (!fileName.endsWith(".json", ignoreCase = true)) continue
                                val apiFileUrl = item.optString("url", "")
                                val downloadUrl = item.optString("download_url", "")
                                val jsonPayload = fetchGitHubJsonFile(apiFileUrl, downloadUrl, githubPat)
                                if (!jsonPayload.isNullOrBlank()) {
                                    val parsed = parseReportPayload(jsonPayload)
                                    newlyPulled += parsed
                                    repoCount += parsed.size
                                }
                            }
                            notes += "$safeSlug: Pulled $repoCount report(s)"
                        }
                        else -> notes += "$safeSlug: HTTP ${response.code}"
                    }
                }
            }

            saveEtagsSync(etags)
            if (newlyPulled.isNotEmpty()) {
                val merged = CardUsageAnalyzer.mergeAndDeduplicate(_reports.value, newlyPulled)
                persistReportsSync(merged)
            }
            UsagePullSummary(
                pulledReportCount = newlyPulled.size,
                totalStoredReports = _reports.value.size,
                checkedRepos = checked,
                detail = notes.joinToString(" • ").ifBlank { "No linked repositories configured." },
            )
        }
    }

    private fun fetchGitHubJsonFile(
        apiFileUrl: String,
        downloadUrl: String,
        githubPat: String,
    ): String? {
        if (downloadUrl.startsWith("https://")) {
            val req = Request.Builder().url(downloadUrl).apply {
                if (githubPat.isNotBlank()) header("Authorization", "Bearer ${githubPat.trim()}")
            }.build()
            runCatching {
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) return resp.body?.string()
                }
            }
        }
        if (apiFileUrl.startsWith("https://")) {
            val req = Request.Builder()
                .url(apiFileUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .apply {
                    if (githubPat.isNotBlank()) header("Authorization", "Bearer ${githubPat.trim()}")
                }
                .build()
            runCatching {
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string().orEmpty())
                        val encoded = obj.optString("content", "").replace("\n", "")
                        if (encoded.isNotBlank()) {
                            return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseReportPayload(rawJson: String): List<CustomCardUsageReport> {
        val trimmed = rawJson.trim()
        return runCatching {
            if (trimmed.startsWith("[")) {
                reportListAdapter.fromJson(trimmed).orEmpty()
            } else {
                listOfNotNull(singleReportAdapter.fromJson(trimmed))
            }
        }.getOrDefault(emptyList()).filter { it.reportId.isNotBlank() }
    }

    suspend fun clearAllReports() = withContext(Dispatchers.IO) {
        persistReportsSync(emptyList())
    }

    companion object {
        private const val GITHUB_API_VERSION = "2026-03-10"
    }
}
