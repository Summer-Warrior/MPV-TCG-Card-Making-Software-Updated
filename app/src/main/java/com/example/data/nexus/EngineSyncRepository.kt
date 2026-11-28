package com.example.data.nexus

import android.content.Context
import android.util.Log
import com.example.domain.nexus.EngineHealthStatusPayload
import com.example.domain.nexus.EngineSyncSnapshot
import com.example.domain.nexus.HandshakeContract
import com.example.domain.nexus.NexusAppManifest
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@JsonClass(generateAdapter = true)
data class AdminAlertLocalState(
    @Json(name = "alertedCommitShas") val alertedCommitShas: List<String> = emptyList(),
    @Json(name = "dismissedAlignmentKeys") val dismissedAlignmentKeys: List<String> = emptyList(),
    @Json(name = "etags") val etags: Map<String, String> = emptyMap(),
    @Json(name = "syncPaused") val syncPaused: Boolean = false,
    @Json(name = "currentUserRoleIds") val currentUserRoleIds: List<String> = emptyList(),
    @Json(name = "currentUserId") val currentUserId: String = "",
    @Json(name = "currentUserEmail") val currentUserEmail: String = "",
    @Json(name = "discordAdminWebhookUrl") val discordAdminWebhookUrl: String = "",
    @Json(name = "emailRelayWebhookUrl") val emailRelayWebhookUrl: String = "",
)

/**
 * Persists `nexus-app.json`, `handshake-contract.json`, verified baseline `engine-sync-snapshot.json`,
 * signed `engine-health-status.json`, ETag cache, verified cached engine file contents (for HTTP 304),
 * and deduplicated commit-SHA alert/dismissal history.
 *
 * Automatically migrates legacy `Summer-Warrior/Nexus-Master-Hub` references on upgrade to
 * `Summer-Warrior/Project-MPV-RP-Master-Hub`.
 */
class EngineSyncRepository(
    context: Context,
    moshi: Moshi,
) {
    private val dir = File(context.filesDir, "engine_handshake").apply { mkdirs() }
    private val appManifestFile = File(dir, "nexus-app.json")
    private val contractFile = File(dir, "handshake-contract.json")
    private val snapshotFile = File(dir, "engine-sync-snapshot.json")
    private val healthFile = File(dir, "engine-health-status.json")
    private val localStateFile = File(dir, "admin_alert_state.json")
    private val cachedFilesFile = File(dir, "cached_engine_files.json")

    private val appManifestAdapter = moshi.adapter(NexusAppManifest::class.java).indent("  ")
    private val contractAdapter = moshi.adapter(HandshakeContract::class.java).indent("  ")
    private val snapshotAdapter = moshi.adapter(EngineSyncSnapshot::class.java).indent("  ")
    private val healthAdapter = moshi.adapter(EngineHealthStatusPayload::class.java).indent("  ")
    private val localStateAdapter = moshi.adapter(AdminAlertLocalState::class.java).indent("  ")
    private val stringMapAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    suspend fun loadAppManifest(): NexusAppManifest = withContext(Dispatchers.IO) {
        if (!appManifestFile.exists()) {
            val seed = NexusAppManifest()
            saveAppManifest(seed)
            return@withContext seed
        }
        val loaded = runCatching { appManifestAdapter.fromJson(appManifestFile.readText(Charsets.UTF_8)) }
            .getOrNull() ?: NexusAppManifest()
        if (loaded.linkedMasterHub.equals(LEGACY_HUB_SLUG, ignoreCase = true)) {
            val migrated = loaded.copy(linkedMasterHub = LIVE_HUB_SLUG)
            saveAppManifest(migrated)
            return@withContext migrated
        }
        loaded
    }

    suspend fun saveAppManifest(manifest: NexusAppManifest) = withContext(Dispatchers.IO) {
        writeAtomic(appManifestFile, appManifestAdapter.toJson(manifest) + "\n")
    }

    suspend fun loadContract(): HandshakeContract = withContext(Dispatchers.IO) {
        if (!contractFile.exists()) {
            val seed = HandshakeContract()
            saveContract(seed)
            return@withContext seed
        }
        val loaded = runCatching { contractAdapter.fromJson(contractFile.readText(Charsets.UTF_8)) }
            .getOrNull() ?: HandshakeContract()
        if (loaded.masterHubRepo.equals(LEGACY_HUB_SLUG, ignoreCase = true) || loaded.contractVersion == "1.0.0") {
            val migrated = loaded.copy(
                contractVersion = "1.1.0",
                masterHubRepo = LIVE_HUB_SLUG,
            )
            saveContract(migrated)
            return@withContext migrated
        }
        loaded
    }

    suspend fun saveContract(contract: HandshakeContract) = withContext(Dispatchers.IO) {
        writeAtomic(contractFile, contractAdapter.toJson(contract) + "\n")
    }

    suspend fun loadBaselineSnapshot(): EngineSyncSnapshot = withContext(Dispatchers.IO) {
        if (!snapshotFile.exists()) {
            val seed = EngineSyncSnapshot()
            saveBaselineSnapshot(seed)
            return@withContext seed
        }
        runCatching { snapshotAdapter.fromJson(snapshotFile.readText(Charsets.UTF_8)) }
            .getOrNull() ?: EngineSyncSnapshot()
    }

    suspend fun saveBaselineSnapshot(snapshot: EngineSyncSnapshot) = withContext(Dispatchers.IO) {
        writeAtomic(snapshotFile, snapshotAdapter.toJson(snapshot) + "\n")
    }

    suspend fun loadLastHealthStatus(): EngineHealthStatusPayload? = withContext(Dispatchers.IO) {
        if (!healthFile.exists()) return@withContext null
        runCatching { healthAdapter.fromJson(healthFile.readText(Charsets.UTF_8)) }.getOrNull()
    }

    suspend fun saveHealthStatus(health: EngineHealthStatusPayload) = withContext(Dispatchers.IO) {
        writeAtomic(healthFile, healthAdapter.toJson(health) + "\n")
    }

    suspend fun loadAdminAlertState(): AdminAlertLocalState = withContext(Dispatchers.IO) {
        if (!localStateFile.exists()) return@withContext AdminAlertLocalState()
        runCatching { localStateAdapter.fromJson(localStateFile.readText(Charsets.UTF_8)) }
            .getOrNull() ?: AdminAlertLocalState()
    }

    suspend fun saveAdminAlertState(state: AdminAlertLocalState) = withContext(Dispatchers.IO) {
        writeAtomic(localStateFile, localStateAdapter.toJson(state) + "\n")
    }

    suspend fun loadCachedEngineFiles(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!cachedFilesFile.exists()) return@withContext emptyMap()
        runCatching { stringMapAdapter.fromJson(cachedFilesFile.readText(Charsets.UTF_8)) }
            .getOrNull().orEmpty()
    }

    suspend fun saveCachedEngineFiles(files: Map<String, String>) = withContext(Dispatchers.IO) {
        writeAtomic(cachedFilesFile, stringMapAdapter.toJson(files))
    }

    fun serializeContractJson(contract: HandshakeContract): String =
        contractAdapter.toJson(contract) + "\n"

    fun serializeSnapshotJson(snapshot: EngineSyncSnapshot): String =
        snapshotAdapter.toJson(snapshot) + "\n"

    fun serializeAppManifestJson(manifest: NexusAppManifest): String =
        appManifestAdapter.toJson(manifest) + "\n"

    fun parseContractJson(rawJson: String): HandshakeContract? =
        runCatching { contractAdapter.fromJson(rawJson) }
            .onFailure { err -> Log.w(TAG, "Failed parsing HandshakeContract JSON: ${err.message}", err) }
            .getOrNull()

    fun parseSnapshotJson(rawJson: String): EngineSyncSnapshot? =
        runCatching { snapshotAdapter.fromJson(rawJson) }
            .onFailure { err -> Log.w(TAG, "Failed parsing EngineSyncSnapshot JSON: ${err.message}", err) }
            .getOrNull()

    private fun writeAtomic(target: File, content: String) {
        dir.mkdirs()
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        tmp.renameTo(target)
    }

    companion object {
        private const val TAG = "EngineSyncRepository"
        private const val LEGACY_HUB_SLUG = "Summer-Warrior/Nexus-Master-Hub"
        private const val LIVE_HUB_SLUG = "Summer-Warrior/Project-MPV-RP-Master-Hub"
    }
}
