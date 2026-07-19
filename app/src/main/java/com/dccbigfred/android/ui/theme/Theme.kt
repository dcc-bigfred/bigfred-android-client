package com.dccbigfred.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B3A4B),
    onPrimary = Color(0xFFE8F1F5),
    secondary = Color(0xFF2F6F7E),
    background = Color(0xFFF3F6F8),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB6C8),
    onPrimary = Color(0xFF0B1C24),
    secondary = Color(0xFF9CC7D2),
    background = Color(0xFF0F1A20),
    surface = Color(0xFF16242C),
)

@Composable
fun BigFredTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
