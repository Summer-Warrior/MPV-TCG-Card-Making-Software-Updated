package com.example.data.upstream

import android.content.Context
import com.squareup.moshi.Moshi
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

/**
 * Reads, edits, and persists `upstream_manifest.json` inside internal app storage,
 * seeded from `assets/upstream_manifest.json` with all Section 3 MagicSetEditor2 and MagicSetEditorPacks splits.
 */
class UpstreamManifestRepository(
    private val context: Context,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(UpstreamManifest::class.java).indent("  ")

    private val manifestFile: File
        get() = File(context.filesDir, "upstream_manifest.json")

    private val _manifest = MutableStateFlow(loadOrSeedManifest())
    val manifest: StateFlow<UpstreamManifest> = _manifest.asStateFlow()

    private fun loadOrSeedManifest(): UpstreamManifest {
        val seeded = loadSeededManifestFromAssets()
        if (manifestFile.exists() && manifestFile.length() > 0L) {
            runCatching {
                val parsed = adapter.fromJson(manifestFile.readText())
                if (parsed != null && parsed.engineSplits.isNotEmpty()) {
                    val existingEngineIds = parsed.engineSplits.map { it.id }.toSet()
                    val existingPackIds = parsed.packSplits.map { it.id }.toSet()
                    val missingEngines = seeded.engineSplits.filter { it.id !in existingEngineIds }
                    val missingPacks = seeded.packSplits.filter { it.id !in existingPackIds }
                    if (missingEngines.isNotEmpty() || missingPacks.isNotEmpty()) {
                        val merged = parsed.copy(
                            engineSplits = parsed.engineSplits + missingEngines,
                            packSplits = parsed.packSplits + missingPacks,
                        )
                        runCatching { manifestFile.writeText(adapter.toJson(merged)) }
                        return merged
                    }
                    return parsed
                }
            }
        }
        runCatching { manifestFile.writeText(adapter.toJson(seeded)) }
        return seeded
    }

    private fun loadSeededManifestFromAssets(): UpstreamManifest = runCatching {
        val seededJson = context.assets.open("upstream_manifest.json").bufferedReader().use { it.readText() }
        adapter.fromJson(seededJson) ?: UpstreamManifest()
    }.getOrDefault(UpstreamManifest())

    private fun saveManifestSync(updated: UpstreamManifest) {
        manifestFile.writeText(adapter.toJson(updated))
        _manifest.value = updated
    }

    fun exportManifestJson(): String = adapter.toJson(_manifest.value)

    suspend fun importRawManifestJson(rawJson: String): Result<UpstreamManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = adapter.fromJson(rawJson)
                    ?: throw IllegalArgumentException("Invalid upstream_manifest.json structure.")
                saveManifestSync(parsed)
                parsed
            }
        }

    suspend fun toggleSplitEnabled(splitId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = _manifest.value
        val updated = current.copy(
            engineSplits = current.engineSplits.map {
                if (it.id == splitId) it.copy(enabled = enabled) else it
            },
            packSplits = current.packSplits.map {
                if (it.id == splitId) it.copy(enabled = enabled) else it
            },
        )
        saveManifestSync(updated)
    }

    suspend fun updateSplitBranch(splitId: String, newBranch: String) = withContext(Dispatchers.IO) {
        val clean = newBranch.trim()
        if (clean.isEmpty()) return@withContext
        val current = _manifest.value
        fun transform(s: UpstreamRepoSplit): UpstreamRepoSplit {
            if (s.id != splitId) return s
            val branches = (s.availableBranches + clean).distinct()
            return s.copy(branch = clean, availableBranches = branches, lastEtag = null)
        }
        saveManifestSync(
            current.copy(
                engineSplits = current.engineSplits.map(::transform),
                packSplits = current.packSplits.map(::transform),
            )
        )
    }

    suspend fun pinSplitSha(splitId: String, newSha: String) = withContext(Dispatchers.IO) {
        val cleanSha = newSha.trim()
        if (cleanSha.isEmpty()) return@withContext
        val current = _manifest.value
        fun transform(s: UpstreamRepoSplit): UpstreamRepoSplit {
            if (s.id != splitId) return s
            val prev = s.pinnedSha?.takeIf { it != cleanSha } ?: s.previousSha
            return s.copy(pinnedSha = cleanSha, previousSha = prev)
        }
        saveManifestSync(
            current.copy(
                engineSplits = current.engineSplits.map(::transform),
                packSplits = current.packSplits.map(::transform),
            )
        )
    }

    suspend fun rollbackSplitSha(splitId: String) = withContext(Dispatchers.IO) {
        val current = _manifest.value
        fun transform(s: UpstreamRepoSplit): UpstreamRepoSplit {
            if (s.id != splitId || s.previousSha.isNullOrBlank()) return s
            return s.copy(pinnedSha = s.previousSha, previousSha = s.pinnedSha)
        }
        saveManifestSync(
            current.copy(
                engineSplits = current.engineSplits.map(::transform),
                packSplits = current.packSplits.map(::transform),
            )
        )
    }

    suspend fun recordCheckMetadata(splitId: String, etag: String?) = withContext(Dispatchers.IO) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val current = _manifest.value
        fun transform(s: UpstreamRepoSplit): UpstreamRepoSplit =
            if (s.id == splitId) s.copy(lastEtag = etag ?: s.lastEtag, lastCheckedAt = now) else s
        saveManifestSync(
            current.copy(
                engineSplits = current.engineSplits.map(::transform),
                packSplits = current.packSplits.map(::transform),
                openResourceConnectors = current.openResourceConnectors.map {
                    if (it.id == splitId) it.copy(lastEtag = etag ?: it.lastEtag) else it
                },
            )
        )
    }

    suspend fun updateWebhookRelayUrl(url: String) = withContext(Dispatchers.IO) {
        saveManifestSync(_manifest.value.copy(webhookRelayUrl = url.trim()))
    }

    suspend fun addOpenResourceConnector(
        gameName: String,
        owner: String,
        repo: String,
        branch: String,
        resourceType: String,
    ) = withContext(Dispatchers.IO) {
        val connector = OpenResourceConnector(
            id = "orc_${UUID.randomUUID().toString().take(8)}",
            gameName = gameName.trim(),
            owner = owner.trim(),
            repo = repo.trim(),
            branch = branch.trim().ifEmpty { "main" },
            resourceType = resourceType.trim().ifEmpty { "frames" },
            enabled = true,
        )
        val current = _manifest.value
        saveManifestSync(
            current.copy(openResourceConnectors = current.openResourceConnectors + connector)
        )
    }

    suspend fun removeOpenResourceConnector(id: String) = withContext(Dispatchers.IO) {
        val current = _manifest.value
        saveManifestSync(
            current.copy(openResourceConnectors = current.openResourceConnectors.filterNot { it.id == id })
        )
    }
}
