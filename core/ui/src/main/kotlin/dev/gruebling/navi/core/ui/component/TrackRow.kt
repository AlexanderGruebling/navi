package dev.gruebling.navi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.R
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviType

/** Outer margin for a [TrackRow] in a full-width list: the row's own 8 dp padding then lines up with [ScreenPadding]. */
val TrackRowInset = ScreenPadding - 8.dp

/**
 * One track in a list. The caller sets the outer margin (usually [TrackRowInset]). Leading: the cover (playlists, search) or the track number in dot-matrix digits (albums).
 * The playing track sits on a raised background and shows equalizer bars instead of its number.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String?,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverArtId: String? = null,
    trackNumber: Int? = null,
    isCurrent: Boolean = false,
    isStarred: Boolean = false,
    /** Long press, usually [SongActionsSheet]. */
    onLongClick: (() -> Unit)? = null,
) {
    val nowPlaying = stringResource(R.string.cd_now_playing)
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(MaterialTheme.shapes.small)
            .then(if (isCurrent) Modifier.background(NaviColors.Surface1) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.cd_song_actions) else null,
            )
            .then(if (isCurrent) Modifier.semantics { contentDescription = nowPlaying } else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            trackNumber != null -> Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    PlayingBars()
                } else {
                    Text(
                        trackNumber.toString(),
                        style = NaviType.dotNumber,
                        color = NaviColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            else -> Box(contentAlignment = Alignment.Center) {
                CoverArt(coverArtId, 44.dp, Modifier.size(44.dp))
                if (isCurrent) {
                    Box(Modifier.matchParentSize().clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.6f)))
                    PlayingBars()
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NaviColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isStarred) {
            Icon(
                painterResource(R.drawable.ic_heart),
                contentDescription = stringResource(R.string.cd_is_favorite),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        MonoLabel(formatTrackTime(durationMs))
    }
}
