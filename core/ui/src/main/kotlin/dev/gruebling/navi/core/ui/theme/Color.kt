package dev.gruebling.navi.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fallback palette for devices without dynamic color (Android 8–11).
// Seed: #F3BF48, the homelab's Noctalia/Glance accent.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF775A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9C),
    onPrimaryContainer = Color(0xFF251A00),
    secondary = Color(0xFF6A5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E0BB),
    onSecondaryContainer = Color(0xFF231A04),
    tertiary = Color(0xFF48654A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9EBC9),
    onTertiaryContainer = Color(0xFF05210B),
    background = Color(0xFFFFF8F1),
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F1),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFECE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF3E7),
    surfaceContainer = Color(0xFFF4EDE1),
    surfaceContainerHigh = Color(0xFFEEE7DB),
    surfaceContainerHighest = Color(0xFFE9E2D6),
    outline = Color(0xFF7E7667),
    outlineVariant = Color(0xFFD0C5B4),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFF3BF48),
    onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF5B4300),
    onPrimaryContainer = Color(0xFFFFDF9C),
    secondary = Color(0xFFD6C4A0),
    onSecondary = Color(0xFF392F15),
    secondaryContainer = Color(0xFF51462A),
    onSecondaryContainer = Color(0xFFF3E0BB),
    tertiary = Color(0xFFAECFAE),
    onTertiary = Color(0xFF1A3720),
    tertiaryContainer = Color(0xFF314D34),
    onTertiaryContainer = Color(0xFFC9EBC9),
    background = Color(0xFF080808),
    onBackground = Color(0xFFEAE1D4),
    surface = Color(0xFF080808),
    onSurface = Color(0xFFEAE1D4),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF121110),
    surfaceContainer = Color(0xFF181715),
    surfaceContainerHigh = Color(0xFF22201D),
    surfaceContainerHighest = Color(0xFF2D2B27),
    outline = Color(0xFF999080),
    outlineVariant = Color(0xFF4D4639),
)
