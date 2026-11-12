package dev.gruebling.navi.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.ReplayGainMode
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.ui.component.DotwaveLogo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun SettingsScreen(
    versionName: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(state, versionName, onBack, viewModel)
}

private enum class QualityDialog { Wifi, Mobile, Download }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(state: SettingsUiState, versionName: String, onBack: () -> Unit, viewModel: SettingsViewModel) {
    var qualityDialog by rememberSaveable { mutableStateOf<QualityDialog?>(null) }
    var replayGainDialog by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveDownloads by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var playlistDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    // While a playlist is dragged (and until the database has the new order) the list shows a local order.
    var localGroups by remember { mutableStateOf<List<PlaylistGroupUi>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.playlistGroups) { if (!dragging) localGroups = null }
    val groups = localGroups ?: state.playlistGroups
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = localGroups ?: state.playlistGroups
        val moved = current.moveWithinGroup(from.key, to.key) ?: return@rememberReorderableLazyListState
        localGroups = moved
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(UiR.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            // Account
            item(key = "account-header") { Header(stringResource(R.string.settings_account)) }
            item(key = "account") {
                ListItem(
                    headlineContent = { Text(state.username.orEmpty()) },
                    supportingContent = { Text(state.serverUrl.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        OutlinedButton(
                            onClick = { confirmLogout = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.settings_logout)) }
                    },
                )
            }

            // Streaming
            item(key = "streaming-header") { Header(stringResource(R.string.settings_streaming)) }
            item(key = "wifi") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_quality_wifi)) },
                    supportingContent = { Text(stringResource(state.wifiQuality.preset.label)) },
                    modifier = Modifier.clickable { qualityDialog = QualityDialog.Wifi },
                )
            }
            item(key = "mobile") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_quality_mobile)) },
                    supportingContent = { Text(stringResource(state.mobileQuality.preset.label)) },
                    modifier = Modifier.clickable { qualityDialog = QualityDialog.Mobile },
                )
            }
            item(key = "streaming-note") { Note(stringResource(R.string.settings_quality_note)) }
            item(key = "replay-gain") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_replay_gain)) },
                    supportingContent = { Text(stringResource(state.replayGainMode.label)) },
                    modifier = Modifier.clickable { replayGainDialog = true },
                )
            }
            item(key = "replay-gain-note") { Note(stringResource(R.string.settings_replay_gain_note)) }

            // Downloads
            item(key = "downloads-header") { Header(stringResource(R.string.settings_downloads)) }
            item(key = "download-quality") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_download_quality)) },
                    supportingContent = { Text(stringResource(state.downloadQuality.preset.label)) },
                    modifier = Modifier.clickable { qualityDialog = QualityDialog.Download },
                )
            }
            item(key = "download-mobile") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_download_mobile)) },
                    trailingContent = { Switch(checked = state.downloadOverMobileData, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = state.downloadOverMobileData,
                        role = Role.Switch,
                        onValueChange = viewModel::setDownloadOverMobileData,
                    ),
                )
            }
            item(key = "download-storage") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_download_storage)) },
                    supportingContent = { Text(Formatter.formatShortFileSize(context, state.downloadedBytes)) },
                    trailingContent = {
                        OutlinedButton(
                            onClick = { confirmRemoveDownloads = true },
                            enabled = state.downloadedBytes > 0,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.settings_download_remove_all)) }
                    },
                )
            }
            item(key = "downloads-note") { Note(stringResource(R.string.settings_downloads_note)) }

            // Playlists on Home
            item(key = "playlists-header") { Header(stringResource(R.string.settings_playlists)) }
            item(key = "playlists-note") { Note(stringResource(R.string.settings_playlists_note)) }
            groups.forEach { group ->
                item(key = "group-${group.group}") {
                    Text(
                        stringResource(group.group.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(group.playlists, key = { _, p -> playlistKey(p) }) { index, playlist ->
                    val list = group.playlists
                    ReorderableItem(reorderState, key = playlistKey(playlist)) { isDragging ->
                        PlaylistRow(
                            playlist = playlist,
                            isDragging = isDragging,
                            canMoveUp = index > 0 && list[index - 1].pinned == playlist.pinned,
                            canMoveDown = index < list.lastIndex && list[index + 1].pinned == playlist.pinned,
                            onClick = { playlistDialog = playlist.id.value },
                            onMove = { viewModel.move(playlist, it) },
                            handle = {
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
                                                val ordered = localGroups?.firstOrNull { it.group == group.group }?.playlists
                                                if (ordered != null && ordered != state.playlistGroups.firstOrNull { it.group == group.group }?.playlists) {
                                                    viewModel.reorder(ordered)
                                                } else {
                                                    localGroups = null
                                                }
                                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(UiR.drawable.ic_drag_handle),
                                        contentDescription = stringResource(R.string.cd_reorder),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // About
            item(key = "about-header") { Header(stringResource(R.string.settings_about)) }
            item(key = "about") {
                ListItem(
                    headlineContent = { Text(stringResource(UiR.string.app_name)) },
                    leadingContent = { DotwaveLogo(size = 40.dp) },
                    supportingContent = { Text(stringResource(R.string.settings_version, versionName)) },
                )
            }
        }
    }

    qualityDialog?.let { which ->
        QualityChooser(
            title = stringResource(
                when (which) {
                    QualityDialog.Wifi -> R.string.settings_quality_wifi
                    QualityDialog.Mobile -> R.string.settings_quality_mobile
                    QualityDialog.Download -> R.string.settings_download_quality
                },
            ),
            selected = when (which) {
                QualityDialog.Wifi -> state.wifiQuality.preset
                QualityDialog.Mobile -> state.mobileQuality.preset
                QualityDialog.Download -> state.downloadQuality.preset
            },
            onSelect = {
                when (which) {
                    QualityDialog.Wifi -> viewModel.setWifiQuality(it)
                    QualityDialog.Mobile -> viewModel.setMobileQuality(it)
                    QualityDialog.Download -> viewModel.setDownloadQuality(it)
                }
                qualityDialog = null
            },
            onDismiss = { qualityDialog = null },
        )
    }

    if (confirmRemoveDownloads) {
        AlertDialog(
            onDismissRequest = { confirmRemoveDownloads = false },
            title = { Text(stringResource(R.string.settings_download_remove_all_title)) },
            text = { Text(stringResource(R.string.settings_download_remove_all_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveDownloads = false
                    viewModel.removeAllDownloads()
                }) { Text(stringResource(R.string.settings_download_remove_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveDownloads = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    if (replayGainDialog) {
        AlertDialog(
            onDismissRequest = { replayGainDialog = false },
            title = { Text(stringResource(R.string.settings_replay_gain)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    ReplayGainMode.entries.forEach { mode ->
                        RadioRow(stringResource(mode.label), mode == state.replayGainMode) {
                            viewModel.setReplayGainMode(mode)
                            replayGainDialog = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { replayGainDialog = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    playlistDialog?.let { id ->
        val playlist = state.playlistGroups.flatMap { it.playlists }.firstOrNull { it.id.value == id }
        if (playlist == null) {
            playlistDialog = null
        } else {
            PlaylistOptions(
                playlist = playlist,
                onGroup = { viewModel.setGroup(playlist, it) },
                onPinned = { viewModel.setPinned(playlist, it) },
                onDismiss = { playlistDialog = null },
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.settings_logout_title)) },
            text = { Text(stringResource(R.string.settings_logout_text)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmLogout = false; viewModel.logout() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_logout)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

private fun playlistKey(playlist: Playlist) = "pl-${playlist.id.value}"

/**
 * Moves the playlist with key [fromKey] to the place of [toKey], but only within one group and among playlists of
 * the same kind (pinned ones always come first on Home). null when that move isn't allowed.
 */
private fun List<PlaylistGroupUi>.moveWithinGroup(fromKey: Any, toKey: Any): List<PlaylistGroupUi>? {
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
private fun PlaylistRow(
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

private val ReplayGainMode.label: Int
    get() = when (this) {
        ReplayGainMode.Off -> R.string.settings_replay_gain_off
        ReplayGainMode.Track -> R.string.settings_replay_gain_track
        ReplayGainMode.Album -> R.string.settings_replay_gain_album
        ReplayGainMode.Auto -> R.string.settings_replay_gain_auto
    }

@Composable
private fun QualityChooser(title: String, selected: QualityPreset, onSelect: (QualityPreset) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                QualityPreset.entries.forEach { preset ->
                    RadioRow(stringResource(preset.label), preset == selected) { onSelect(preset) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun PlaylistOptions(
    playlist: Playlist,
    onGroup: (PlaylistGroup?) -> Unit,
    onPinned: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(stringResource(R.string.settings_group), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    RadioRow(stringResource(R.string.settings_group_auto), playlist.groupOverride == null) { onGroup(null) }
                    PlaylistGroup.entries.forEach { group ->
                        RadioRow(stringResource(group.label), playlist.groupOverride == group) { onGroup(group) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = playlist.pinned, role = Role.Switch, onClick = { onPinned(!playlist.pinned) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_pin))
                        Text(
                            stringResource(R.string.settings_pin_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = playlist.pinned, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private val PlaylistGroup.label: Int
    get() = when (this) {
        PlaylistGroup.Charts -> R.string.settings_group_charts
        PlaylistGroup.ForYou -> R.string.settings_group_foryou
        PlaylistGroup.Mine -> R.string.settings_group_mine
    }
