package com.example.data.nexus

import android.net.Uri
import android.util.Base64
import com.example.domain.nexus.EngineCommitDiff
import com.example.domain.nexus.EngineCompatibilityAuditor
import com.example.domain.nexus.EngineHealthStatusPayload
import com.example.domain.nexus.EngineSyncSnapshot
import com.example.domain.nexus.HandshakeContract
import com.example.domain.nexus.MasterHubAdminGate
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
 * Live Engine Data Reader & Tri-Channel Admin Notification Handshake Service.
 *
 * Reads live metadata, latest commit SHA, and source schemas directly from
 * `Summer-Warrior/Tcg-game-engine-test` via GitHub REST API with ETag caching
 * (`If-None-Match`) and persisted file-content caching so HTTP `304 Not Modified`
 * passes real cached source files to the auditor without triggering false alerts.
 */
class EngineSyncIngestionService(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val branchAdapter = moshi.adapter(GitHubBranchResponse::class.java)
    private val contentAdapter = moshi.adapter(GitHubContentFileResponse::class.java)
    private val contractAdapter = moshi.adapter(HandshakeContract::class.java)
    private val healthAdapter = moshi.adapter(EngineHealthStatusPayload::class.java).indent("  ")
    private val dispatchAdapter = moshi.adapter(MasterHubDispatchBody::class.java)

    suspend fun ingestLiveEngineAndAudit(
        contract: HandshakeContract,
        baselineSnapshot: EngineSyncSnapshot,
        engineBranch: String,
        cachedEtags: Map<String, String>,
        cachedFileContents: Map<String, String> = emptyMap(),
        fineGrainedPat: String? = null,
    ): LiveEngineIngestionResult = withContext(Dispatchers.IO) {
        val updatedEtags = cachedEtags.toMutableMap()
        val updatedCachedFiles = cachedFileContents.toMutableMap()
        val unchanged304Files = mutableSetOf<String>()
        val engineRepo = contract.gameEngineRepo.trim()

        // 1. Fetch latest branch commit SHA with ETag caching
        val branchUrl = "${NexusHubBridgeService.GITHUB_API_BASE}/repos/$engineRepo/branches/${encodeSeg(engineBranch)}"
        val branchFetch = fetchWithEtag(branchUrl, cachedEtags[branchUrl], fineGrainedPat)
        val latestSha: String
        var usedOfflineFallback = false
        var statusMessage: String

        when (branchFetch) {
            is EtagResponse.NotModified -> {
                latestSha = baselineSnapshot.commitSha
                statusMessage = "ETag 304 Not Modified — $engineRepo@$engineBranch matches cached baseline (${latestSha.take(10)})."
            }
            is EtagResponse.Ok -> {
                branchFetch.etag?.let { updatedEtags[branchUrl] = it }
                val parsedBranch = runCatching { branchAdapter.fromJson(branchFetch.body) }.getOrNull()
                latestSha = parsedBranch?.commit?.sha.orEmpty()
                statusMessage = if (latestSha.isNotBlank()) {
                    "Fetched live head ${latestSha.take(10)} from $engineRepo@$engineBranch."
                } else {
                    "Branch response from $engineRepo did not include a commit SHA."
                }
            }
            is EtagResponse.Error -> {
                usedOfflineFallback = true
                latestSha = ""
                statusMessage = "Offline/API fallback (${branchFetch.detail}) — keeping cached baseline snapshot ${baselineSnapshot.commitSha.take(10)}."
            }
        }

        if (usedOfflineFallback) {
            val fallbackHealth = EngineCompatibilityAuditor.runBenefitIntegrityAudit(
                contract = contract,
                commitSha = baselineSnapshot.commitSha,
                fetchedFiles = updatedCachedFiles,
                missingFiles = emptyList(),
                remoteEngineConsentStatus = contract.gameEngineConsent.status,
                unchanged304Files = updatedCachedFiles.keys,
            )
            return@withContext LiveEngineIngestionResult(
                latestCommitSha = baselineSnapshot.commitSha,
                stagedSnapshot = baselineSnapshot,
                diff = EngineCompatibilityAuditor.computeEngineDiff(baselineSnapshot, baselineSnapshot),
                healthStatus = fallbackHealth,
                remoteEngineContract = null,
                updatedEtags = updatedEtags,
                updatedCachedFiles = updatedCachedFiles,
                unchanged304Files = unchanged304Files,
                missingFiles = emptyList(),
                usedOfflineFallback = true,
                statusMessage = statusMessage,
            )
        }

        // 2. Fetch required engine files from Tcg-game-engine-test with real cached body reuse on 304
        val fetchedFiles = mutableMapOf<String, String>()
        val missingFiles = mutableListOf<String>()

        contract.requiredEngineFiles.forEach { relPath ->
            val fileUrl = buildContentsUrl(engineRepo, relPath, engineBranch)
            val existingBody = updatedCachedFiles[relPath]
            // Only send If-None-Match if we actually have the cached content body on disk
            val etagToSend = if (!existingBody.isNullOrBlank()) cachedEtags[fileUrl] else null

            when (val res = fetchWithEtag(fileUrl, etagToSend, fineGrainedPat)) {
                is EtagResponse.Ok -> {
                    res.etag?.let { updatedEtags[fileUrl] = it }
                    val decoded = decodeGitHubFileBody(res.body)
                    if (decoded != null) {
                        fetchedFiles[relPath] = decoded
                        updatedCachedFiles[relPath] = decoded
                    } else {
                        missingFiles += relPath
                    }
                }
                is EtagResponse.NotModified -> {
                    unchanged304Files += relPath
                    if (!existingBody.isNullOrBlank()) {
                        fetchedFiles[relPath] = existingBody
                    } else {
                        // Safety fallback: re-fetch cleanly without ETag if cache body was missing
                        when (val cleanRes = fetchWithEtag(fileUrl, null, fineGrainedPat)) {
                            is EtagResponse.Ok -> {
                                cleanRes.etag?.let { updatedEtags[fileUrl] = it }
                                val decoded = decodeGitHubFileBody(cleanRes.body)
                                if (decoded != null) {
                                    fetchedFiles[relPath] = decoded
                                    updatedCachedFiles[relPath] = decoded
                                }
                            }
                            else -> Unit
                        }
                    }
                }
                is EtagResponse.Error -> {
                    missingFiles += "$relPath (HTTP ${res.httpCode ?: 0})"
                }
            }
        }

        // 3. Optionally read remote handshake-contract.json from Tcg-game-engine-test
        val remoteContractUrl = buildContentsUrl(engineRepo, "handshake-contract.json", engineBranch)
        val remoteContract: HandshakeContract? = when (val hcRes = fetchWithEtag(remoteContractUrl, null, fineGrainedPat)) {
            is EtagResponse.Ok -> decodeGitHubFileBody(hcRes.body)?.let { raw ->
                runCatching { contractAdapter.fromJson(raw) }.getOrNull()
            }
            else -> null
        }

        // 4. Parse incoming staged snapshot & compute diff against verified baseline
        val stagedSnapshot = EngineCompatibilityAuditor.parseSnapshotFromEngineFiles(
            engineRepo = engineRepo,
            engineBranch = engineBranch,
            commitSha = latestSha.ifBlank { baselineSnapshot.commitSha },
            fetchedFiles = fetchedFiles,
            fallbackBaseline = baselineSnapshot,
        )
        val diff = EngineCompatibilityAuditor.computeEngineDiff(baselineSnapshot, stagedSnapshot)

        // 5. Run Benefit-Integrity Compatibility Audit (passes real cached bodies + unchanged304Files)
        val healthStatus = EngineCompatibilityAuditor.runBenefitIntegrityAudit(
            contract = contract,
            commitSha = latestSha,
            fetchedFiles = fetchedFiles,
            missingFiles = missingFiles,
            remoteEngineConsentStatus = remoteContract?.gameEngineConsent?.status,
            unchanged304Files = unchanged304Files,
        )

        LiveEngineIngestionResult(
            latestCommitSha = latestSha,
            stagedSnapshot = stagedSnapshot,
            diff = diff,
            healthStatus = healthStatus,
            remoteEngineContract = remoteContract,
            updatedEtags = updatedEtags,
            updatedCachedFiles = updatedCachedFiles,
            unchanged304Files = unchanged304Files,
            missingFiles = missingFiles,
            usedOfflineFallback = false,
            statusMessage = statusMessage,
        )
    }

    /**
     * Channel 1: Sends formal `repository_dispatch` (`nexus_engine_breaking_alert`)
     * Notification Handshake to `Summer-Warrior/Project-MPV-RP-Master-Hub`.
     */
    suspend fun dispatchMasterHubBreakingAlert(
        masterHubRepo: String,
        healthStatus: EngineHealthStatusPayload,
        dispatchPat: String,
    ): NotificationChannelResult = withContext(Dispatchers.IO) {
        val token = dispatchPat.trim()
        if (token.isEmpty()) {
            return@withContext NotificationChannelResult(
                channel = "Master Hub Handshake",
                success = false,
                detail = "Requires Fine-Grained PAT or GitHub Actions MASTER_HUB_DISPATCH_TOKEN.",
            )
        }
        val url = "${NexusHubBridgeService.GITHUB_API_BASE}/repos/${masterHubRepo.trim()}/dispatches"
        val bodyJson = dispatchAdapter.toJson(
            MasterHubDispatchBody(
                eventType = "nexus_engine_breaking_alert",
                clientPayload = healthStatus,
            )
        )
        val request = Request.Builder()
            .url(url)
            .header("Accept", NexusHubBridgeService.GITHUB_ACCEPT)
            .header("X-GitHub-Api-Version", NexusHubBridgeService.GITHUB_API_VERSION)
            .header("User-Agent", NexusHubBridgeService.USER_AGENT)
            .header("Authorization", "Bearer $token")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        executePost("Master Hub Handshake", request)
    }

    /**
     * Channel 3: Dispatches rich structured Discord Webhook alert mentioning Master Hub Admin Role IDs (`<@&ROLE_ID>`).
     * Never stores or asks for Discord Bot Tokens.
     */
    suspend fun sendDiscordAdminWebhook(
        webhookUrl: String,
        adminGate: MasterHubAdminGate,
        healthStatus: EngineHealthStatusPayload,
    ): NotificationChannelResult = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (!cleanUrl.startsWith("https://discord.com/api/webhooks/") &&
            !cleanUrl.startsWith("https://discordapp.com/api/webhooks/")
        ) {
            return@withContext NotificationChannelResult(
                channel = "Discord Admin Alert",
                success = false,
                detail = "Configure a valid Discord Admin Alert Webhook URL first.",
            )
        }
        val mentions = adminGate.adminRoleIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "<@&$it>" }
            .ifBlank { "@here" }

        val commitUrl = "https://github.com/${healthStatus.targetEngineRepo}/commit/${healthStatus.failingCommitSha}"
        val colorInt = if (healthStatus.severity == "BREAKING_INCOMPATIBLE") 15158332 else 15105570
        val brokenText = healthStatus.brokenCapabilities.joinToString(", ").ifBlank { "None" }
        val framesText = healthStatus.affectedCardFrames.joinToString(", ").ifBlank { "None" }

        val payloadJson = buildString {
            append("{")
            append("\"content\":${jsonEscape("$mentions **${healthStatus.severity}** detected in `${healthStatus.targetEngineRepo}`")},")
            append("\"embeds\":[{")
            append("\"title\":${jsonEscape("TCG Engine Alert: ${healthStatus.severity}")},")
            append("\"url\":${jsonEscape(commitUrl)},")
            append("\"color\":$colorInt,")
            append("\"fields\":[")
            append("{\"name\":\"Alert ID\",\"value\":${jsonEscape(healthStatus.alertId)},\"inline\":true},")
            append("{\"name\":\"Failing Commit\",\"value\":${jsonEscape(healthStatus.failingCommitSha.take(12).ifBlank { "unknown" })},\"inline\":true},")
            append("{\"name\":\"Master Hub Ack\",\"value\":${jsonEscape(healthStatus.masterHubAckStatus)},\"inline\":true},")
            append("{\"name\":\"Broken Capabilities\",\"value\":${jsonEscape(brokenText)},\"inline\":false},")
            append("{\"name\":\"Affected Card Frames\",\"value\":${jsonEscape(framesText)},\"inline\":false}")
            append("],")
            append("\"timestamp\":${jsonEscape(healthStatus.timestamp)}")
            append("}]}")
        }

        val request = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", NexusHubBridgeService.USER_AGENT)
            .post(payloadJson.toRequestBody(jsonMediaType))
            .build()

        executePost("Discord Admin Alert", request)
    }

    /**
     * Channel 4: Optional Admin Email Relay Webhook dispatch.
     */
    suspend fun sendAdminEmailRelay(
        relayUrl: String,
        adminEmails: List<String>,
        healthStatus: EngineHealthStatusPayload,
    ): NotificationChannelResult = withContext(Dispatchers.IO) {
        val cleanUrl = relayUrl.trim()
        if (!cleanUrl.startsWith("https://")) {
            return@withContext NotificationChannelResult(
                channel = "Admin Email Relay",
                success = false,
                detail = "Configure an HTTPS Admin Email Relay URL or use the one-tap Mailto Escalation fallback.",
            )
        }
        val payload = buildString {
            append("{")
            append("\"recipients\":[${adminEmails.joinToString(",") { jsonEscape(it) }}],")
            append("\"subject\":${jsonEscape("[${healthStatus.severity}] TCG Engine Compatibility Alert (${healthStatus.failingCommitSha.take(10)})")},")
            append("\"healthStatus\":${healthAdapter.toJson(healthStatus)}")
            append("}")
        }
        val request = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", NexusHubBridgeService.USER_AGENT)
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        executePost("Admin Email Relay", request)
    }

    fun buildPrefilledMailtoUri(
        adminEmails: List<String>,
        healthStatus: EngineHealthStatusPayload,
        diffSummary: String,
    ): Uri {
        val toAddress = adminEmails.joinToString(",")
        val subject = "[${healthStatus.severity}] MPV <-> ${healthStatus.targetEngineRepo} Breaking Alert"
        val body = buildString {
            appendLine("Alert ID: ${healthStatus.alertId}")
            appendLine("Severity: ${healthStatus.severity}")
            appendLine("Target Engine Repo: ${healthStatus.targetEngineRepo}")
            appendLine("Failing Commit SHA: ${healthStatus.failingCommitSha}")
            appendLine("Master Hub Ack Status: ${healthStatus.masterHubAckStatus}")
            appendLine("Broken Capabilities: ${healthStatus.brokenCapabilities.joinToString(", ")}")
            appendLine("Affected Card Frames: ${healthStatus.affectedCardFrames.joinToString(", ")}")
            appendLine("Signed SHA-256: ${healthStatus.signatureSha256}")
            if (diffSummary.isNotBlank()) {
                appendLine()
                appendLine("Diagnostic Diff:")
                appendLine(diffSummary)
            }
        }
        return Uri.parse(
            "mailto:${Uri.encode(toAddress)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
    }

    fun serializeHealthStatusJson(payload: EngineHealthStatusPayload): String =
        healthAdapter.toJson(payload) + "\n"

    private fun executePost(channel: String, request: Request): NotificationChannelResult {
        return try {
            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    NotificationChannelResult(channel, true, "Dispatched (HTTP ${resp.code}).")
                } else {
                    NotificationChannelResult(
                        channel,
                        false,
                        "Rejected (HTTP ${resp.code}): ${resp.body?.string().orEmpty().take(160)}",
                    )
                }
            }
        } catch (io: IOException) {
            NotificationChannelResult(channel, false, "Network error: ${io.message ?: io::class.java.simpleName}")
        }
    }

    private fun fetchWithEtag(
        url: String,
        cachedEtag: String?,
        pat: String?,
    ): EtagResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", NexusHubBridgeService.GITHUB_ACCEPT)
            .header("X-GitHub-Api-Version", NexusHubBridgeService.GITHUB_API_VERSION)
            .header("User-Agent", NexusHubBridgeService.USER_AGENT)
            .get()
        if (!cachedEtag.isNullOrBlank()) {
            builder.header("If-None-Match", cachedEtag)
        }
        if (!pat.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${pat.trim()}")
        }
        return try {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 304 -> EtagResponse.NotModified
                    response.isSuccessful -> EtagResponse.Ok(
                        body = response.body?.string().orEmpty(),
                        etag = response.header("ETag"),
                    )
                    else -> EtagResponse.Error(response.code, "HTTP ${response.code}")
                }
            }
        } catch (io: IOException) {
            EtagResponse.Error(null, io.message ?: io::class.java.simpleName)
        }
    }

    private fun decodeGitHubFileBody(rawJson: String): String? {
        val dto = runCatching { contentAdapter.fromJson(rawJson) }.getOrNull() ?: return null
        val cleaned = dto.content.orEmpty().replace("\n", "").replace("\r", "")
        if (cleaned.isEmpty()) return null
        return runCatching { String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
    }

    private fun buildContentsUrl(repoSlug: String, relPath: String, branch: String): String {
        val encodedPath = relPath.trim().trimStart('/')
            .split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/") { encodeSeg(it) }
        return "${NexusHubBridgeService.GITHUB_API_BASE}/repos/$repoSlug/contents/$encodedPath?ref=${encodeSeg(branch)}"
    }

    private fun encodeSeg(raw: String): String =
        URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")

    private fun jsonEscape(raw: String): String {
        val escaped = raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        return "\"$escaped\""
    }
}

data class LiveEngineIngestionResult(
    val latestCommitSha: String,
    val stagedSnapshot: EngineSyncSnapshot,
    val diff: EngineCommitDiff,
    val healthStatus: EngineHealthStatusPayload,
    val remoteEngineContract: HandshakeContract?,
    val updatedEtags: Map<String, String>,
    val updatedCachedFiles: Map<String, String> = emptyMap(),
    val unchanged304Files: Set<String> = emptySet(),
    val missingFiles: List<String> = emptyList(),
    val usedOfflineFallback: Boolean,
    val statusMessage: String,
)

data class NotificationChannelResult(
    val channel: String,
    val success: Boolean,
    val detail: String,
)

private sealed class EtagResponse {
    data class Ok(val body: String, val etag: String?) : EtagResponse()
    data object NotModified : EtagResponse()
    data class Error(val httpCode: Int?, val detail: String) : EtagResponse()
}

@JsonClass(generateAdapter = true)
internal data class GitHubBranchResponse(
    @Json(name = "commit") val commit: GitHubBranchCommit? = null,
)

@JsonClass(generateAdapter = true)
internal data class GitHubBranchCommit(
    @Json(name = "sha") val sha: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class MasterHubDispatchBody(
    @Json(name = "event_type") val eventType: String,
    @Json(name = "client_payload") val clientPayload: EngineHealthStatusPayload,
)
