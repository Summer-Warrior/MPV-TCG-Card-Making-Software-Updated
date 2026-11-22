package com.example.data.bridge

import android.util.Base64
import com.example.data.settings.EngineSplit
import com.example.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Real HTTP/REST client for the external on-device Termux ARM64 MSE Bridge (`http://127.0.0.1:8765`, configurable).
 * Implements all Section 2 contract endpoints with zero fake responses or simulated rendering.
 */
class MseBridgeClient(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val settingsRepository: SettingsRepository,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetStreamType = "application/octet-stream".toMediaType()

    private val healthAdapter = moshi.adapter(BridgeHealthResponse::class.java)
    private val syncAdapter = moshi.adapter(UpstreamSyncResponse::class.java)
    private val renderJsonAdapter = moshi.adapter(RenderCardJsonResponse::class.java)
    private val templatesAdapter = moshi.adapter(TemplatesResponse::class.java)
    private val validateReqAdapter = moshi.adapter(TemplateValidateRequest::class.java)
    private val validateRespAdapter = moshi.adapter(TemplateValidateResponse::class.java)
    private val fontsAdapter = moshi.adapter(BridgeFontsResponse::class.java)
    private val fontImportAdapter = moshi.adapter(FontImportResponse::class.java)
    private val hitboxListAdapter = moshi.adapter<List<BridgeHitboxDto>>(
        Types.newParameterizedType(List::class.java, BridgeHitboxDto::class.java)
    )
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    /**
     * GET /health -> { status, engines: { mse_modern, mse_legacy }, installed_splits: [...] }
     */
    suspend fun getHealth(): BridgeResult<BridgeHealthResponse> = executeJsonGet(
        path = "/health",
        parse = { body -> healthAdapter.fromJson(body) },
    )

    /**
     * POST /upstream/sync -> Triggers delta-only GitHub split sync from upstream_manifest.json
     */
    suspend fun triggerUpstreamSync(manifestJson: String): BridgeResult<UpstreamSyncResponse> =
        withContext(Dispatchers.IO) {
            val baseUrl = settingsRepository.currentBridgeBaseUrl()
            val path = "/upstream/sync"
            val start = System.currentTimeMillis()
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(manifestJson.toRequestBody(jsonMediaType))
                .build()
            executeRequest(request, baseUrl, path, start) { bodyStr, _ ->
                syncAdapter.fromJson(bodyStr)
                    ?: throw IllegalStateException("Empty /upstream/sync payload")
            }
        }

    /**
     * POST /sets/render -> Multipart upload sending `.mse-set` + card_index + engine_split + stylesheet_id.
     * Supports both JSON response (png_base64 + metadata) and direct `image/png` binary response with metadata headers.
     */
    suspend fun renderCard(
        mseSetBytes: ByteArray,
        setFileName: String,
        cardIndex: Int,
        engineSplit: EngineSplit,
        stylesheetId: String,
    ): BridgeResult<RenderCardResult> = withContext(Dispatchers.IO) {
        val baseUrl = settingsRepository.currentBridgeBaseUrl()
        val path = "/sets/render"
        val start = System.currentTimeMillis()

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "set_file",
                filename = setFileName.ifBlank { "active.mse-set" },
                body = mseSetBytes.toRequestBody(octetStreamType),
            )
            .addFormDataPart("card_index", cardIndex.toString())
            .addFormDataPart("engine_split", engineSplit.apiValue)
            .addFormDataPart("stylesheet_id", stylesheetId)
            .build()

        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(multipart)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                if (!response.isSuccessful) {
                    return@withContext BridgeResult.HttpError(
                        endpoint = path,
                        code = response.code,
                        body = response.body?.string().orEmpty().take(500),
                        latencyMs = elapsed,
                    )
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val rawBytes = response.body?.bytes() ?: ByteArray(0)
                if (contentType.contains("image/png")) {
                    val widthPx = response.header("X-Card-Width-Px")?.toIntOrNull() ?: 375
                    val heightPx = response.header("X-Card-Height-Px")?.toIntOrNull() ?: 523
                    val dpi = response.header("X-Card-Dpi")?.toIntOrNull() ?: 150
                    val hitboxes = response.header("X-Card-Hitboxes")
                        ?.let { runCatching { hitboxListAdapter.fromJson(it) }.getOrNull() }
                        ?: emptyList()
                    val missingFonts = response.header("X-Missing-Fonts")
                        ?.let { runCatching { stringListAdapter.fromJson(it) }.getOrNull() }
                        ?: emptyList()
                    BridgeResult.Success(
                        data = RenderCardResult(
                            pngBytes = rawBytes,
                            cardWidthPx = widthPx,
                            cardHeightPx = heightPx,
                            cardDpi = dpi,
                            hitboxes = hitboxes,
                            missingFonts = missingFonts,
                        ),
                        endpoint = path,
                        latencyMs = elapsed,
                    )
                } else {
                    val jsonStr = rawBytes.toString(Charsets.UTF_8)
                    val dto = renderJsonAdapter.fromJson(jsonStr)
                        ?: return@withContext BridgeResult.ParseError(
                            endpoint = path,
                            detail = "Failed to decode JSON render payload.",
                            latencyMs = elapsed,
                        )
                    val pngBytes = dto.pngBase64
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Base64.decode(it, Base64.DEFAULT) }
                        ?: return@withContext BridgeResult.ParseError(
                            endpoint = path,
                            detail = "Bridge response did not include png_base64 image bytes.",
                            latencyMs = elapsed,
                        )
                    BridgeResult.Success(
                        data = RenderCardResult(
                            pngBytes = pngBytes,
                            cardWidthPx = dto.cardWidthPx,
                            cardHeightPx = dto.cardHeightPx,
                            cardDpi = dto.cardDpi,
                            hitboxes = dto.hitboxes,
                            missingFonts = dto.missingFonts,
                        ),
                        endpoint = path,
                        latencyMs = elapsed,
                    )
                }
            }
        } catch (io: IOException) {
            BridgeResult.Offline(
                endpoint = path,
                baseUrl = baseUrl,
                detail = io.message ?: io::class.java.simpleName,
                latencyMs = System.currentTimeMillis() - start,
            )
        } catch (ex: Exception) {
            BridgeResult.ParseError(
                endpoint = path,
                detail = ex.message ?: ex::class.java.simpleName,
                latencyMs = System.currentTimeMillis() - start,
            )
        }
    }

    /**
     * GET /templates -> Lists available `.mse-game` & `.mse-style` templates across upstream splits and custom_templates/
     */
    suspend fun getTemplates(): BridgeResult<TemplatesResponse> = executeJsonGet(
        path = "/templates",
        parse = { body -> templatesAdapter.fromJson(body) },
    )

    /**
     * POST /templates/validate -> Runs `magicseteditor --cli --raw` on custom `.mse-game`/`.mse-style`
     */
    suspend fun validateTemplate(
        requestPayload: TemplateValidateRequest,
    ): BridgeResult<TemplateValidateResponse> = withContext(Dispatchers.IO) {
        val baseUrl = settingsRepository.currentBridgeBaseUrl()
        val path = "/templates/validate"
        val start = System.currentTimeMillis()
        val bodyJson = validateReqAdapter.toJson(requestPayload)
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
        executeRequest(request, baseUrl, path, start) { bodyStr, _ ->
            validateRespAdapter.fromJson(bodyStr)
                ?: throw IllegalStateException("Empty /templates/validate payload")
        }
    }

    /**
     * GET /fonts -> Lists all local fonts indexed in MSE (`fc-list` + pack font folders)
     */
    suspend fun getFonts(): BridgeResult<BridgeFontsResponse> = executeJsonGet(
        path = "/fonts",
        parse = { body -> fontsAdapter.fromJson(body) },
    )

    /**
     * POST /fonts/import -> Uploads legally acquired `.ttf`/`.otf`/`.zip` bytes to bridge, installs into local MSE font directory, runs `fc-cache -fv`.
     */
    suspend fun importFontArchive(
        fileName: String,
        bytes: ByteArray,
    ): BridgeResult<FontImportResponse> = withContext(Dispatchers.IO) {
        val baseUrl = settingsRepository.currentBridgeBaseUrl()
        val path = "/fonts/import"
        val start = System.currentTimeMillis()
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "font_file",
                filename = fileName,
                body = bytes.toRequestBody(octetStreamType),
            )
            .build()
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(multipart)
            .build()
        executeRequest(request, baseUrl, path, start) { bodyStr, _ ->
            fontImportAdapter.fromJson(bodyStr)
                ?: throw IllegalStateException("Empty /fonts/import payload")
        }
    }

    private suspend fun <T> executeJsonGet(
        path: String,
        parse: (String) -> T?,
    ): BridgeResult<T> = withContext(Dispatchers.IO) {
        val baseUrl = settingsRepository.currentBridgeBaseUrl()
        val start = System.currentTimeMillis()
        val request = Request.Builder()
            .url("$baseUrl$path")
            .get()
            .build()
        executeRequest(request, baseUrl, path, start) { bodyStr, _ ->
            parse(bodyStr) ?: throw IllegalStateException("Null parsed response for $path")
        }
    }

    private fun <T> executeRequest(
        request: Request,
        baseUrl: String,
        path: String,
        startMs: Long,
        parser: (String, Long) -> T,
    ): BridgeResult<T> {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startMs
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    BridgeResult.HttpError(
                        endpoint = path,
                        code = response.code,
                        body = bodyStr.take(500),
                        latencyMs = elapsed,
                    )
                } else {
                    val parsed = parser(bodyStr, elapsed)
                    BridgeResult.Success(
                        data = parsed,
                        endpoint = path,
                        latencyMs = elapsed,
                    )
                }
            }
        } catch (io: IOException) {
            BridgeResult.Offline(
                endpoint = path,
                baseUrl = baseUrl,
                detail = io.message ?: io::class.java.simpleName,
                latencyMs = System.currentTimeMillis() - startMs,
            )
        } catch (ex: Exception) {
            BridgeResult.ParseError(
                endpoint = path,
                detail = ex.message ?: ex::class.java.simpleName,
                latencyMs = System.currentTimeMillis() - startMs,
            )
        }
    }
}
