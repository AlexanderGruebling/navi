package dev.gruebling.navi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors

/** Cover tile for horizontal rows and grids: rounded square cover, title, grey subtitle, optional "NEW" badge. */
@Composable
fun CoverTile(
    title: String,
    subtitle: String?,
    coverArtId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    isNew: Boolean = false,
    subtitleLines: Int = 2,
    /** Fill the available width instead of [size] (grids); [size] then only picks the image resolution. */
    fillWidth: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    textAlign: TextAlign? = null,
) {
    Column(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(size))
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box {
            CoverArt(coverArtId, size, shape = shape)
            if (isNew) {
                NewBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NaviColors.TextSecondary,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                maxLines = subtitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact shortcut tile (Home's two-column grid): square cover flush left in a dark rounded bar, bold title. */
@Composable
fun ShortcutTile(
    title: String,
    coverArtId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
) {
    Row(
        modifier
            .height(56.dp)
            .clip(MaterialTheme.shapes.small)
            .background(NaviColors.Surface1)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(coverArtId, 56.dp, Modifier.size(56.dp), shape = RectangleShape)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (isNew) StatusDot(Modifier.padding(end = 10.dp))
    }
}

/** Section title with an optional mono label on the right ("Made for you ··· SMART PLAYLISTS"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, label: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (label != null) MonoLabel(label)
    }
}
