package dev.gruebling.navi.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.playback.model.PlayerState
import dev.gruebling.navi.core.playback.model.QueueEntry
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.TrackRow
import dev.gruebling.navi.core.ui.component.TrackRowInset
import dev.gruebling.navi.core.ui.theme.NaviColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.gruebling.navi.core.ui.R as UiR

/** One row of the queue sheet. [key] stays the same while the song moves (n-th copy of a song id). */
@Immutable
private data class QueueRow(val key: String, val queueIndex: Int, val entry: QueueEntry)

private fun PlayerState.rows(): List<QueueRow> {
    val copies = HashMap<String, Int>()
    val keys = queue.map { entry -> "${entry.songId}#${copies.merge(entry.songId, 1, Int::plus)}" }
    val order = playOrder.ifEmpty { queue.indices.toList() }
    return order.mapNotNull { i -> queue.getOrNull(i)?.let { QueueRow(keys[i], i, it) } }
}

/**
 * The queue in play order, scrolled so the current track is on top. Tap to jump, drag the handle to reorder, swipe
 * sideways to remove. Reordering needs shuffle off: then the rows are in queue order, so a row's position is its
 * queue index. "Save as playlist" stores the queue on the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    state: PlayerState,
    isSaving: Boolean,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val serverRows = remember(state.queue, state.playOrder) { state.rows() }
    // While a drag is going on (and until the player has applied the move) the sheet shows its own order.
    var localRows by remember { mutableStateOf<List<QueueRow>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(serverRows) { if (!dragging) localRows = null }
    val rows = localRows ?: serverRows

    val canReorder = !state.shuffle
    val start = rows.indexOfFirst { it.queueIndex == state.currentIndex }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localRows = (localRows ?: serverRows).toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding - 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.player_queue), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (isSaving) {
                CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                MonoLabel(
                    stringResource(R.string.player_queue_save),
                    color = NaviColors.White,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = rows.isNotEmpty()) { showSaveDialog = true }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
        MonoLabel(
            if (canReorder) pluralStringResource(R.plurals.player_queue_count, rows.size, rows.size)
            else stringResource(R.string.player_queue_shuffle_hint),
            modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = 8.dp),
        )
        LazyColumn(state = listState) {
            itemsIndexed(rows, key = { _, row -> row.key }) { position, row ->
                ReorderableItem(reorderState, key = row.key, enabled = canReorder) { isDragging ->
                    QueueItem(
                        row = row,
                        isCurrent = row.queueIndex == state.currentIndex,
                        isDragging = isDragging,
                        canMoveUp = canReorder && position > 0,
                        canMoveDown = canReorder && position < rows.lastIndex,
                        onClick = { onSkipTo(row.queueIndex) },
                        onRemove = { onRemove(row.queueIndex) },
                        onMoveBy = { delta -> onMove(row.queueIndex, row.queueIndex + delta) },
                        handle = {
                            if (canReorder) {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .draggableHandle(
                                            onDragStarted = {
                                                dragging = true
                                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            },
                                            onDragStopped = {
                                                dragging = false
                                                val moved = localRows?.indexOfFirst { it.key == row.key } ?: -1
                                                // Without shuffle, a row's position is its queue index.
                                                if (moved >= 0 && moved != row.queueIndex) onMove(row.queueIndex, moved)
                                                else localRows = null
                                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(UiR.drawable.ic_drag_handle),
                                        contentDescription = stringResource(R.string.cd_reorder),
                                        tint = NaviColors.TextSecondary,
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(48.dp))
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveQueueDialog(
            onSave = { name ->
                showSaveDialog = false
                onSave(name)
            },
            onDismiss = { showSaveDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItem(
    row: QueueRow,
    isCurrent: Boolean,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveBy: (Int) -> Unit,
    handle: @Composable () -> Unit,
) {
    val removeLabel = stringResource(R.string.cd_remove_from_queue)
    val upLabel = stringResource(R.string.cd_move_up)
    val downLabel = stringResource(R.string.cd_move_down)
    val swipeState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipeState,
        // The playing song stays: removing it would skip to the next one.
        gesturesEnabled = !isCurrent && !isDragging,
        onDismiss = { onRemove() },
        backgroundContent = { RemoveBackground(swipeState.dismissDirection) },
        modifier = Modifier.semantics {
            customActions = buildList {
                if (!isCurrent) add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                if (canMoveUp) add(CustomAccessibilityAction(upLabel) { onMoveBy(-1); true })
                if (canMoveDown) add(CustomAccessibilityAction(downLabel) { onMoveBy(1); true })
            }
        },
    ) {
        Row(
            Modifier
                .background(if (isDragging) NaviColors.Surface2 else MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(start = TrackRowInset, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackRow(
                title = row.entry.title,
                subtitle = row.entry.artist,
                durationMs = row.entry.durationMs,
                onClick = onClick,
                coverArtId = row.entry.coverArt,
                isCurrent = isCurrent,
                modifier = Modifier.weight(1f),
            )
            handle()
        }
    }
}

/** Red strip with a × on the side the row is swiped away from. */
@Composable
private fun RemoveBackground(direction: SwipeToDismissBoxValue) {
    Row(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(horizontal = ScreenPadding),
        horizontalArrangement = if (direction == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(UiR.drawable.ic_close), contentDescription = null, tint = NaviColors.White)
    }
}

@Composable
private fun SaveQueueDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    val defaultName = stringResource(R.string.player_queue_default_name, today)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_queue_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.player_queue_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.player_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_cancel)) } },
    )
}
