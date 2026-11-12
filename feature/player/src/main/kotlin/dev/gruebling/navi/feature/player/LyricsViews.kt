package dev.gruebling.navi.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gruebling.navi.core.model.Lyrics
import dev.gruebling.navi.core.ui.component.AccentPlayButton
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.R as UiR

/** Shown for empty lines in synced lyrics (instrumental breaks). */
private const val BREAK = "• • •"

/** Past lines in the full lyrics view: dimmer than upcoming ones, so the eye goes forward. */
private val PastLine = Color(0xFF5C5C5C)

/**
 * The lyrics panel at the bottom of the full player, as in the design: "LYRICS ··· SYNCED", then the line being
 * sung (or the first lines, for unsynced lyrics) and the next one in grey. Tap to open [LyricsView].
 */
@Composable
internal fun LyricsPeek(lyrics: Lyrics, currentLine: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val lines = lyrics.lines
    // Synced: the current line (or the first, before the song's first line). Unsynced: the first two text lines.
    val first = if (lyrics.synced) currentLine.coerceAtLeast(0) else lines.indexOfFirst { it.text.isNotEmpty() }.coerceAtLeast(0)
    val next = (first + 1 until lines.size).firstOrNull { lines[it].text.isNotEmpty() }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(NaviColors.Surface1)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_open_lyrics), onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(stringResource(R.string.player_lyrics), Modifier.weight(1f))
            if (lyrics.synced) MonoLabel(stringResource(R.string.player_lyrics_synced))
        }
        Text(
            lines.getOrNull(first)?.text?.ifEmpty { BREAK }.orEmpty(),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            next?.let { lines[it].text }.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = NaviColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Full-screen lyrics over the player. Synced lyrics follow the song: the current line is white and kept in the upper
 * third, unless the user has just scrolled (then it waits a few seconds). Tapping a synced line seeks to it.
 */
@Composable
internal fun LyricsView(
    lyrics: Lyrics,
    title: String,
    currentLine: Int,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentLine.coerceAtLeast(0))
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val lastUserScroll = remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged) lastUserScroll.longValue = System.currentTimeMillis() }

    val line by rememberUpdatedState(currentLine)
    LaunchedEffect(currentLine, dragged) {
        if (!lyrics.synced || line < 0 || dragged) return@LaunchedEffect
        if (System.currentTimeMillis() - lastUserScroll.longValue < AUTO_SCROLL_PAUSE_MS) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        listState.animateScrollToItem(line, scrollOffset = -viewport / 3)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(NaviColors.Black)
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(UiR.drawable.ic_expand_more), stringResource(R.string.cd_close_lyrics), Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MonoLabel(
                    stringResource(R.string.player_lyrics) +
                        if (lyrics.synced) " · " + stringResource(R.string.player_lyrics_synced) else "",
                )
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(48.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().fadingEdges(),
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, item ->
                val color by animateColorAsState(
                    when {
                        !lyrics.synced || index == currentLine -> NaviColors.White
                        index < currentLine -> PastLine
                        else -> NaviColors.TextSecondary
                    },
                    tween(300),
                    label = "lyricColor",
                )
                val start = item.startMs
                val seekLabel = stringResource(R.string.cd_lyrics_seek)
                Text(
                    item.text.ifEmpty { if (lyrics.synced) BREAK else "" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (lyrics.synced && start != null) {
                                Modifier.clickable(onClickLabel = seekLabel) { onSeek(start) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 6.dp),
                )
            }
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            AccentPlayButton(
                onClick = onTogglePlay,
                size = 64.dp,
                icon = if (isPlaying) UiR.drawable.ic_pause else UiR.drawable.ic_play,
                contentDescription = stringResource(if (isPlaying) UiR.string.cd_pause else UiR.string.cd_play),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Fades the list out at the top and bottom instead of cutting lines off under the header and the play button. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = 40.dp.toPx() / size.height
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** How long auto-scrolling waits after the user scrolled the lyrics themselves. */
private const val AUTO_SCROLL_PAUSE_MS = 4_000L
