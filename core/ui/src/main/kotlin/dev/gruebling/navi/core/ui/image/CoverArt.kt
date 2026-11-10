package dev.gruebling.navi.core.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import dev.gruebling.navi.core.ui.R
import kotlinx.collections.immutable.ImmutableList

/**
 * Square cover art. [size] is the rendered size; it picks which server size to request.
 * Without a cover id: a neutral placeholder with a note icon.
 */
@Composable
fun CoverArt(
    coverArtId: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    contentDescription: String? = null,
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val request = remember(coverArtId, px) { coverArtId?.let { CoverArtRequest(it, px) } }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (request == null) {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 3),
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 2×2 mosaic of album covers, for playlists without a cover of their own. */
@Composable
fun CoverMosaic(
    coverArtIds: ImmutableList<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    if (coverArtIds.size < 4) {
        CoverArt(coverArtIds.firstOrNull(), size, modifier, shape)
        return
    }
    Column(modifier.aspectRatio(1f).clip(shape)) {
        for (row in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0..1) {
                    CoverArt(
                        coverArtIds[row * 2 + col],
                        size / 2,
                        Modifier.weight(1f),
                        shape = RectangleShape,
                    )
                }
            }
        }
    }
}
