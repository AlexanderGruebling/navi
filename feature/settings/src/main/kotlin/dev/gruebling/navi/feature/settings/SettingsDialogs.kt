package dev.gruebling.navi.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.ReplayGainMode

/** The three quality settings, each with its own chooser. */
internal enum class QualityDialog(@StringRes val title: Int) {
    Wifi(R.string.settings_quality_wifi),
    Mobile(R.string.settings_quality_mobile),
    Download(R.string.settings_download_quality),
    ;

    fun selected(state: SettingsUiState): QualityPreset = when (this) {
        Wifi -> state.wifiQuality.preset
        Mobile -> state.mobileQuality.preset
        Download -> state.downloadQuality.preset
    }
}

internal val ReplayGainMode.label: Int
    get() = when (this) {
        ReplayGainMode.Off -> R.string.settings_replay_gain_off
        ReplayGainMode.Track -> R.string.settings_replay_gain_track
        ReplayGainMode.Album -> R.string.settings_replay_gain_album
        ReplayGainMode.Auto -> R.string.settings_replay_gain_auto
    }

@Composable
internal fun QualityChooser(title: String, selected: QualityPreset, onSelect: (QualityPreset) -> Unit, onDismiss: () -> Unit) {
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
        confirmButton = { CancelButton(onDismiss) },
    )
}

@Composable
internal fun ReplayGainChooser(selected: ReplayGainMode, onSelect: (ReplayGainMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_replay_gain)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ReplayGainMode.entries.forEach { mode ->
                    RadioRow(stringResource(mode.label), mode == selected) { onSelect(mode) }
                }
            }
        },
        confirmButton = { CancelButton(onDismiss) },
    )
}

@Composable
internal fun ConfirmRemoveDownloads(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_download_remove_all_title)) },
        text = { Text(stringResource(R.string.settings_download_remove_all_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_download_remove_all)) } },
        dismissButton = { CancelButton(onDismiss) },
    )
}

@Composable
internal fun ConfirmLogout(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_logout_title)) },
        text = { Text(stringResource(R.string.settings_logout_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.settings_logout)) }
        },
        dismissButton = { CancelButton(onDismiss) },
    )
}

/** Group and pin of one playlist on Home. */
@Composable
internal fun PlaylistOptions(
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
private fun CancelButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.settings_cancel)) }
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

/** Edits the new-releases feed URL; an empty field turns the feature off. */
@Composable
internal fun ReleaseFeedDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_release_feed)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_release_feed_hint)) },
                supportingText = { Text(stringResource(R.string.settings_release_feed_empty)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(dev.gruebling.navi.core.ui.R.string.action_cancel)) } },
    )
}
