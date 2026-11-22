package com.example.data.fonts

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class FontAvailability(val jsonValue: String, val badgeText: String) {
    INSTALLED_FREE("installed_free", "Ready ✅"),
    USER_PURCHASED_INSTALLED("user_purchased_installed", "Ready ✅ (Licensed)"),
    DOWNLOADABLE_OPEN("downloadable_open", "Free ⬇️"),
    COMMERCIAL_PAYMENT_REQUIRED("commercial_payment_required", "Paid 🔒"),
    ;

    val isReady: Boolean
        get() = this == INSTALLED_FREE || this == USER_PURCHASED_INSTALLED

    companion object {
        fun fromJsonValue(value: String?): FontAvailability =
            entries.firstOrNull { it.jsonValue == value } ?: COMMERCIAL_PAYMENT_REQUIRED
    }
}

enum class FontFilter(val label: String) {
    ALL("All"),
    READY("Ready ✅"),
    FREE("Free ⬇️"),
    PAID("Paid 🔒"),
}

@JsonClass(generateAdapter = true)
data class FontCatalogSeedFile(
    @Json(name = "schema_version") val schemaVersion: Int = 1,
    @Json(name = "fonts") val fonts: List<FontCatalogSeedEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class FontCatalogSeedEntry(
    @Json(name = "id") val id: String,
    @Json(name = "family_name") val familyName: String,
    @Json(name = "foundry") val foundry: String,
    @Json(name = "license_note") val licenseNote: String,
    @Json(name = "availability") val availability: String,
    @Json(name = "source_pack") val sourcePack: String,
    @Json(name = "purchase_url") val purchaseUrl: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
)

data class FontEntry(
    val id: String,
    val familyName: String,
    val foundry: String,
    val licenseNote: String,
    val availability: FontAvailability,
    val sourcePack: String,
    val purchaseUrl: String? = null,
    val downloadUrl: String? = null,
    val detectedMissingInTemplate: Boolean = false,
    val syncedToBridge: Boolean = false,
)

data class FontImportOutcome(
    val importedFamilies: List<String>,
    val cachedLocally: Boolean,
    val pushedToBridge: Boolean,
    val message: String,
)
