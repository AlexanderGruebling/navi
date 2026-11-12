package dev.gruebling.navi.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Nothing-style palette from the "Dotwave × NothingOS" design: pure black, a ladder of greys for raised
 * surfaces, white text and one red accent. The app is dark only.
 */
object NaviColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)

    /** Signal red: play buttons, the active tab dot, progress, "new" badges. */
    val Accent = Color(0xFFD71921)

    /** Cards and the highlighted (playing) row. */
    val Surface1 = Color(0xFF141414)

    /** Mini player, avatar, chip-like containers. */
    val Surface2 = Color(0xFF1C1C1C)

    /** Placeholders, pressed states. */
    val Surface3 = Color(0xFF2B2B2B)

    /** Hairlines: the navigation bar's top border. */
    val Hairline = Color(0xFF1A1A1A)

    /** Outlines of unselected chips, unplayed part of seek bars. */
    val Outline = Color(0xFF333333)

    /** Secondary text. 7.4:1 on black. */
    val TextSecondary = Color(0xFF9A9A9A)

    /** Inactive icons and labels (navigation bar). 5.9:1 on black. */
    val TextTertiary = Color(0xFF8A8A8A)
}

internal val NaviColorScheme = darkColorScheme(
    primary = NaviColors.Accent,
    onPrimary = NaviColors.White,
    primaryContainer = NaviColors.Accent,
    onPrimaryContainer = NaviColors.White,
    secondary = NaviColors.White,
    onSecondary = NaviColors.Black,
    secondaryContainer = NaviColors.White,
    onSecondaryContainer = NaviColors.Black,
    tertiary = NaviColors.White,
    onTertiary = NaviColors.Black,
    tertiaryContainer = NaviColors.Surface2,
    onTertiaryContainer = NaviColors.White,
    background = NaviColors.Black,
    onBackground = NaviColors.White,
    surface = NaviColors.Black,
    onSurface = NaviColors.White,
    surfaceVariant = NaviColors.Surface2,
    onSurfaceVariant = NaviColors.TextSecondary,
    surfaceTint = NaviColors.Black,
    inverseSurface = NaviColors.White,
    inverseOnSurface = NaviColors.Black,
    inversePrimary = NaviColors.Accent,
    surfaceBright = NaviColors.Surface3,
    surfaceDim = NaviColors.Black,
    surfaceContainerLowest = NaviColors.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = NaviColors.Surface1,
    surfaceContainerHigh = NaviColors.Surface2,
    surfaceContainerHighest = NaviColors.Surface3,
    outline = NaviColors.Outline,
    outlineVariant = Color(0xFF222222),
    error = Color(0xFFFF5A5F),
    onError = NaviColors.Black,
    scrim = NaviColors.Black,
)
