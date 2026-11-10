package dev.gruebling.navi.feature.settings

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
import dev.gruebling.navi.core.model.PlaylistGroup
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

private enum class QualityDialog { Wifi, Mobile }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(state: SettingsUiState, versionName: String, onBack: () -> Unit, viewModel: SettingsViewModel) {
    var qualityDialog by rememberSaveable { mutableStateOf<QualityDialog?>(null) }
    var playlistDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
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

            // Playlists on Home
            item(key = "playlists-header") { Header(stringResource(R.string.settings_playlists)) }
            item(key = "playlists-note") { Note(stringResource(R.string.settings_playlists_note)) }
            state.playlistGroups.forEach { group ->
                item(key = "group-${group.group}") {
                    Text(
                        stringResource(group.group.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(group.playlists, key = { _, p -> "pl-${p.id.value}" }) { index, playlist ->
                    val list = group.playlists
                    PlaylistRow(
                        playlist = playlist,
                        canMoveUp = index > 0 && list[index - 1].pinned == playlist.pinned,
                        canMoveDown = index < list.lastIndex && list[index + 1].pinned == playlist.pinned,
                        onClick = { playlistDialog = playlist.id.value },
                        onMove = { viewModel.move(playlist, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // About
            item(key = "about-header") { Header(stringResource(R.string.settings_about)) }
            item(key = "about") {
                ListItem(
                    headlineContent = { Text("Navi") },
                    supportingContent = { Text(stringResource(R.string.settings_version, versionName)) },
                )
            }
        }
    }

    qualityDialog?.let { which ->
        QualityChooser(
            title = stringResource(if (which == QualityDialog.Wifi) R.string.settings_quality_wifi else R.string.settings_quality_mobile),
            selected = if (which == QualityDialog.Wifi) state.wifiQuality.preset else state.mobileQuality.preset,
            onSelect = {
                if (which == QualityDialog.Wifi) viewModel.setWifiQuality(it) else viewModel.setMobileQuality(it)
                qualityDialog = null
            },
            onDismiss = { qualityDialog = null },
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

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        trailingContent = {
            Row {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(painterResource(UiR.drawable.ic_arrow_upward), stringResource(R.string.cd_move_up))
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(painterResource(UiR.drawable.ic_arrow_downward), stringResource(R.string.cd_move_down))
                }
            }
        },
        modifier = modifier.clickable(onClick = onClick),
    )
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
