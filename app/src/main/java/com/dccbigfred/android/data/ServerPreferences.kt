package com.dccbigfred.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dccbigfred.android.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bigfred_settings")

class ServerPreferences(private val context: Context) {
    private val serverBaseUrlKey = stringPreferencesKey("server_base_url")
    private val volumeKeysThrottleEnabledKey =
        booleanPreferencesKey("volume_keys_throttle_enabled")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    private val themeSyncPrefs =
        context.getSharedPreferences(THEME_SYNC_PREFS, Context.MODE_PRIVATE)

    val serverBaseUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[serverBaseUrlKey]?.takeIf { it.isNotBlank() }
    }

    /** When true, volume keys step throttle speed in the SPA WebView. Default on. */
    val volumeKeysThrottleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[volumeKeysThrottleEnabledKey] ?: DEFAULT_VOLUME_KEYS_THROTTLE_ENABLED
    }

    suspend fun setVolumeKeysThrottleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[volumeKeysThrottleEnabledKey] = enabled
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromStorage(prefs[themeModeKey])
    }

    /**
     * Apply night mode without blocking the main thread on DataStore.
     * Uses a small SharedPreferences mirror; falls back to SYSTEM and
     * reconciles from DataStore asynchronously on first launch.
     */
    fun applyStoredNightModeSync() {
        val cached = themeSyncPrefs.getString(THEME_SYNC_KEY, null)
        if (cached != null) {
            ThemeMode.fromStorage(cached).applyNightMode()
            return
        }
        ThemeMode.SYSTEM.applyNightMode()
        CoroutineScope(Dispatchers.IO).launch {
            val mode = themeMode.first()
            mode.applyNightMode()
            themeSyncPrefs.edit().putString(THEME_SYNC_KEY, mode.storageValue).apply()
        }
    }

    suspend fun setServerBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        context.dataStore.edit { prefs ->
            prefs[serverBaseUrlKey] = normalized
        }
    }

    suspend fun clearServerBaseUrl() {
        context.dataStore.edit { prefs ->
            prefs.remove(serverBaseUrlKey)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        themeSyncPrefs.edit().putString(THEME_SYNC_KEY, mode.storageValue).apply()
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.storageValue
        }
    }

    companion object {
        const val DEFAULT_VOLUME_KEYS_THROTTLE_ENABLED = true

        private const val THEME_SYNC_PREFS = "bigfred_theme_sync"
        private const val THEME_SYNC_KEY = "theme_mode"

        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            return url.trimEnd('/')
        }
    }
}
