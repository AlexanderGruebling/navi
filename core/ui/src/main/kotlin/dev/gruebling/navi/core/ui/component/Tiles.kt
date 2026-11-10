package dev.gruebling.navi.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.R
import dev.gruebling.navi.core.ui.image.CoverArt

/** Cover tile for Home rows: square cover, title, up to two lines of subtitle, optional "Neu" badge. */
@Composable
fun CoverTile(
    title: String,
    subtitle: String?,
    coverArtId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    isNew: Boolean = false,
    subtitleLines: Int = 2,
) {
    Column(
        modifier
            .width(size)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box {
            CoverArt(coverArtId, size, shape = MaterialTheme.shapes.medium)
            if (isNew) {
                NewBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            stringResource(R.string.badge_new),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
    )
}
