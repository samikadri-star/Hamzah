package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GoldSMCColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = DarkCarbon,
    secondary = GoldMuted,
    onSecondary = TextPrimary,
    tertiary = BlueFVG,
    background = DarkCarbon,
    onBackground = TextPrimary,
    surface = DarkCard,
    onSurface = TextPrimary,
    surfaceVariant = DarkCardHeader,
    onSurfaceVariant = TextPrimary,
    outline = DarkBorder,
    error = RedBearish
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GoldSMCColorScheme,
        typography = Typography,
        content = content
    )
}
