package com.dccbigfred.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dccbigfred.android.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bigfred_settings")

class ServerPreferences(private val context: Context) {
    private val serverBaseUrlKey = stringPreferencesKey("server_base_url")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val serverBaseUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[serverBaseUrlKey]?.takeIf { it.isNotBlank() }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromStorage(prefs[themeModeKey])
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
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.storageValue
        }
    }

    companion object {
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            return url.trimEnd('/')
        }
    }
}
