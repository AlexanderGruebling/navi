package dev.gruebling.navi.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import kotlinx.coroutines.delay
import dev.gruebling.navi.core.ui.R as UiR

/**
 * Persistent mini player above the bottom navigation: cover, title, artist, favorite and play/pause, with a thin
 * progress line along the bottom. Swipe up opens the full player; swipe sideways stops and clears the queue.
 * Hidden while the queue is empty.
 */
@Composable
fun MiniPlayer(onExpand: () -> Unit, modifier: Modifier = Modifier, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state.current

    // Progress lives in its own state and is only read inside the indicator's draw lambda:
    // ticking it redraws the bar, it never recomposes the mini player.
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(current?.songId, state.isPlaying, state.durationMs) {
        while (true) {
            val duration = state.durationMs
            progress.floatValue = if (duration > 0) (viewModel.positionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
            if (!state.isPlaying) break
            delay(250)
        }
    }

    val isStarred by viewModel.isCurrentStarred.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary

    val clearLabel = stringResource(R.string.cd_clear_queue)
    AnimatedVisibility(visible = current != null, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        // Swiping it away to either side stops playback and empties the queue. The state lives inside
        // AnimatedVisibility, so the next queue gets a fresh, centered mini player.
        val swipeState = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(
            state = swipeState,
            backgroundContent = {},
            onDismiss = { viewModel.clearQueue() },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    // Fades out as it slides away.
                    .graphicsLayer {
                        alpha = 1f - (swipeState.progress.takeIf { swipeState.targetValue != SwipeToDismissBoxValue.Settled } ?: 0f)
                    }
                    .semantics { customActions = listOf(CustomAccessibilityAction(clearLabel) { viewModel.clearQueue(); true }) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .height(60.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(NaviColors.Surface2)
                    .testTag("mini_player")
                    .clickable(onClick = onExpand)
                    // Swipe up opens the full player, like the system's bottom sheets.
                    .pointerInput(onExpand) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total < -32.dp.toPx()) onExpand() },
                        ) { change, dy ->
                            change.consume()
                            total += dy
                        }
                    }
                    // Progress: a 2 dp line along the bottom edge, drawn from the polled state without recomposing.
                    .drawWithContent {
                        drawContent()
                        val inset = 14.dp.toPx()
                        val y = size.height - 1.dp.toPx()
                        val width = size.width - 2 * inset
                        val stroke = 2.dp.toPx()
                        drawLine(NaviColors.Outline, Offset(inset, y), Offset(inset + width, y), stroke)
                        drawLine(accent, Offset(inset, y), Offset(inset + width * progress.floatValue, y), stroke)
                    },
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CoverArt(current?.coverArt, 44.dp, Modifier.size(44.dp))
                    Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            current?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MonoLabel(current?.artist ?: stringResource(R.string.player_unknown_artist))
                    }
                    IconButton(onClick = viewModel::toggleStar) {
                        Icon(
                            painterResource(if (isStarred) UiR.drawable.ic_heart else UiR.drawable.ic_heart_outline),
                            contentDescription = stringResource(if (isStarred) UiR.string.cd_unfavorite else UiR.string.cd_favorite),
                            tint = if (isStarred) accent else NaviColors.White,
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NaviColors.White)
                            .clickable(role = Role.Button, onClick = viewModel::togglePlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 2.dp, color = NaviColors.Black)
                        }
                        Icon(
                            painterResource(if (state.isPlaying) UiR.drawable.ic_pause else UiR.drawable.ic_play),
                            contentDescription = stringResource(if (state.isPlaying) UiR.string.cd_pause else UiR.string.cd_play),
                            tint = NaviColors.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
