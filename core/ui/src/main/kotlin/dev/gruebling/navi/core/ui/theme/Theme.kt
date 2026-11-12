package dev.gruebling.navi.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NaviShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App theme, after the "Dotwave × NothingOS" design: black, monochrome, one red accent; Space Grotesk with Doto
 * and Space Mono for display text and labels (see [NaviType]). Dark only, no dynamic color: the look is the brand.
 */
@Composable
fun NaviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NaviColorScheme,
        typography = NaviTypography,
        shapes = NaviShapes,
        content = content,
    )
}
