package com.dccbigfred.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bigfred_settings")

class ServerPreferences(private val context: Context) {
    private val serverBaseUrlKey = stringPreferencesKey("server_base_url")
    private val volumeKeysThrottleEnabledKey =
        booleanPreferencesKey("volume_keys_throttle_enabled")

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

    companion object {
        const val DEFAULT_VOLUME_KEYS_THROTTLE_ENABLED = true

        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            return url.trimEnd('/')
        }
    }
}
