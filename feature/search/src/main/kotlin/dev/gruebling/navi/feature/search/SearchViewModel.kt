package dev.gruebling.navi.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.SearchRepository
import dev.gruebling.navi.core.model.Album
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.model.SearchResults
import dev.gruebling.navi.core.model.Song
import dev.gruebling.navi.core.playback.PlaybackController
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import dev.gruebling.navi.core.model.EnqueuePosition

@Immutable
data class SearchUiState(
    val query: String = "",
    val artists: ImmutableList<Artist> = persistentListOf(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val songs: ImmutableList<Song> = persistentListOf(),
    /** The server hasn't answered for the current query yet (local results may already be shown). */
    val isSearching: Boolean = false,
    val remoteFailed: Boolean = false,
    val currentSongId: String? = null,
) {
    val hasResults: Boolean get() = artists.isNotEmpty() || albums.isNotEmpty() || songs.isNotEmpty()
}

private data class Results(val query: String, val results: SearchResults, val isSearching: Boolean, val failed: Boolean)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: SearchRepository,
    private val playback: PlaybackController,
) : ViewModel() {
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    /**
     * Each keystroke shows the cached matches at once; the server is asked only once typing pauses for
     * [DEBOUNCE_MS]. A new keystroke cancels both (transformLatest).
     */
    private val results = query
        .map { it.trim() }
        .distinctUntilChanged()
        .transformLatest { q ->
            if (q.isEmpty()) {
                emit(Results(q, SearchResults.Empty, isSearching = false, failed = false))
                return@transformLatest
            }
            val local = repository.searchLocal(q)
            emit(Results(q, local, isSearching = true, failed = false))
            delay(DEBOUNCE_MS)
            repository.searchRemote(q).fold(
                onSuccess = { remote -> emit(Results(q, remote, isSearching = false, failed = false)) },
                onFailure = { emit(Results(q, local, isSearching = false, failed = true)) },
            )
        }

    val state: StateFlow<SearchUiState> = combine(
        query,
        results,
        playback.state.map { it.current?.songId }.distinctUntilChanged(),
    ) { q, r, currentSongId ->
        // While the user types, keep the last results visible instead of flashing an empty list.
        SearchUiState(
            query = q,
            artists = r.results.artists.toImmutableList(),
            albums = r.results.albums.toImmutableList(),
            songs = r.results.songs.toImmutableList(),
            isSearching = r.isSearching || q.trim() != r.query,
            remoteFailed = r.failed,
            currentSongId = currentSongId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(query = query.value))

    fun onQueryChange(value: String) {
        savedState[KEY_QUERY] = value
    }

    /** Plays the song results as a queue, starting at the tapped one. */
    fun enqueueSong(index: Int, position: EnqueuePosition) {
        state.value.songs.getOrNull(index)?.let { playback.enqueue(listOf(it), position) }
    }

    fun playSong(index: Int) {
        val songs = state.value.songs
        if (index in songs.indices) playback.playSongs(songs, startIndex = index)
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val DEBOUNCE_MS = 300L
    }
}

