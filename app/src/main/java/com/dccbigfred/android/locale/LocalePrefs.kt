package com.dccbigfred.android.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Resolves the locale the BigFred SPA should use (pl / en / de).
 * App-specific locales win; SYSTEM falls back to the device language,
 * then to Polish (same as the web i18n fallback).
 */
object LocalePrefs {
    private val SUPPORTED = setOf("pl", "en", "de")

    fun resolvedWebLocale(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            appLocales[0]?.language?.let { lang ->
                return normalize(lang)
            }
        }
        val device = LocaleListCompat.getDefault()
        if (!device.isEmpty) {
            device[0]?.language?.let { lang ->
                val normalized = lang.lowercase(Locale.ROOT)
                if (normalized in SUPPORTED) return normalized
            }
        }
        return "pl"
    }

    private fun normalize(language: String): String {
        val lang = language.lowercase(Locale.ROOT)
        return if (lang in SUPPORTED) lang else "pl"
    }
}
