package com.dccbigfred.android.ui.theme

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (this) {
                SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
