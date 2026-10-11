package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkTradingColorScheme = darkColorScheme(
    primary = EmeraldLong,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = EmeraldLongContainer,
    onPrimaryContainer = EmeraldLongLight,
    secondary = CyanAccent,
    onSecondary = Color(0xFF083344),
    secondaryContainer = CyanAccentContainer,
    onSecondaryContainer = Color(0xFF67E8F9),
    tertiary = AmberWarning,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberWarningContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CrimsonShort,
    onError = Color.White,
    errorContainer = CrimsonShortContainer,
    onErrorContainer = CrimsonShortLight,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder,
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkTradingColorScheme,
        typography = Typography,
        content = content
    )
}
