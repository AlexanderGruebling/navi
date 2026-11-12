package dev.gruebling.navi.feature.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.playback.model.PlayerState
import dev.gruebling.navi.core.playback.model.RepeatMode
import dev.gruebling.navi.core.ui.component.AccentPlayButton
import dev.gruebling.navi.core.ui.component.MessageToasts
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.WaveformSeekBar
import dev.gruebling.navi.core.ui.component.formatTrackTime
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.gruebling.navi.core.ui.R as UiR

/**
 * Hosts the full-screen player above the app. It slides up when [expanded], follows the finger when dragged down,
 * and shrinks with the predictive back gesture; releasing past the threshold (or committing back) calls [onCollapse].
 */
@Composable
fun FullPlayerHost(
    expanded: Boolean,
    onCollapse: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Collected even while the player is closed, so the lyrics are usually loaded before it opens.
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isEmpty, expanded) { if (expanded && state.isEmpty) onCollapse() }
    LaunchedEffect(expanded, lyrics) { if (!expanded || lyrics !is LyricsState.Available) showLyrics = false }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        val offset = remember { Animatable(heightPx) }
        var shown by remember { mutableStateOf(false) }
        var backProgress by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(expanded) {
            if (expanded) {
                shown = true
                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            } else if (shown) {
                offset.animateTo(heightPx, tween(250))
                shown = false
                backProgress = 0f
            }
        }

        PredictiveBackHandler(enabled = expanded) { events ->
            try {
                events.collect { backProgress = it.progress }
                onCollapse()
            } catch (e: CancellationException) {
                backProgress = 0f
                throw e
            }
        }

        if (shown) {
            val dragState = rememberDraggableState { delta ->
                scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
            }
            FullPlayer(
                state = state,
                lyrics = lyrics,
                showLyrics = showLyrics,
                onShowLyrics = { showLyrics = it },
                viewModel = viewModel,
                onCollapse = onCollapse,
                onOpenAlbum = { onOpenAlbum(it); onCollapse() },
                onOpenArtist = { onOpenArtist(it); onCollapse() },
                modifier = Modifier
                    .graphicsLayer {
                        translationY = offset.value
                        val scale = 1f - 0.1f * backProgress
                        scaleX = scale
                        scaleY = scale
                        shape = RoundedCornerShape((32 * backProgress).dp)
                        clip = backProgress > 0f
                    }
                    .draggable(
                        dragState,
                        Orientation.Vertical,
                        // In the lyrics view, vertical drags belong to the lyrics.
                        enabled = !showLyrics,
                        onDragStopped = { velocity ->
                            if (offset.value > heightPx * 0.2f || velocity > 1_500f) onCollapse()
                            else offset.animateTo(0f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun FullPlayer(
    state: PlayerState,
    lyrics: LyricsState,
    showLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current
    val isStarred by viewModel.isCurrentStarred.collectAsStateWithLifecycle()
    val isStartingMix by viewModel.isStartingMix.collectAsStateWithLifecycle()
    MessageToasts(viewModel.messages)
    var showQueue by rememberSaveable { mutableStateOf(false) }

    // Polled position, read only by the seek bar's draw phase and the small time labels.
    val position = remember { mutableLongStateOf(0L) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(current?.songId, state.isPlaying) {
        while (true) {
            position.longValue = viewModel.positionMs()
            if (!state.isPlaying) break
            delay(200)
        }
    }

    val available = (lyrics as? LyricsState.Available)?.lyrics
    // Recomposes when the line changes, not on every position tick. A small lead makes up for the 200 ms polling.
    val currentLine by remember(available) {
        derivedStateOf { available?.lineAt((scrubMs ?: position.longValue) + 250) ?: -1 }
    }

    Surface(modifier.fillMaxSize().testTag("full_player"), color = NaviColors.Black) {
      Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(painterResource(UiR.drawable.ic_expand_more), stringResource(R.string.cd_collapse_player), Modifier.size(26.dp))
                }
                // Balances the mix button on the right, so the title stays centered.
                Spacer(Modifier.size(48.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = current?.albumId != null) { current?.albumId?.let(onOpenAlbum) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    MonoLabel(stringResource(R.string.player_now_playing))
                    current?.album?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = viewModel::startMix, enabled = current != null && !isStartingMix) {
                    if (isStartingMix) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(UiR.drawable.ic_mix), stringResource(UiR.string.cd_instant_mix))
                    }
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(painterResource(UiR.drawable.ic_queue_music), stringResource(R.string.cd_open_queue))
                }
            }

            // The cover takes the space that's left: a square as large as fits (smaller when the lyrics panel shows).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight, 480.dp)
                Box(Modifier.size(side)) {
                    Crossfade(current?.coverArt, label = "cover") { cover ->
                        CoverArt(cover, 360.dp, Modifier.size(side), shape = MaterialTheme.shapes.extraLarge)
                    }
                    QueuePosition(state, Modifier.align(Alignment.TopEnd).padding(14.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        current?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        // Long titles scroll slowly instead of being cut off.
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
                    )
                    // The album is in the header above (and opens from there); the artist opens from here.
                    val artistId = current?.artistId
                    Text(
                        current?.artist ?: stringResource(R.string.player_unknown_artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NaviColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                enabled = artistId != null,
                                onClickLabel = stringResource(R.string.cd_open_artist),
                            ) { artistId?.let(onOpenArtist) },
                    )
                }
                IconToggleButton(checked = isStarred, onCheckedChange = { viewModel.toggleStar() }, modifier = Modifier.offset(x = 10.dp)) {
                    Icon(
                        painterResource(if (isStarred) UiR.drawable.ic_heart else UiR.drawable.ic_heart_outline),
                        contentDescription = stringResource(if (isStarred) UiR.string.cd_unfavorite else UiR.string.cd_favorite),
                        tint = if (isStarred) MaterialTheme.colorScheme.primary else NaviColors.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            WaveformSeekBar(
                positionMs = { scrubMs ?: position.longValue },
                durationMs = state.durationMs,
                seed = current?.songId,
                onSeek = { viewModel.seekTo(it); position.longValue = it },
                onScrub = { scrubMs = it },
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                MonoLabel(formatTrackTime(scrubMs ?: position.longValue))
                Spacer(Modifier.weight(1f))
                MonoLabel(formatTrackTime(state.durationMs))
            }

            Spacer(Modifier.height(20.dp))
            Controls(state, viewModel)
            AnimatedVisibility(visible = available != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                available?.let {
                    LyricsPeek(it, currentLine, onOpen = { onShowLyrics(true) }, modifier = Modifier.padding(top = 20.dp))
                }
            }
            Spacer(Modifier.height(if (available != null) 16.dp else 32.dp))
        }

        AnimatedVisibility(
            visible = showLyrics && available != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            available?.let {
                LyricsView(
                    lyrics = it,
                    title = current?.title.orEmpty(),
                    currentLine = currentLine,
                    isPlaying = state.isPlaying,
                    onSeek = { ms -> viewModel.seekTo(ms); position.longValue = ms },
                    onTogglePlay = viewModel::togglePlayPause,
                    onClose = { onShowLyrics(false) },
                )
            }
        }
      }
    }

    if (showQueue) {
        val isSavingQueue by viewModel.isSavingQueue.collectAsStateWithLifecycle()
        QueueSheet(
            state = state,
            isSaving = isSavingQueue,
            onSkipTo = viewModel::skipTo,
            onRemove = viewModel::remove,
            onMove = viewModel::move,
            onSave = viewModel::saveQueueAsPlaylist,
            onDismiss = { showQueue = false },
        )
    }
}

/** "03 / 40" on the cover: the track's place in the play order. */
@Composable
private fun QueuePosition(state: PlayerState, modifier: Modifier = Modifier) {
    val total = state.queue.size
    if (total < 2) return
    val order = state.playOrder.ifEmpty { state.queue.indices.toList() }
    val place = order.indexOf(state.currentIndex) + 1
    if (place <= 0) return
    val width = total.toString().length.coerceAtLeast(2)
    MonoLabel(
        "%0${width}d / %d".format(place, total),
        color = NaviColors.White,
        bold = true,
        modifier = modifier
            .background(NaviColors.Black.copy(alpha = 0.55f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun Controls(state: PlayerState, viewModel: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(checked = state.shuffle, onCheckedChange = { viewModel.toggleShuffle() }) {
            ModeIcon(UiR.drawable.ic_shuffle, stringResource(UiR.string.cd_shuffle), on = state.shuffle)
        }
        IconButton(onClick = viewModel::previous, enabled = state.hasPrevious, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(UiR.drawable.ic_skip_previous), stringResource(UiR.string.cd_previous), Modifier.size(30.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            AccentPlayButton(
                onClick = viewModel::togglePlayPause,
                size = 76.dp,
                icon = if (state.isPlaying) UiR.drawable.ic_pause else UiR.drawable.ic_play,
                contentDescription = stringResource(if (state.isPlaying) UiR.string.cd_pause else UiR.string.cd_play),
            )
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.size(76.dp), strokeWidth = 2.dp, color = NaviColors.White)
            }
        }
        IconButton(onClick = viewModel::next, enabled = state.hasNext, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(UiR.drawable.ic_skip_next), stringResource(UiR.string.cd_next), Modifier.size(30.dp))
        }
        IconButton(onClick = viewModel::cycleRepeat) {
            ModeIcon(
                if (state.repeat == RepeatMode.One) UiR.drawable.ic_repeat_one else UiR.drawable.ic_repeat,
                stringResource(
                    when (state.repeat) {
                        RepeatMode.Off -> R.string.cd_repeat_off
                        RepeatMode.All -> R.string.cd_repeat_all
                        RepeatMode.One -> R.string.cd_repeat_one
                    },
                ),
                on = state.repeat != RepeatMode.Off,
            )
        }
    }
}

/** Shuffle/repeat icon: white when on, grey when off, and a red dot underneath when on. */
@Composable
private fun ModeIcon(icon: Int, contentDescription: String, on: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription, tint = if (on) NaviColors.White else NaviColors.TextTertiary)
        if (on) {
            Box(
                Modifier
                    .offset(y = 16.dp)
                    .size(4.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
