package dev.gruebling.navi.feature.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.gruebling.navi.core.ui.R as UiR

/**
 * Edit mode of an own playlist: rename, drag songs into a new order, remove songs, or delete the playlist. Nothing is
 * sent until Save; back or Cancel drops the changes.
 */
@Composable
internal fun PlaylistEditor(
    state: PlaylistEditState,
    onRename: (String) -> Unit,
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    onRemove: (key: String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.key, to.key)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(painterResource(UiR.drawable.ic_close), contentDescription = stringResource(R.string.playlist_edit_cancel))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MonoLabel(stringResource(R.string.playlist_edit))
            }
            TextButton(onClick = onSave, enabled = !state.isSaving && state.name.isNotBlank()) {
                Text(stringResource(R.string.playlist_edit_save))
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onRename,
                    label = { Text(stringResource(R.string.playlist_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
                )
            }
            if (state.tracks.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.playlist_edit_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = NaviColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
                    )
                }
            }
            items(state.tracks, key = { it.key }) { track ->
                ReorderableItem(reorderState, key = track.key) { isDragging ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(if (isDragging) NaviColors.Surface2 else Color.Transparent)
                            .padding(start = ScreenPadding - 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = { onRemove(track.key) }) {
                            Icon(
                                painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.playlist_edit_remove_song, track.title),
                                tint = NaviColors.TextSecondary,
                            )
                        }
                        CoverArt(track.coverArtId, 40.dp, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDragging) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            track.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = NaviColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Box(
                            Modifier.size(48.dp).draggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                onDragStopped = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(UiR.drawable.ic_drag_handle),
                                contentDescription = stringResource(R.string.playlist_edit_reorder),
                                tint = NaviColors.TextSecondary,
                            )
                        }
                    }
                }
            }
            item(key = "delete") {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = ScreenPadding - 12.dp, vertical = 16.dp),
                ) {
                    Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.playlist_edit_delete))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.playlist_edit_delete_title, state.name)) },
            text = { Text(stringResource(R.string.playlist_edit_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.playlist_edit_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.playlist_edit_cancel)) } },
        )
    }
}
