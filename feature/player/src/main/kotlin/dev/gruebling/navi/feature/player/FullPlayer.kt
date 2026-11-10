package dev.gruebling.navi.feature.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.playback.model.PlayerState
import dev.gruebling.navi.core.playback.model.RepeatMode
import dev.gruebling.navi.core.ui.component.WavySeekBar
import dev.gruebling.navi.core.ui.component.formatTrackTime
import dev.gruebling.navi.core.ui.image.CoverArt
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
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isEmpty, expanded) { if (expanded && state.isEmpty) onCollapse() }

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
                viewModel = viewModel,
                onCollapse = onCollapse,
                onOpenAlbum = { onOpenAlbum(it); onCollapse() },
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
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current
    val isStarred by viewModel.isCurrentStarred.collectAsStateWithLifecycle()
    var showQueue by rememberSaveable { mutableStateOf(false) }

    val surface = MaterialTheme.colorScheme.surface
    val coverColor = rememberCoverColor(current?.coverArt)
    val tint by animateColorAsState(
        coverColor?.let { lerp(surface, it, 0.45f) } ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(600),
        label = "coverTint",
    )

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

    Surface(modifier.fillMaxSize(), color = surface) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to tint, 0.75f to surface))
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(painterResource(UiR.drawable.ic_expand_more), stringResource(R.string.cd_collapse_player))
                }
                Text(
                    stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { showQueue = true }) {
                    Icon(painterResource(UiR.drawable.ic_queue_music), stringResource(R.string.cd_open_queue))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(current?.coverArt, label = "cover") { cover ->
                    CoverArt(
                        cover,
                        360.dp,
                        Modifier.fillMaxWidth().widthIn(max = 480.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        // Long titles scroll slowly instead of being cut off.
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
                    )
                    Text(
                        listOfNotNull(current?.artist ?: stringResource(R.string.player_unknown_artist), current?.album)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = current?.albumId != null) { current?.albumId?.let(onOpenAlbum) },
                    )
                }
                IconToggleButton(checked = isStarred, onCheckedChange = { viewModel.toggleStar() }) {
                    Icon(
                        painterResource(if (isStarred) UiR.drawable.ic_star else UiR.drawable.ic_star_outline),
                        contentDescription = stringResource(if (isStarred) UiR.string.cd_unfavorite else UiR.string.cd_favorite),
                        tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            WavySeekBar(
                positionMs = { scrubMs ?: position.longValue },
                durationMs = state.durationMs,
                isPlaying = state.isPlaying && scrubMs == null,
                onSeek = { viewModel.seekTo(it); position.longValue = it },
                onScrub = { scrubMs = it },
            )
            Row(Modifier.fillMaxWidth()) {
                val shown = scrubMs ?: position.longValue
                Text(formatTrackTime(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    "-" + formatTrackTime((state.durationMs - shown).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Controls(state, viewModel)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showQueue) {
        QueueSheet(
            state = state,
            onSkipTo = viewModel::skipTo,
            onRemove = viewModel::remove,
            onDismiss = { showQueue = false },
        )
    }
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
        IconButton(onClick = viewModel::previous, enabled = state.hasPrevious, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(UiR.drawable.ic_skip_previous), stringResource(UiR.string.cd_previous), Modifier.size(36.dp))
        }
        FilledIconButton(
            onClick = viewModel::togglePlayPause,
            modifier = Modifier.size(76.dp),
            shape = if (state.isPlaying) MaterialTheme.shapes.large else RoundedCornerShape(50),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.isBuffering) CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                Icon(
                    painterResource(if (state.isPlaying) UiR.drawable.ic_pause else UiR.drawable.ic_play),
                    stringResource(if (state.isPlaying) UiR.string.cd_pause else UiR.string.cd_play),
                    Modifier.size(36.dp),
                )
            }
        }
        IconButton(onClick = viewModel::next, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(UiR.drawable.ic_skip_next), stringResource(UiR.string.cd_next), Modifier.size(36.dp))
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

/**
 * Shuffle/repeat icon. "On" is shown by a dot underneath, not only by color: with some wallpapers the dynamic
 * primary is nearly white and indistinguishable from the "off" tint.
 */
@Composable
private fun ModeIcon(icon: Int, contentDescription: String, on: Boolean) {
    val color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Box(contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription, tint = color)
        if (on) {
            Box(
                Modifier
                    .offset(y = 16.dp)
                    .size(4.dp)
                    .background(color, CircleShape),
            )
        }
    }
}
