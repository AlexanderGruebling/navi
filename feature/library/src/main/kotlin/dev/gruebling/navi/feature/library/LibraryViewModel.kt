package dev.gruebling.navi.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.data.repository.AlbumSort
import dev.gruebling.navi.core.data.repository.ArtistRepository
import dev.gruebling.navi.core.data.repository.FavoritesRepository
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.Album
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.Song
import dev.gruebling.navi.core.playback.PlaybackController
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.gruebling.navi.core.model.EnqueuePosition

enum class LibraryTab { Playlists, Albums, Artists, Favorites }

@Immutable
data class ArtistLetterGroup(val letter: String, val artists: ImmutableList<Artist>)

@Immutable
data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Playlists,
    val albumSort: AlbumSort = AlbumSort.RecentlyAdded,
    val playlists: ImmutableList<Playlist> = persistentListOf(),
    val artistGroups: ImmutableList<ArtistLetterGroup> = persistentListOf(),
    val favoriteSongs: ImmutableList<Song> = persistentListOf(),
    val favoriteAlbums: ImmutableList<Album> = persistentListOf(),
    val favoriteArtists: ImmutableList<Artist> = persistentListOf(),
    val currentSongId: String? = null,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playback: PlaybackController,
) : ViewModel() {
    private val tab = savedState.getStateFlow(KEY_TAB, LibraryTab.Playlists)
    private val albumSort = savedState.getStateFlow(KEY_SORT, AlbumSort.RecentlyAdded)
    private val refreshing = MutableStateFlow(false)
    private val refreshFailed = MutableStateFlow(false)

    /** The whole album library, paged from Room; switching the sort swaps the pager. */
    val albums: Flow<PagingData<Album>> = albumSort.flatMapLatest { albumRepository.pagedAlbums(it) }.cachedIn(viewModelScope)

    private val content = combine(
        playlistRepository.observePlaylists().map { list -> list.sortedBy { it.name.lowercase() }.toImmutableList() },
        artistRepository.observeArtists().map(::groupByLetter),
        favoritesRepository.observeFavorites(),
    ) { playlists, artistGroups, favorites -> Triple(playlists, artistGroups, favorites) }

    val state: StateFlow<LibraryUiState> = combine(
        combine(tab, albumSort, ::Pair),
        content,
        playback.state.map { it.current?.songId }.distinctUntilChanged(),
        refreshing,
        refreshFailed,
    ) { (tab, sort), (playlists, artistGroups, favorites), currentSongId, isRefreshing, failed ->
        LibraryUiState(
            tab = tab,
            albumSort = sort,
            playlists = playlists,
            artistGroups = artistGroups,
            favoriteSongs = favorites.songs.toImmutableList(),
            favoriteAlbums = favorites.albums.toImmutableList(),
            favoriteArtists = favorites.artists.toImmutableList(),
            currentSongId = currentSongId,
            isRefreshing = isRefreshing,
            refreshFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState(tab = tab.value, albumSort = albumSort.value))

    init {
        refresh()
    }

    fun selectTab(tab: LibraryTab) {
        savedState[KEY_TAB] = tab
    }

    fun setAlbumSort(sort: AlbumSort) {
        savedState[KEY_SORT] = sort
    }

    /** Everything the library shows, in parallel; Room updates the screen as each part arrives. */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val results = listOf(
                async { playlistRepository.refreshPlaylists() },
                async { albumRepository.syncLibrary() },
                async { artistRepository.refreshArtists() },
                async { favoritesRepository.refreshFavorites() },
            ).awaitAll()
            refreshFailed.value = results.any { it.isFailure }
            refreshing.value = false
        }
    }

    fun enqueueFavorite(index: Int, position: EnqueuePosition) {
        state.value.favoriteSongs.getOrNull(index)?.let { playback.enqueue(listOf(it), position) }
    }

    fun playFavorites(startIndex: Int = 0, shuffle: Boolean = false) {
        viewModelScope.launch {
            val songs = favoritesRepository.observeFavorites().first().songs
            if (songs.isNotEmpty()) playback.playSongs(songs, startIndex = startIndex, shuffle = shuffle)
        }
    }

    private fun groupByLetter(artists: List<Artist>): ImmutableList<ArtistLetterGroup> =
        artists.groupBy { it.indexLetter ?: it.name.firstOrNull()?.uppercase() ?: "#" }
            .map { (letter, list) -> ArtistLetterGroup(letter, list.toImmutableList()) }
            .toImmutableList()

    private companion object {
        const val KEY_TAB = "tab"
        const val KEY_SORT = "albumSort"
    }
}
