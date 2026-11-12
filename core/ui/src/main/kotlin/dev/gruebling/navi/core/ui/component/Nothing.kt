package dev.gruebling.navi.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.R
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviType

/** Horizontal screen margin of the design. */
val ScreenPadding = 20.dp

/** Small caps label in Space Mono: "SMART PLAYLISTS", "HOME", "01 / 40". Upper-cases [text] itself. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NaviColors.TextSecondary,
    bold: Boolean = false,
) {
    Text(
        text.uppercase(),
        style = NaviType.monoLabel,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The little round status light in front of labels ("● ONLINE"). */
@Composable
fun StatusDot(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 6.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** [MonoLabel] with a [StatusDot] in front. */
@Composable
fun StatusLabel(text: String, modifier: Modifier = Modifier, dotColor: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(color = dotColor)
        MonoLabel(text)
    }
}

/** Big dot-matrix screen title below the status bar, with optional trailing actions. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = ScreenPadding, end = ScreenPadding - 10.dp, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = NaviType.dotTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * Pill chip: white when selected, outlined otherwise. 44 dp tall, so it is a full touch target. As a tab (the
 * default) it reports its selected state; with another [role] (e.g. [Role.Button] for a one-off action) it is a
 * plain clickable and [selected] only picks the look.
 */
@Composable
fun NaviChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(NaviColors.White, shape)
                else Modifier.border(BorderStroke(1.dp, NaviColors.Outline), shape),
            )
            .then(
                if (role == Role.Tab) Modifier.selectable(selected = selected, role = role, onClick = onClick)
                else Modifier.clickable(role = role, onClick = onClick),
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) NaviColors.Black else NaviColors.White,
            maxLines = 1,
        )
    }
}

/** Round red play button (playlists, charts). Shows a spinner instead of the icon while [busy]. */
@Composable
fun AccentPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: Int = R.drawable.ic_play,
    contentDescription: String = stringResource(R.string.cd_play),
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else NaviColors.Surface3)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(size * 0.4f), strokeWidth = 2.dp, color = NaviColors.White)
        } else {
            Icon(painterResource(icon), contentDescription, tint = NaviColors.White, modifier = Modifier.size(size * 0.43f))
        }
    }
}

/** Four static equalizer bars marking the playing track. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier.width(24.dp).height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (h in PLAYING_BAR_HEIGHTS) {
            Box(Modifier.width(3.dp).height(h.dp).background(color))
        }
    }
}

private val PLAYING_BAR_HEIGHTS = intArrayOf(10, 18, 7, 14)

/** Small "NEW" tag in the accent color. */
@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.badge_new).uppercase(),
        style = NaviType.monoTiny,
        fontWeight = FontWeight.Bold,
        color = NaviColors.White,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Circle with a letter, e.g. the account avatar. */
@Composable
fun LetterAvatar(letter: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).background(NaviColors.Surface2, CircleShape), contentAlignment = Alignment.Center) {
        Text(letter.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current)
    }
}

/**
 * Nothing's dot-grid texture: one round point every [spacing], drawn in a single call. Used as the placeholder for
 * missing covers. The points are computed once per size, not on every draw.
 */
fun Modifier.dotGrid(
    color: Color = Color(0xFF4A4A4A),
    background: Color = NaviColors.Surface2,
    spacing: Dp = 9.dp,
    dotSize: Dp = 2.6.dp,
): Modifier = drawWithCache {
    val step = spacing.toPx()
    val stroke = dotSize.toPx()
    val points = ArrayList<Offset>()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            points += Offset(x, y)
            x += step
        }
        y += step
    }
    onDrawBehind {
        drawRect(background)
        drawPoints(points, PointMode.Points, color, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
