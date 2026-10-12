package com.system.screenrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalOneUIColors = staticCompositionLocalOf { OneUIDarkColors }

object OneUITheme {
    val colors: OneUIColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUIColors.current
}

private val OneUIDarkMaterialColorScheme = darkColorScheme(
    primary = OneUIDarkColors.accent,
    onPrimary = Color.White,
    background = OneUIDarkColors.background,
    onBackground = OneUIDarkColors.textPrimary,
    surface = OneUIDarkColors.cardBackground,
    onSurface = OneUIDarkColors.textPrimary,
    surfaceVariant = OneUIDarkColors.iconCircleBackground,
    onSurfaceVariant = OneUIDarkColors.textSecondary
)

private val OneUILightMaterialColorScheme = lightColorScheme(
    primary = OneUILightColors.accent,
    onPrimary = Color.White,
    background = OneUILightColors.background,
    onBackground = OneUILightColors.textPrimary,
    surface = OneUILightColors.cardBackground,
    onSurface = OneUILightColors.textPrimary,
    surfaceVariant = OneUILightColors.iconCircleBackground,
    onSurfaceVariant = OneUILightColors.textSecondary
)

@Composable
fun ScreenRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val oneUIColors = if (darkTheme) OneUIDarkColors else OneUILightColors
    val materialColorScheme = if (darkTheme) OneUIDarkMaterialColorScheme else OneUILightMaterialColorScheme

    CompositionLocalProvider(LocalOneUIColors provides oneUIColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}