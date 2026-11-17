package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val KeplerDarkColorScheme = darkColorScheme(
    primary = VeedIndigo,
    onPrimary = TextPrimary,
    primaryContainer = VeedIndigoDark,
    onPrimaryContainer = TextPrimary,
    secondary = VeedViolet,
    onSecondary = TextPrimary,
    secondaryContainer = StudioElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = VeedCyan,
    onTertiary = StudioBackground,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioPanel,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurface,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioBorder.copy(alpha = 0.6f),
    error = VeedRose,
    onError = TextPrimary
)

val KeplerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun KeplerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KeplerDarkColorScheme,
        typography = KeplerTypography,
        shapes = KeplerShapes,
        content = content
    )
}
