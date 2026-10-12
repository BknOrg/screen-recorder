package com.system.screenrecorder.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Default Material3 Fallbacks
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Semantic Samsung One UI color system supporting seamless Light & Dark modes.
 */
@Immutable
data class OneUIColors(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val iconCircleBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentButtonBackground: Color,
    val divider: Color,
    val warningCardBg: Color,
    val warningText: Color,
    val warningSubtext: Color,
    val warningActionBg: Color,
    val successCardBg: Color,
    val successText: Color,
    val successSubtext: Color,
    val dialogSurface: Color,
    val dialogDismissText: Color,
    val switchTrackUnchecked: Color,
    val switchThumbUnchecked: Color
)

val OneUIDarkColors = OneUIColors(
    isDark = true,
    background = Color(0xFF0F0F12),
    cardBackground = Color(0xFF1A1A1E),
    iconCircleBackground = Color(0xFF28282E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    accent = Color(0xFF4C8DF5),
    accentButtonBackground = Color(0xFF2D72D9),
    divider = Color(0xFF26262B),
    warningCardBg = Color(0xFF382314),
    warningText = Color(0xFFFF9F0A),
    warningSubtext = Color(0xFFE5C8A8),
    warningActionBg = Color(0xFFE07A25),
    successCardBg = Color(0xFF142B1A),
    successText = Color(0xFF34C759),
    successSubtext = Color(0xFFA2D4AE),
    dialogSurface = Color(0xFF222428),
    dialogDismissText = Color(0xFFA0A0A8),
    switchTrackUnchecked = Color(0xFF28282E),
    switchThumbUnchecked = Color(0xFF8E8E93)
)

val OneUILightColors = OneUIColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    cardBackground = Color(0xFFFFFFFF),
    iconCircleBackground = Color(0xFFE8E8EE),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF7C7C84),
    accent = Color(0xFF1B6EF3),
    accentButtonBackground = Color(0xFF1B6EF3),
    divider = Color(0xFFE5E5EA),
    warningCardBg = Color(0xFFFFF5E5),
    warningText = Color(0xFFD97706),
    warningSubtext = Color(0xFF78350F),
    warningActionBg = Color(0xFFD97706),
    successCardBg = Color(0xFFEBF8EE),
    successText = Color(0xFF28A745),
    successSubtext = Color(0xFF1E7E34),
    dialogSurface = Color(0xFFFFFFFF),
    dialogDismissText = Color(0xFF6E6E73),
    switchTrackUnchecked = Color(0xFFE5E5EA),
    switchThumbUnchecked = Color(0xFFFFFFFF)
)