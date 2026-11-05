package ru.funpaymobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF08080C)
val Surface1 = Color(0xFF14141C)
val Surface2 = Color(0xFF1C1C27)
val Border = Color(0xFF2A2A3B)
val Accent = Color(0xFF9B7BFF)
val AccentDeep = Color(0xFF5B2FD6)
val Muted = Color(0xFF9A9AB2)
val Green = Color(0xFF58D6A0)
val Orange = Color(0xFFFFB45E)
val Red = Color(0xFFFF6B7A)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF14082E),
    secondary = AccentDeep,
    background = Bg,
    onBackground = Color.White,
    surface = Surface1,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Border,
    error = Red
)

@Composable
fun FunPayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
