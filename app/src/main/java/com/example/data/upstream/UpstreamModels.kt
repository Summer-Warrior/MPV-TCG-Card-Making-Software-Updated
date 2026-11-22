package com.example.data.upstream

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpstreamManifest(
    @Json(name = "schema_version") val schemaVersion: Int = 1,
    @Json(name = "webhook_relay_url") val webhookRelayUrl: String = "",
    @Json(name = "engine_splits") val engineSplits: List<UpstreamRepoSplit> = emptyList(),
    @Json(name = "pack_splits") val packSplits: List<UpstreamRepoSplit> = emptyList(),
    @Json(name = "open_resource_connectors") val openResourceConnectors: List<OpenResourceConnector> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class UpstreamRepoSplit(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "owner") val owner: String,
    @Json(name = "repo") val repo: String,
    @Json(name = "branch") val branch: String,
    @Json(name = "available_branches") val availableBranches: List<String> = listOf("main"),
    @Json(name = "channel") val channel: String = "stable",
    @Json(name = "engine_target") val engineTarget: String = "mse_modern",
    @Json(name = "font_folder") val fontFolder: String? = null,
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "pinned_sha") val pinnedSha: String? = null,
    @Json(name = "previous_sha") val previousSha: String? = null,
    @Json(name = "last_etag") val lastEtag: String? = null,
    @Json(name = "last_checked_at") val lastCheckedAt: String? = null,
    @Json(name = "description") val description: String = "",
)

@JsonClass(generateAdapter = true)
data class OpenResourceConnector(
    @Json(name = "id") val id: String,
    @Json(name = "game_name") val gameName: String,
    @Json(name = "owner") val owner: String,
    @Json(name = "repo") val repo: String,
    @Json(name = "branch") val branch: String = "main",
    @Json(name = "resource_type") val resourceType: String = "frames",
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "pinned_sha") val pinnedSha: String? = null,
    @Json(name = "last_etag") val lastEtag: String? = null,
)

data class GitHubCommitCheckResult(
    val splitId: String,
    val ownerRepo: String,
    val branch: String,
    val status: UpdateCheckState,
    val latestSha: String? = null,
    val commitMessage: String? = null,
    val author: String? = null,
    val commitDate: String? = null,
    val etag: String? = null,
    val sourceMethod: String = "REST/ETag",
    val detail: String = "",
)

enum class UpdateCheckState {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    NOT_MODIFIED_304,
    ERROR,
}
