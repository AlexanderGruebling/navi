package dev.gruebling.navi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gruebling.navi.core.model.NewRelease
import dev.gruebling.navi.core.model.ReleaseType
import dev.gruebling.navi.core.ui.R
import dev.gruebling.navi.core.ui.theme.NaviColors
import java.time.format.DateTimeFormatter

/**
 * Cover of a new release (a plain web URL, not a Subsonic cover id). Releases that aren't in the library get the
 * design's dashed outline: "info only, nothing to play yet". Without a cover: the dot grid.
 */
@Composable
fun ReleaseCover(
    url: String?,
    inLibrary: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .then(if (url == null) Modifier.dotGrid() else Modifier.background(NaviColors.Surface2))
            .then(if (inLibrary) Modifier else Modifier.dashedOutline(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Dashed rounded outline drawn over the content, as in the design's "not on your server" covers. */
fun Modifier.dashedOutline(cornerRadius: Dp, color: Color = Color(0xFF8A8A8A), width: Dp = 1.5.dp): Modifier =
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val inset = stroke / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
    }

@Composable
fun releaseTypeName(type: ReleaseType): String = stringResource(
    when (type) {
        ReleaseType.Album -> R.string.release_type_album
        ReleaseType.Ep -> R.string.release_type_ep
        ReleaseType.Single -> R.string.release_type_single
    },
)

/** "Album · 11 songs · 13 Nov" (with [withYear]: "13 Nov 2026"). Meant for [MonoLabel], which upper-cases it. */
@Composable
fun releaseMeta(release: NewRelease, withYear: Boolean = false): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = release.releaseDate.format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", locale))
    return buildList {
        add(releaseTypeName(release.type))
        release.trackCount?.takeIf { it > 1 }?.let { add(pluralStringResource(R.plurals.song_count, it, it)) }
        add(date)
    }.joinToString(" · ")
}
