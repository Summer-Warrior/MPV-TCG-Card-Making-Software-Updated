package com.example.data.fonts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.bridge.BridgeFontDto
import com.example.data.bridge.BridgeResult
import com.example.data.bridge.MseBridgeClient
import com.example.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Manages the unified searchable font catalog:
 * 1. Installed free/MSE pack fonts (`Magic - Fonts`, `Other - Fonts`, `Font-Pack`, Android system fonts)
 *    + bridge-reported fonts + user-purchased imported fonts.
 * 2. Open-source downloadable fonts (`downloadable_open`, SIL OFL / Google Fonts) fetched on demand.
 * 3. Commercial foundry fonts from `assets/font_catalog.json` PLUS dynamically detected missing fonts
 *    from active `.mse-style` or bridge render responses (`commercial_payment_required`).
 */
class FontCatalogRepository(
    private val context: Context,
    private val moshi: Moshi,
    private val okHttpClient: OkHttpClient,
    private val bridgeClient: MseBridgeClient,
    private val settingsRepository: SettingsRepository,
) {
    private val userFontsDir: File
        get() = File(context.filesDir, "user_fonts").apply { mkdirs() }

    private val seedEntries: List<FontCatalogSeedEntry> by lazy { loadSeedCatalog() }

    private val bridgeInstalledFonts = MutableStateFlow<List<BridgeFontDto>>(emptyList())
    private val templateReferencedFonts = MutableStateFlow<List<String>>(emptyList())
    private val bridgeMissingFonts = MutableStateFlow<List<String>>(emptyList())

    private val androidSystemFonts: List<FontEntry> = listOf(
        systemFont("Roboto", "Google / Android System"),
        systemFont("Noto Serif", "Google / Android System"),
        systemFont("Noto Sans", "Google / Android System"),
        systemFont("Droid Sans Mono", "Android System"),
    )

    val unifiedFontsFlow: Flow<List<FontEntry>> = combine(
        bridgeInstalledFonts,
        templateReferencedFonts,
        bridgeMissingFonts,
        settingsRepository.purchasedLicensedFontsFlow,
        settingsRepository.downloadedOpenFontsFlow,
    ) { bridgeFonts, styleFonts, missingFromBridge, licensedSet, downloadedOpenSet ->
        buildUnifiedCatalog(
            bridgeFonts = bridgeFonts,
            styleFonts = styleFonts,
            missingFromBridge = missingFromBridge,
            licensedSet = licensedSet,
            downloadedOpenSet = downloadedOpenSet,
        )
    }

    fun updateStyleReferencedFonts(fonts: List<String>) {
        templateReferencedFonts.value = fonts.filter { it.isNotBlank() }.distinct()
    }

    fun updateBridgeMissingFonts(fonts: List<String>) {
        bridgeMissingFonts.value = fonts.filter { it.isNotBlank() }.distinct()
    }

    suspend fun refreshFromBridge(): BridgeResult<List<BridgeFontDto>> {
        return when (val result = bridgeClient.getFonts()) {
            is BridgeResult.Success -> {
                bridgeInstalledFonts.value = result.data.fonts
                BridgeResult.Success(result.data.fonts, result.endpoint, result.latencyMs)
            }

            is BridgeResult.Offline -> result
            is BridgeResult.HttpError -> result
            is BridgeResult.ParseError -> result
        }
    }

    /**
     * Downloads an open-source (`downloadable_open`) SIL OFL font on demand, caches locally,
     * and pushes to the MSE bridge via `POST /fonts/import` if online.
     */
    suspend fun downloadOpenFont(entry: FontEntry): Result<FontImportOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = entry.downloadUrl
                    ?: throw IllegalStateException("No open download URL configured for ${entry.familyName}.")
                val request = Request.Builder().url(url).get().build()
                val bytes = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Download failed with HTTP ${resp.code}.")
                    }
                    resp.body?.bytes() ?: throw IllegalStateException("Empty font response.")
                }

                val safeName = "${entry.id}.ttf"
                File(userFontsDir, safeName).writeBytes(bytes)
                settingsRepository.markOpenFontDownloaded(entry.familyName)

                val pushed = when (val bridgeResp = bridgeClient.importFontArchive(safeName, bytes)) {
                    is BridgeResult.Success -> {
                        refreshFromBridge()
                        true
                    }

                    else -> false
                }

                FontImportOutcome(
                    importedFamilies = listOf(entry.familyName),
                    cachedLocally = true,
                    pushedToBridge = pushed,
                    message = if (pushed) {
                        "Downloaded ${entry.familyName} and installed into MSE Bridge fc-cache."
                    } else {
                        "Downloaded ${entry.familyName} locally (will sync to MSE Bridge when online)."
                    },
                )
            }
        }

    /**
     * Imports a user's legally acquired commercial `.ttf`, `.otf`, or vendor `.zip` via Android SAF,
     * validates/extracts font binaries, caches locally, forwards to `POST /fonts/import`,
     * and flips the target font's badge from `Paid 🔒` to `Ready ✅ (Licensed)`.
     */
    suspend fun importPurchasedFontUri(
        uri: Uri,
        expectedFamilyName: String? = null,
    ): Result<FontImportOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveDisplayName(uri) ?: "imported_font.ttf"
            val lower = fileName.lowercase()
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf") && !lower.endsWith(".zip")) {
                throw IllegalArgumentException("Unsupported font file '$fileName'. Select a .ttf, .otf, or .zip archive.")
            }

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read selected font file.")
            if (bytes.isEmpty()) {
                throw IllegalArgumentException("Selected font file is empty.")
            }

            val extractedFontNames = mutableListOf<String>()
            if (lower.endsWith(".zip")) {
                ZipInputStream(bytes.inputStream()).use { zis ->
                    var zipEntry = zis.nextEntry
                    while (zipEntry != null) {
                        val name = zipEntry.name.substringAfterLast('/')
                        val nameLower = name.lowercase()
                        if (!zipEntry.isDirectory && (nameLower.endsWith(".ttf") || nameLower.endsWith(".otf"))) {
                            val fontBytes = zis.readBytes()
                            File(userFontsDir, name).writeBytes(fontBytes)
                            extractedFontNames += inferFamilyNameFromFilename(name)
                        }
                        zis.closeEntry()
                        zipEntry = zis.nextEntry
                    }
                }
                if (extractedFontNames.isEmpty()) {
                    throw IllegalArgumentException("ZIP archive did not contain any .ttf or .otf font files.")
                }
            } else {
                File(userFontsDir, fileName).writeBytes(bytes)
                extractedFontNames += inferFamilyNameFromFilename(fileName)
            }

            // Forward raw archive/font to bridge `POST /fonts/import`
            val bridgeResult = bridgeClient.importFontArchive(fileName, bytes)
            val bridgeFamilies = (bridgeResult as? BridgeResult.Success)?.data?.installedFamilies.orEmpty()
            val pushedToBridge = bridgeResult is BridgeResult.Success

            val allLicensedFamilies = buildSet {
                if (!expectedFamilyName.isNullOrBlank()) add(expectedFamilyName)
                addAll(extractedFontNames)
                addAll(bridgeFamilies)
            }

            settingsRepository.markPurchasedFontLicensed(allLicensedFamilies)
            if (pushedToBridge) {
                refreshFromBridge()
            }

            FontImportOutcome(
                importedFamilies = allLicensedFamilies.toList(),
                cachedLocally = true,
                pushedToBridge = pushedToBridge,
                message = if (pushedToBridge) {
                    "Licensed font imported & indexed in MSE Bridge (${allLicensedFamilies.joinToString(", ")})."
                } else {
                    "Licensed font cached locally (${allLicensedFamilies.joinToString(", ")}). Bridge is offline; connect bridge to push to fc-cache."
                },
            )
        }
    }

    private fun buildUnifiedCatalog(
        bridgeFonts: List<BridgeFontDto>,
        styleFonts: List<String>,
        missingFromBridge: List<String>,
        licensedSet: Set<String>,
        downloadedOpenSet: Set<String>,
    ): List<FontEntry> {
        val byNormalizedName = linkedMapOf<String, FontEntry>()
        val bridgeInstalledNormalized = bridgeFonts.map { normalizeName(it.family) }.toSet()
        val licensedNormalized = licensedSet.map { normalizeName(it) }.toSet()
        val downloadedNormalized = downloadedOpenSet.map { normalizeName(it) }.toSet()
        val missingNormalized = missingFromBridge.map { normalizeName(it) }.toSet()

        // 1. Seed catalog entries (`font_catalog.json`)
        for (seed in seedEntries) {
            val norm = normalizeName(seed.familyName)
            val seedAvail = FontAvailability.fromJsonValue(seed.availability)
            val resolvedAvail = when {
                norm in licensedNormalized -> FontAvailability.USER_PURCHASED_INSTALLED
                norm in downloadedNormalized -> FontAvailability.INSTALLED_FREE
                norm in bridgeInstalledNormalized && seedAvail == FontAvailability.COMMERCIAL_PAYMENT_REQUIRED ->
                    FontAvailability.USER_PURCHASED_INSTALLED
                norm in bridgeInstalledNormalized -> FontAvailability.INSTALLED_FREE
                else -> seedAvail
            }
            val isMissing = norm in missingNormalized && !resolvedAvail.isReady
            byNormalizedName[norm] = FontEntry(
                id = seed.id,
                familyName = seed.familyName,
                foundry = seed.foundry,
                licenseNote = seed.licenseNote,
                availability = resolvedAvail,
                sourcePack = seed.sourcePack,
                purchaseUrl = seed.purchaseUrl,
                downloadUrl = seed.downloadUrl,
                detectedMissingInTemplate = isMissing,
                syncedToBridge = norm in bridgeInstalledNormalized,
            )
        }

        // 2. Android system fonts
        for (sys in androidSystemFonts) {
            val norm = normalizeName(sys.familyName)
            if (!byNormalizedName.containsKey(norm)) {
                byNormalizedName[norm] = sys.copy(
                    syncedToBridge = norm in bridgeInstalledNormalized,
                )
            }
        }

        // 3. Bridge-installed fonts not already in seed catalog
        for (bf in bridgeFonts) {
            val norm = normalizeName(bf.family)
            if (!byNormalizedName.containsKey(norm)) {
                val isUserLicensed = norm in licensedNormalized
                byNormalizedName[norm] = FontEntry(
                    id = "bridge_$norm",
                    familyName = bf.family,
                    foundry = bf.source.ifBlank { "MSE Bridge Local Index" },
                    licenseNote = "Indexed on your local Termux MSE Bridge (${bf.source}).",
                    availability = if (isUserLicensed) {
                        FontAvailability.USER_PURCHASED_INSTALLED
                    } else {
                        FontAvailability.INSTALLED_FREE
                    },
                    sourcePack = bf.source.ifBlank { "MSE Bridge" },
                    syncedToBridge = true,
                )
            }
        }

        // 4. User-imported licensed fonts that weren't in seed catalog
        for (licensedName in licensedSet) {
            val norm = normalizeName(licensedName)
            if (!byNormalizedName.containsKey(norm)) {
                byNormalizedName[norm] = FontEntry(
                    id = "licensed_$norm",
                    familyName = licensedName,
                    foundry = "User Licensed Import",
                    licenseNote = "Imported via SAF into TCG Studio user font cache.",
                    availability = FontAvailability.USER_PURCHASED_INSTALLED,
                    sourcePack = "User Licensed",
                    syncedToBridge = norm in bridgeInstalledNormalized,
                )
            }
        }

        // 5. Dynamically detected missing fonts from active `.mse-style` or bridge `missing_fonts`
        val dynamicCandidates = (styleFonts + missingFromBridge).distinct()
        for (fontName in dynamicCandidates) {
            val norm = normalizeName(fontName)
            val existing = byNormalizedName[norm]
            if (existing != null) {
                if (!existing.availability.isReady) {
                    byNormalizedName[norm] = existing.copy(detectedMissingInTemplate = true)
                }
            } else {
                val encoded = URLEncoder.encode(fontName, StandardCharsets.UTF_8.name())
                byNormalizedName[norm] = FontEntry(
                    id = "detected_$norm",
                    familyName = fontName,
                    foundry = "Detected in Active .mse-style (External Foundry)",
                    licenseNote = "Required by the active .mse-style template but not installed locally. " +
                        "Purchase or obtain an authorized desktop license in your browser, then import the .ttf/.otf/.zip file.",
                    availability = FontAvailability.COMMERCIAL_PAYMENT_REQUIRED,
                    sourcePack = "Active Template Requirement",
                    purchaseUrl = "https://www.myfonts.com/search?query=$encoded",
                    detectedMissingInTemplate = true,
                    syncedToBridge = false,
                )
            }
        }

        return byNormalizedName.values.sortedWith(
            compareByDescending<FontEntry> { it.detectedMissingInTemplate }
                .thenBy { availabilitySortRank(it.availability) }
                .thenBy { it.familyName.lowercase() }
        )
    }

    private fun availabilitySortRank(avail: FontAvailability): Int = when (avail) {
        FontAvailability.USER_PURCHASED_INSTALLED -> 0
        FontAvailability.INSTALLED_FREE -> 1
        FontAvailability.DOWNLOADABLE_OPEN -> 2
        FontAvailability.COMMERCIAL_PAYMENT_REQUIRED -> 3
    }

    private fun loadSeedCatalog(): List<FontCatalogSeedEntry> {
        return runCatching {
            val json = context.assets.open("font_catalog.json").bufferedReader().use { it.readText() }
            val adapter = moshi.adapter(FontCatalogSeedFile::class.java)
            adapter.fromJson(json)?.fonts.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun systemFont(name: String, foundry: String) = FontEntry(
        id = "sys_${normalizeName(name)}",
        familyName = name,
        foundry = foundry,
        licenseNote = "Built-in Android system typeface.",
        availability = FontAvailability.INSTALLED_FREE,
        sourcePack = "Android System",
    )

    private fun normalizeName(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "")

    private fun inferFamilyNameFromFilename(fileName: String): String =
        fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifEmpty { "Imported Font" }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
