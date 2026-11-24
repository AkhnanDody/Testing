package com.golda.veniceseedream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentYellow = Color(0xFFFFC400)
val DarkBackground = Color(0xFF0F0F12)
val DarkSurface = Color(0xFF1A1A1F)
val DarkSurfaceVariant = Color(0xFF26262C)
val TextMuted = Color(0xFF9E9EA7)
val ErrorRed = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = AccentYellow,
    onPrimary = Color(0xFF1A1A1A),
    secondary = AccentYellow,
    onSecondary = Color(0xFF1A1A1A),
    background = DarkBackground,
    onBackground = Color(0xFFF2F2F2),
    surface = DarkSurface,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextMuted,
    error = ErrorRed,
    onError = Color(0xFF1A1A1A)
)

@Composable
fun VeniceSeedreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
