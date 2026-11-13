package dev.gruebling.navi.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.ui.component.DotwaveLogo
import sh.calvin.reorderable.ReorderableItem
import dev.gruebling.navi.core.ui.R as UiR

// The sections of the settings screen. They share one LazyColumn, which dragging playlists needs.

internal fun LazyListScope.accountSection(state: SettingsUiState, onLogout: () -> Unit) {
    item(key = "account-header") { Header(stringResource(R.string.settings_account)) }
    item(key = "account") {
        ListItem(
            headlineContent = { Text(state.username.orEmpty()) },
            supportingContent = { Text(state.serverUrl.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                OutlinedButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_logout)) }
            },
        )
    }
}

internal fun LazyListScope.streamingSection(state: SettingsUiState, onQuality: (QualityDialog) -> Unit, onReplayGain: () -> Unit) {
    item(key = "streaming-header") { Header(stringResource(R.string.settings_streaming)) }
    item(key = "wifi") {
        ChoiceItem(stringResource(R.string.settings_quality_wifi), stringResource(state.wifiQuality.preset.label)) {
            onQuality(QualityDialog.Wifi)
        }
    }
    item(key = "mobile") {
        ChoiceItem(stringResource(R.string.settings_quality_mobile), stringResource(state.mobileQuality.preset.label)) {
            onQuality(QualityDialog.Mobile)
        }
    }
    item(key = "streaming-note") { Note(stringResource(R.string.settings_quality_note)) }
    item(key = "replay-gain") {
        ChoiceItem(stringResource(R.string.settings_replay_gain), stringResource(state.replayGainMode.label), onReplayGain)
    }
    item(key = "replay-gain-note") { Note(stringResource(R.string.settings_replay_gain_note)) }
}

internal fun LazyListScope.downloadsSection(
    state: SettingsUiState,
    onQuality: () -> Unit,
    onMobileData: (Boolean) -> Unit,
    onRemoveAll: () -> Unit,
) {
    item(key = "downloads-header") { Header(stringResource(R.string.settings_downloads)) }
    item(key = "download-quality") {
        ChoiceItem(stringResource(R.string.settings_download_quality), stringResource(state.downloadQuality.preset.label), onQuality)
    }
    item(key = "download-mobile") {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_download_mobile)) },
            trailingContent = { Switch(checked = state.downloadOverMobileData, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = state.downloadOverMobileData, role = Role.Switch, onValueChange = onMobileData),
        )
    }
    item(key = "download-storage") {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_download_storage)) },
            supportingContent = { Text(Formatter.formatShortFileSize(LocalContext.current, state.downloadedBytes)) },
            trailingContent = {
                OutlinedButton(
                    onClick = onRemoveAll,
                    enabled = state.downloadedBytes > 0,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_download_remove_all)) }
            },
        )
    }
    item(key = "downloads-note") { Note(stringResource(R.string.settings_downloads_note)) }
}

/** The playlists on Home, by group, in their Home order; drag or use the TalkBack actions to reorder. */
internal fun LazyListScope.playlistsSection(reorder: PlaylistReorder, onOpen: (Playlist) -> Unit, onMove: (Playlist, Int) -> Unit) {
    item(key = "playlists-header") { Header(stringResource(R.string.settings_playlists)) }
    item(key = "playlists-note") { Note(stringResource(R.string.settings_playlists_note)) }
    reorder.groups.forEach { group ->
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
            ReorderableItem(reorder.lazyState, key = playlistKey(playlist)) { isDragging ->
                PlaylistRow(
                    playlist = playlist,
                    isDragging = isDragging,
                    canMoveUp = index > 0 && list[index - 1].pinned == playlist.pinned,
                    canMoveDown = index < list.lastIndex && list[index + 1].pinned == playlist.pinned,
                    onClick = { onOpen(playlist) },
                    onMove = { onMove(playlist, it) },
                    handle = { DragHandle(reorder, group.group) },
                )
            }
        }
    }
}

internal fun LazyListScope.aboutSection(versionName: String) {
    item(key = "about-header") { Header(stringResource(R.string.settings_about)) }
    item(key = "about") {
        ListItem(
            headlineContent = { Text(stringResource(UiR.string.app_name)) },
            leadingContent = { DotwaveLogo(size = 40.dp) },
            supportingContent = { Text(stringResource(R.string.settings_version, versionName)) },
        )
    }
}

/** A setting that opens a chooser: its name and the current choice. */
@Composable
private fun ChoiceItem(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
