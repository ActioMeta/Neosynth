package com.example.neosynth.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class StreamQuality(val bitrate: Int, val format: String) {
    LOW(128, "mp3"),
    MEDIUM(192, "mp3"),
    HIGH(256, "mp3"),
    VERY_HIGH(320, "mp3"),
    LOSSLESS(0, "raw") // Sin transcodificación
}

enum class DownloadQuality(val bitrate: Int, val format: String) {
    LOW(128, "mp3"),
    MEDIUM(192, "mp3"),
    HIGH(256, "mp3"),
    VERY_HIGH(320, "mp3"),
    LOSSLESS(0, "raw") // Sin transcodificación
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class AudioSettings(
    val crossfadeEnabled: Boolean = false,
    val crossfadeDuration: Int = 5, // segundos
    val normalizeVolume: Boolean = true,
    // Streaming
    val streamWifiQuality: StreamQuality = StreamQuality.LOSSLESS,
    val streamMobileQuality: StreamQuality = StreamQuality.MEDIUM,
    // Descargas
    val downloadWifiQuality: DownloadQuality = DownloadQuality.LOSSLESS,
    val downloadMobileQuality: DownloadQuality = DownloadQuality.HIGH
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = true
)

@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object Keys {
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
        val NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")
        // Streaming
        val STREAM_WIFI_QUALITY = stringPreferencesKey("stream_wifi_quality")
        val STREAM_MOBILE_QUALITY = stringPreferencesKey("stream_mobile_quality")
        // Descargas
        val DOWNLOAD_WIFI_QUALITY = stringPreferencesKey("download_wifi_quality")
        val DOWNLOAD_MOBILE_QUALITY = stringPreferencesKey("download_mobile_quality")
        // Apariencia
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
    }

    // Audio Settings Flow
    val audioSettings: Flow<AudioSettings> = dataStore.data.map { prefs ->
        AudioSettings(
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            crossfadeDuration = prefs[Keys.CROSSFADE_DURATION] ?: 5,
            normalizeVolume = prefs[Keys.NORMALIZE_VOLUME] ?: true,
            streamWifiQuality = StreamQuality.valueOf(prefs[Keys.STREAM_WIFI_QUALITY] ?: StreamQuality.LOSSLESS.name),
            streamMobileQuality = StreamQuality.valueOf(prefs[Keys.STREAM_MOBILE_QUALITY] ?: StreamQuality.MEDIUM.name),
            downloadWifiQuality = DownloadQuality.valueOf(prefs[Keys.DOWNLOAD_WIFI_QUALITY] ?: DownloadQuality.LOSSLESS.name),
            downloadMobileQuality = DownloadQuality.valueOf(prefs[Keys.DOWNLOAD_MOBILE_QUALITY] ?: DownloadQuality.HIGH.name)
        )
    }

    // App Settings Flow
    val appSettings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
            dynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: true
        )
    }

    // Update functions
    suspend fun updateCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CROSSFADE_ENABLED] = enabled }
    }

    suspend fun updateCrossfadeDuration(duration: Int) {
        dataStore.edit { it[Keys.CROSSFADE_DURATION] = duration }
    }

    suspend fun updateNormalizeVolume(enabled: Boolean) {
        dataStore.edit { it[Keys.NORMALIZE_VOLUME] = enabled }
    }

    suspend fun updateStreamWifiQuality(quality: StreamQuality) {
        dataStore.edit { it[Keys.STREAM_WIFI_QUALITY] = quality.name }
    }

    suspend fun updateStreamMobileQuality(quality: StreamQuality) {
        dataStore.edit { it[Keys.STREAM_MOBILE_QUALITY] = quality.name }
    }

    suspend fun updateDownloadWifiQuality(quality: DownloadQuality) {
        dataStore.edit { it[Keys.DOWNLOAD_WIFI_QUALITY] = quality.name }
    }

    suspend fun updateDownloadMobileQuality(quality: DownloadQuality) {
        dataStore.edit { it[Keys.DOWNLOAD_MOBILE_QUALITY] = quality.name }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateDynamicColors(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }
    }
}
