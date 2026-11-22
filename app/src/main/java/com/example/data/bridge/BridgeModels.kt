package com.example.data.bridge

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BridgeHealthResponse(
    @Json(name = "status") val status: String = "unknown",
    @Json(name = "engines") val engines: BridgeEnginesDto = BridgeEnginesDto(),
    @Json(name = "installed_splits") val installedSplits: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BridgeEnginesDto(
    @Json(name = "mse_modern") val mseModern: Boolean = false,
    @Json(name = "mse_legacy") val mseLegacy: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UpstreamSyncResponse(
    @Json(name = "status") val status: String = "ok",
    @Json(name = "synced_splits") val syncedSplits: List<String> = emptyList(),
    @Json(name = "fc_cache_updated") val fcCacheUpdated: Boolean = false,
    @Json(name = "message") val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class BridgeHitboxDto(
    @Json(name = "field") val field: String,
    @Json(name = "left") val left: Float,
    @Json(name = "top") val top: Float,
    @Json(name = "width") val width: Float,
    @Json(name = "height") val height: Float,
    @Json(name = "z_index") val zIndex: Int = 0,
)

@JsonClass(generateAdapter = true)
data class RenderCardJsonResponse(
    @Json(name = "png_base64") val pngBase64: String? = null,
    @Json(name = "card_width_px") val cardWidthPx: Int = 375,
    @Json(name = "card_height_px") val cardHeightPx: Int = 523,
    @Json(name = "card_dpi") val cardDpi: Int = 150,
    @Json(name = "hitboxes") val hitboxes: List<BridgeHitboxDto> = emptyList(),
    @Json(name = "missing_fonts") val missingFonts: List<String> = emptyList(),
)

data class RenderCardResult(
    val pngBytes: ByteArray,
    val cardWidthPx: Int,
    val cardHeightPx: Int,
    val cardDpi: Int,
    val hitboxes: List<BridgeHitboxDto>,
    val missingFonts: List<String>,
)

@JsonClass(generateAdapter = true)
data class BridgeTemplateDto(
    @Json(name = "id") val id: String,
    @Json(name = "game") val game: String = "",
    @Json(name = "short_name") val shortName: String = "",
    @Json(name = "source_split") val sourceSplit: String = "",
    @Json(name = "is_custom") val isCustom: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class TemplatesResponse(
    @Json(name = "templates") val templates: List<BridgeTemplateDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TemplateValidateRequest(
    @Json(name = "template_id") val templateId: String,
    @Json(name = "engine_split") val engineSplit: String = "mse_modern",
    @Json(name = "game_source") val gameSource: String? = null,
    @Json(name = "style_source") val styleSource: String,
)

@JsonClass(generateAdapter = true)
data class TemplateValidateResponse(
    @Json(name = "valid") val valid: Boolean = false,
    @Json(name = "status_code") val statusCode: Int = 1,
    @Json(name = "errors") val errors: List<String> = emptyList(),
    @Json(name = "preview_png_base64") val previewPngBase64: String? = null,
)

@JsonClass(generateAdapter = true)
data class BridgeFontDto(
    @Json(name = "family") val family: String,
    @Json(name = "source") val source: String = "fc-list",
    @Json(name = "file") val file: String? = null,
)

@JsonClass(generateAdapter = true)
data class BridgeFontsResponse(
    @Json(name = "fonts") val fonts: List<BridgeFontDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class FontImportResponse(
    @Json(name = "status") val status: String = "ok",
    @Json(name = "installed_families") val installedFamilies: List<String> = emptyList(),
    @Json(name = "fc_cache_output") val fcCacheOutput: String? = null,
)

sealed class BridgeResult<out T> {
    data class Success<T>(
        val data: T,
        val endpoint: String,
        val latencyMs: Long,
    ) : BridgeResult<T>()

    data class Offline(
        val endpoint: String,
        val baseUrl: String,
        val detail: String,
        val latencyMs: Long,
    ) : BridgeResult<Nothing>()

    data class HttpError(
        val endpoint: String,
        val code: Int,
        val body: String,
        val latencyMs: Long,
    ) : BridgeResult<Nothing>()

    data class ParseError(
        val endpoint: String,
        val detail: String,
        val latencyMs: Long,
    ) : BridgeResult<Nothing>()
}
