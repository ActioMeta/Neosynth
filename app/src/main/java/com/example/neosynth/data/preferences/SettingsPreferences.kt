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

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class AudioSettings(
    val crossfadeEnabled: Boolean = false,
    val crossfadeDuration: Int = 5, // segundos
    val normalizeVolume: Boolean = true,
    val streamQuality: StreamQuality = StreamQuality.HIGH,
    val wifiQuality: StreamQuality = StreamQuality.LOSSLESS,
    val mobileQuality: StreamQuality = StreamQuality.MEDIUM
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
        val STREAM_QUALITY = stringPreferencesKey("stream_quality")
        val WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        val MOBILE_QUALITY = stringPreferencesKey("mobile_quality")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
    }

    // Audio Settings Flow
    val audioSettings: Flow<AudioSettings> = dataStore.data.map { prefs ->
        AudioSettings(
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            crossfadeDuration = prefs[Keys.CROSSFADE_DURATION] ?: 5,
            normalizeVolume = prefs[Keys.NORMALIZE_VOLUME] ?: true,
            streamQuality = StreamQuality.valueOf(prefs[Keys.STREAM_QUALITY] ?: StreamQuality.HIGH.name),
            wifiQuality = StreamQuality.valueOf(prefs[Keys.WIFI_QUALITY] ?: StreamQuality.LOSSLESS.name),
            mobileQuality = StreamQuality.valueOf(prefs[Keys.MOBILE_QUALITY] ?: StreamQuality.MEDIUM.name)
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

    suspend fun updateStreamQuality(quality: StreamQuality) {
        dataStore.edit { it[Keys.STREAM_QUALITY] = quality.name }
    }

    suspend fun updateWifiQuality(quality: StreamQuality) {
        dataStore.edit { it[Keys.WIFI_QUALITY] = quality.name }
    }

    suspend fun updateMobileQuality(quality: StreamQuality) {
        dataStore.edit { it[Keys.MOBILE_QUALITY] = quality.name }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateDynamicColors(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }
    }
}
