package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tcg_studio_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BRIDGE_BASE_URL = stringPreferencesKey("bridge_base_url")
        val CALIBRATION_SCALE = floatPreferencesKey("calibration_scale")
        val CALIBRATION_LOCKED = booleanPreferencesKey("calibration_locked")
        val PHYSICAL_PROFILE = stringPreferencesKey("physical_profile")
        val ACTIVE_ENGINE_SPLIT = stringPreferencesKey("active_engine_split")
        val SELECTED_FONT_FAMILY = stringPreferencesKey("selected_font_family")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val PURCHASED_LICENSED_FONTS = stringSetPreferencesKey("purchased_licensed_fonts")
        val DOWNLOADED_OPEN_FONTS = stringSetPreferencesKey("downloaded_open_fonts")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            bridgeBaseUrl = prefs[Keys.BRIDGE_BASE_URL]
                ?.takeIf { it.isNotBlank() }
                ?: StudioSettings.DEFAULT_BRIDGE_URL,
            calibrationScale = (prefs[Keys.CALIBRATION_SCALE] ?: 1.0f)
                .coerceIn(StudioSettings.MIN_CALIBRATION, StudioSettings.MAX_CALIBRATION),
            calibrationLocked = prefs[Keys.CALIBRATION_LOCKED] ?: false,
            physicalProfile = PhysicalCardProfile.fromId(prefs[Keys.PHYSICAL_PROFILE]),
            activeEngineSplit = EngineSplit.fromApiValue(prefs[Keys.ACTIVE_ENGINE_SPLIT]),
            selectedFontFamily = prefs[Keys.SELECTED_FONT_FAMILY],
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
        )
    }

    val purchasedLicensedFontsFlow: Flow<Set<String>> = context.studioDataStore.data.map { prefs ->
        prefs[Keys.PURCHASED_LICENSED_FONTS] ?: emptySet()
    }

    val downloadedOpenFontsFlow: Flow<Set<String>> = context.studioDataStore.data.map { prefs ->
        prefs[Keys.DOWNLOADED_OPEN_FONTS] ?: emptySet()
    }

    suspend fun currentBridgeBaseUrl(): String =
        settingsFlow.first().bridgeBaseUrl

    suspend fun updateBridgeBaseUrl(rawUrl: String) {
        val cleaned = normalizeBridgeUrl(rawUrl)
        context.studioDataStore.edit { prefs ->
            prefs[Keys.BRIDGE_BASE_URL] = cleaned
        }
    }

    suspend fun updateCalibrationScale(scale: Float) {
        val clamped = scale.coerceIn(StudioSettings.MIN_CALIBRATION, StudioSettings.MAX_CALIBRATION)
        context.studioDataStore.edit { prefs ->
            prefs[Keys.CALIBRATION_SCALE] = clamped
        }
    }

    suspend fun updateCalibrationLocked(locked: Boolean) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.CALIBRATION_LOCKED] = locked
        }
    }

    suspend fun updatePhysicalProfile(profile: PhysicalCardProfile) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.PHYSICAL_PROFILE] = profile.id
        }
    }

    suspend fun updateEngineSplit(split: EngineSplit) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_ENGINE_SPLIT] = split.apiValue
        }
    }

    suspend fun updateSelectedFontFamily(family: String?) {
        context.studioDataStore.edit { prefs ->
            if (family.isNullOrBlank()) {
                prefs.remove(Keys.SELECTED_FONT_FAMILY)
            } else {
                prefs[Keys.SELECTED_FONT_FAMILY] = family
            }
        }
    }

    suspend fun updateDarkTheme(darkTheme: Boolean) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = darkTheme
        }
    }

    suspend fun markPurchasedFontLicensed(familyNames: Collection<String>) {
        if (familyNames.isEmpty()) return
        context.studioDataStore.edit { prefs ->
            val current = prefs[Keys.PURCHASED_LICENSED_FONTS] ?: emptySet()
            prefs[Keys.PURCHASED_LICENSED_FONTS] = current + familyNames
        }
    }

    suspend fun markOpenFontDownloaded(familyName: String) {
        context.studioDataStore.edit { prefs ->
            val current = prefs[Keys.DOWNLOADED_OPEN_FONTS] ?: emptySet()
            prefs[Keys.DOWNLOADED_OPEN_FONTS] = current + familyName
        }
    }

    companion object {
        fun normalizeBridgeUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return StudioSettings.DEFAULT_BRIDGE_URL
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }
    }
}
