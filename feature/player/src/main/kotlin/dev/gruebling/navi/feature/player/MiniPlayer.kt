package dev.gruebling.navi.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.ui.image.CoverArt
import kotlinx.coroutines.delay
import dev.gruebling.navi.core.ui.R as UiR

/** Persistent mini player above the bottom navigation. Hidden while the queue is empty. */
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

    AnimatedVisibility(visible = current != null, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Column(Modifier.clickable(onClick = onExpand)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(current?.coverArt, 40.dp, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            current?.title.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            current?.artist ?: stringResource(R.string.player_unknown_artist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = viewModel::togglePlayPause) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isBuffering) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            }
                            Icon(
                                painterResource(if (state.isPlaying) UiR.drawable.ic_pause else UiR.drawable.ic_play),
                                contentDescription = stringResource(if (state.isPlaying) UiR.string.cd_pause else UiR.string.cd_play),
                            )
                        }
                    }
                    IconButton(onClick = viewModel::next, enabled = state.hasNext) {
                        Icon(painterResource(UiR.drawable.ic_skip_next), contentDescription = stringResource(UiR.string.cd_next))
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.floatValue },
                    modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp),
                    strokeCap = StrokeCap.Butt,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}
