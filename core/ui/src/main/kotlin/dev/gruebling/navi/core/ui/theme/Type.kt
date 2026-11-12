package dev.gruebling.navi.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.gruebling.navi.core.ui.R

/*
 * Three families, as in the design:
 * - Space Grotesk for everything readable (titles, lists, body).
 * - Doto, a dot-matrix face, for screen titles and big numbers only.
 * - Space Mono in caps with wide tracking for small labels ("SMART PLAYLISTS", "HOME", times).
 * Doto and Space Grotesk are variable fonts; each weight is an instance of the same file (API 26+).
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk, FontWeight.Normal),
    variable(R.font.space_grotesk, FontWeight.Medium),
    variable(R.font.space_grotesk, FontWeight.SemiBold),
    variable(R.font.space_grotesk, FontWeight.Bold),
)

val Doto = FontFamily(
    variable(R.font.doto, FontWeight.Bold),
    variable(R.font.doto, FontWeight.Black),
)

val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** Text styles outside the Material type scale. */
object NaviType {
    /** Screen titles: "Guten Abend", "Library", "Search". */
    val dotTitle = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 40.sp)

    /** Big numbers and words on covers and in lists (track numbers). */
    val dotNumber = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 22.sp)

    /** Small caps labels. Pass text in upper case (see [MonoLabel][dev.gruebling.navi.core.ui.component.MonoLabel]). */
    val monoLabel = TextStyle(fontFamily = SpaceMono, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em)

    /** Navigation bar labels. */
    val monoTiny = TextStyle(fontFamily = SpaceMono, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.12.em)
}

private val base = Typography()

private fun TextStyle.grotesk(weight: FontWeight? = fontWeight) = copy(fontFamily = SpaceGrotesk, fontWeight = weight)

internal val NaviTypography = Typography(
    displayLarge = base.displayLarge.grotesk(),
    displayMedium = base.displayMedium.grotesk(),
    displaySmall = base.displaySmall.grotesk(),
    headlineLarge = base.headlineLarge.grotesk(FontWeight.Bold),
    headlineMedium = base.headlineMedium.grotesk(FontWeight.Bold).copy(fontSize = 28.sp, letterSpacing = (-0.01).em),
    headlineSmall = base.headlineSmall.grotesk(FontWeight.Bold).copy(letterSpacing = (-0.01).em),
    titleLarge = base.titleLarge.grotesk(FontWeight.SemiBold).copy(fontSize = 20.sp),
    titleMedium = base.titleMedium.grotesk(FontWeight.SemiBold).copy(fontSize = 15.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.grotesk(FontWeight.SemiBold).copy(fontSize = 13.sp, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.grotesk().copy(fontSize = 15.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.grotesk().copy(fontSize = 14.sp, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.grotesk().copy(fontSize = 13.sp, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.grotesk(FontWeight.SemiBold).copy(letterSpacing = 0.sp),
    labelMedium = base.labelMedium.copy(fontFamily = SpaceMono, letterSpacing = 0.08.em),
    labelSmall = base.labelSmall.copy(fontFamily = SpaceMono, letterSpacing = 0.1.em),
)
