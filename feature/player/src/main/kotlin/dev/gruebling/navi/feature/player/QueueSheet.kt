package dev.gruebling.navi.feature.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.playback.model.PlayerState
import dev.gruebling.navi.core.ui.component.TrackRow
import dev.gruebling.navi.core.ui.R as UiR

/** The queue in play order, scrolled so the current track is on top. Tap to jump, × to remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    state: PlayerState,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val order = state.playOrder.ifEmpty { state.queue.indices.toList() }
    val start = order.indexOf(state.currentIndex).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.player_queue),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState) {
            itemsIndexed(order, key = { _, queueIndex -> "${state.queue.getOrNull(queueIndex)?.songId}#$queueIndex" }) { _, queueIndex ->
                val entry = state.queue.getOrNull(queueIndex) ?: return@itemsIndexed
                val isCurrent = queueIndex == state.currentIndex
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackRow(
                        title = entry.title,
                        subtitle = entry.artist,
                        durationMs = entry.durationMs,
                        onClick = { onSkipTo(queueIndex) },
                        coverArtId = entry.coverArt,
                        isCurrent = isCurrent,
                        modifier = Modifier.weight(1f),
                    )
                    if (!isCurrent) {
                        IconButton(onClick = { onRemove(queueIndex) }) {
                            Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.cd_remove_from_queue))
                        }
                    }
                }
            }
        }
    }
}
