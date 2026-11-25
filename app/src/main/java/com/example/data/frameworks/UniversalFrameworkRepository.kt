package com.example.data.frameworks

import android.content.Context
import com.example.domain.frameworks.FrameworkCategory
import com.example.domain.frameworks.FrameworkMseScaffoldGenerator
import com.example.domain.frameworks.GeneratedMseScaffold
import com.example.domain.frameworks.UniversalCardFramework
import com.example.domain.frameworks.UniversalFrameworkCatalog
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class GeneratedScaffoldResult(
    val scaffold: GeneratedMseScaffold,
    val gameFilePath: String,
    val styleFilePath: String,
)

/**
 * Loads the bundled Universal 110+ Physical & Digital TCG Card Framework Registry from
 * `assets/frameworks/tcg_card_frameworks.json` and writes clean-room `.mse-game` & `.mse-style`
 * scaffolds strictly into `custom_templates/` (never modifying read-only `upstream/`).
 */
class UniversalFrameworkRepository(
    private val context: Context,
    moshi: Moshi,
) {
    private val catalogAdapter = moshi.adapter(UniversalFrameworkCatalog::class.java)

    private val customTemplatesRoot: File
        get() = File(context.filesDir, "custom_templates").apply { mkdirs() }

    private val _frameworks = MutableStateFlow<List<UniversalCardFramework>>(loadCatalogSync())
    val frameworks: StateFlow<List<UniversalCardFramework>> = _frameworks.asStateFlow()

    private fun loadCatalogSync(): List<UniversalCardFramework> {
        return runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                catalogAdapter.fromJson(reader.readText())?.frameworks.orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    fun findById(id: String?): UniversalCardFramework? {
        val clean = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return _frameworks.value.firstOrNull { it.id.equals(clean, ignoreCase = true) }
    }

    fun findBestMatchForGame(gameId: String?): UniversalCardFramework? {
        val clean = gameId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val all = _frameworks.value
        return all.firstOrNull { it.id.equals(clean, ignoreCase = true) }
            ?: all.firstOrNull { it.upstreamMseGameId.equals(clean, ignoreCase = true) }
            ?: all.firstOrNull { it.name.lowercase().contains(clean) }
    }

    fun filterFrameworks(
        query: String,
        categoryChip: String?,
    ): List<UniversalCardFramework> {
        val q = query.trim().lowercase()
        return _frameworks.value.filter { fw ->
            val catMatch = when (categoryChip) {
                null, "ALL" -> true
                "DEFUNCT" -> fw.categoryEnum == FrameworkCategory.DEFUNCT_PHYSICAL ||
                    fw.categoryEnum == FrameworkCategory.DEFUNCT_DIGITAL
                else -> fw.category.equals(categoryChip, ignoreCase = true)
            }
            val queryMatch = q.isEmpty() ||
                fw.name.lowercase().contains(q) ||
                fw.shortName.lowercase().contains(q) ||
                fw.id.lowercase().contains(q) ||
                fw.mechanicsSummary.lowercase().contains(q)
            catMatch && queryMatch
        }
    }

    /**
     * Generates a clean-room `.mse-game` and `.mse-style` scaffold and writes it strictly inside
     * `filesDir/custom_templates/` (NEVER modifies `upstream/`).
     */
    suspend fun generateCustomTemplateScaffold(
        framework: UniversalCardFramework,
    ): Result<GeneratedScaffoldResult> = withContext(Dispatchers.IO) {
        runCatching {
            val scaffold = FrameworkMseScaffoldGenerator.generateScaffold(framework)
            val gameDir = File(customTemplatesRoot, scaffold.gameFolderName).apply { mkdirs() }
            val styleDir = File(customTemplatesRoot, scaffold.styleFolderName).apply { mkdirs() }
            val gameFile = File(gameDir, "game")
            val styleFile = File(styleDir, "style")
            gameFile.writeText(scaffold.gameFileContent)
            styleFile.writeText(scaffold.styleFileContent)
            GeneratedScaffoldResult(
                scaffold = scaffold,
                gameFilePath = gameFile.absolutePath,
                styleFilePath = styleFile.absolutePath,
            )
        }
    }

    companion object {
        const val ASSET_PATH = "frameworks/tcg_card_frameworks.json"
    }
}
