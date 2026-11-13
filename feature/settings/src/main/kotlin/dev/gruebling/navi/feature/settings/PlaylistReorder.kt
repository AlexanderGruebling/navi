package dev.gruebling.navi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistGroup
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.gruebling.navi.core.ui.R as UiR

/**
 * Dragging playlists into a new Home order. While a playlist is dragged, and until the database has the new order,
 * the list shows a local order instead of the saved one.
 */
@Stable
internal class PlaylistReorder(private val haptics: HapticFeedback) {
    /** The order from the database; set on every composition (like rememberUpdatedState). */
    internal var saved: List<PlaylistGroupUi> by mutableStateOf(emptyList())
    internal var onReorder: (List<Playlist>) -> Unit = {}
    internal lateinit var lazyState: ReorderableLazyListState

    private var local by mutableStateOf<List<PlaylistGroupUi>?>(null)
    private var dragging = false

    val groups: List<PlaylistGroupUi> get() = local ?: saved

    internal fun move(fromKey: Any, toKey: Any) {
        local = groups.moveWithinGroup(fromKey, toKey) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    internal fun onDragStarted() {
        dragging = true
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    internal fun onDragStopped(group: PlaylistGroup) {
        dragging = false
        val ordered = local?.firstOrNull { it.group == group }?.playlists
        if (ordered != null && ordered != saved.firstOrNull { it.group == group }?.playlists) {
            onReorder(ordered)
        } else {
            local = null
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }

    /** The database has a new order: show it, unless a drag is still going on. */
    internal fun onSavedChanged() {
        if (!dragging) local = null
    }
}

@Composable
internal fun rememberPlaylistReorder(
    saved: List<PlaylistGroupUi>,
    listState: LazyListState,
    onReorder: (List<Playlist>) -> Unit,
): PlaylistReorder {
    val haptics = LocalHapticFeedback.current
    val currentOnReorder by rememberUpdatedState(onReorder)
    val reorder = remember { PlaylistReorder(haptics).apply { this.onReorder = { currentOnReorder(it) } } }
    reorder.saved = saved
    reorder.lazyState = rememberReorderableLazyListState(listState) { from, to -> reorder.move(from.key, to.key) }
    LaunchedEffect(saved) { reorder.onSavedChanged() }
    return reorder
}

@Composable
internal fun ReorderableCollectionItemScope.DragHandle(reorder: PlaylistReorder, group: PlaylistGroup) {
    Box(
        Modifier
            .size(48.dp)
            .draggableHandle(onDragStarted = { reorder.onDragStarted() }, onDragStopped = { reorder.onDragStopped(group) }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(UiR.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.cd_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun playlistKey(playlist: Playlist) = "pl-${playlist.id.value}"

/**
 * Moves the playlist with key [fromKey] to the place of [toKey], but only within one group and among playlists of
 * the same kind (pinned ones always come first on Home). null when that move isn't allowed.
 */
internal fun List<PlaylistGroupUi>.moveWithinGroup(fromKey: Any, toKey: Any): List<PlaylistGroupUi>? {
    val groupIndex = indexOfFirst { g -> g.playlists.any { playlistKey(it) == fromKey } }
    if (groupIndex < 0) return null
    val list = this[groupIndex].playlists
    val from = list.indexOfFirst { playlistKey(it) == fromKey }
    val to = list.indexOfFirst { playlistKey(it) == toKey }
    if (to < 0 || list[to].pinned != list[from].pinned) return null
    val moved = list.toMutableList().apply { add(to, removeAt(from)) }.toImmutableList()
    return toMutableList().apply { this[groupIndex] = this[groupIndex].copy(playlists = moved) }
}

/** One playlist in the Home order: name, how it's grouped, and a drag handle. Up/down are TalkBack actions. */
@Composable
internal fun PlaylistRow(
    playlist: Playlist,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    handle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val upLabel = stringResource(R.string.cd_move_up)
    val downLabel = stringResource(R.string.cd_move_down)
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlist.pinned) {
                    Icon(
                        painterResource(UiR.drawable.ic_push_pin),
                        contentDescription = stringResource(R.string.settings_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        supportingContent = {
            Text(
                if (playlist.groupOverride == null) stringResource(R.string.settings_group_auto_is, stringResource(playlist.group.label))
                else stringResource(R.string.settings_group_manual),
            )
        },
        trailingContent = handle,
        colors = if (isDragging) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        else ListItemDefaults.colors(),
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(upLabel) { onMove(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction(downLabel) { onMove(1); true })
                }
            },
    )
}

internal val PlaylistGroup.label: Int
    get() = when (this) {
        PlaylistGroup.Charts -> R.string.settings_group_charts
        PlaylistGroup.ForYou -> R.string.settings_group_foryou
        PlaylistGroup.Mine -> R.string.settings_group_mine
    }
