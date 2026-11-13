package dev.gruebling.navi.feature.addtoplaylist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.R as UiR

/**
 * "Add to playlist": while [songIds] is set, a sheet to pick one of the user's editable playlists or create a new
 * one. Stays composed (also while closed) so the result toast still shows after the sheet is gone.
 */
@Composable
fun AddToPlaylist(songIds: List<String>?, onDismiss: () -> Unit, viewModel: AddToPlaylistViewModel = hiltViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.results.collect { r ->
            val text = context.getString(if (r.success) R.string.add_to_playlist_added else R.string.add_to_playlist_failed, r.playlistName)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    if (songIds == null) return
    val playlists by viewModel.editable.collectAsStateWithLifecycle()
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        NewPlaylistDialog(
            onCreate = { name ->
                viewModel.createWith(name, songIds)
                naming = false
                onDismiss()
            },
            onDismiss = { naming = false },
        )
    } else {
        PlaylistPicker(
            playlists = playlists,
            onNew = { naming = true },
            onPick = { playlist ->
                viewModel.addTo(playlist, songIds)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPicker(playlists: List<Playlist>, onNew: () -> Unit, onPick: (Playlist) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.add_to_playlist_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 12.dp),
        )
        HorizontalDivider(color = NaviColors.Outline)
        LazyColumn {
            item(key = "new") {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onNew).padding(horizontal = ScreenPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(painterResource(UiR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.add_to_playlist_new), style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(playlists, key = { it.id.value }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clickable { onPick(playlist) }.padding(horizontal = ScreenPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CoverArt(playlist.coverArt?.value, 40.dp, Modifier.size(40.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MonoLabel(pluralStringResource(UiR.plurals.song_count, playlist.songCount, playlist.songCount))
                    }
                }
            }
            if (playlists.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.add_to_playlist_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist_new).trimEnd('…')) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.add_to_playlist_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.add_to_playlist_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_to_playlist_cancel)) } },
    )
}
