package dev.gruebling.navi.feature.addtoplaylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.Playlist
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The outcome of adding songs, shown as a toast. */
internal data class AddResult(val playlistName: String, val success: Boolean)

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(private val playlists: PlaylistRepository) : ViewModel() {
    /** Playlists songs can be added to, by name. */
    internal val editable: StateFlow<ImmutableList<Playlist>> = playlists.observePlaylists()
        .map { list -> list.filter { it.isEditable }.sortedBy { it.name.lowercase() }.toImmutableList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    private val _results = Channel<AddResult>(Channel.BUFFERED)
    internal val results: Flow<AddResult> = _results.receiveAsFlow()

    /** Runs in the ViewModel, so it finishes after the sheet has closed. */
    internal fun addTo(playlist: Playlist, songIds: List<String>) {
        viewModelScope.launch {
            val ok = playlists.addSongs(playlist.id, songIds).isSuccess
            _results.send(AddResult(playlist.name, ok))
        }
    }

    internal fun createWith(name: String, songIds: List<String>) {
        viewModelScope.launch {
            val ok = playlists.createPlaylist(name.trim(), songIds).isSuccess
            _results.send(AddResult(name.trim(), ok))
        }
    }
}
