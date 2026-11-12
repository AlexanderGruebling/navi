package dev.gruebling.navi.feature.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.data.repository.ArtistRepository
import dev.gruebling.navi.core.data.repository.FavoritesRepository
import dev.gruebling.navi.core.model.Album
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.model.ArtistId
import dev.gruebling.navi.core.playback.PlaybackController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Immutable
data class ArtistUiState(
    val artist: Artist? = null,
    val albums: ImmutableList<Album> = persistentListOf(),
    /** Most popular songs (Last.fm ranking via the server); empty when the server has none. */
    val topSongs: ImmutableList<TrackUi> = persistentListOf(),
    val similarArtists: ImmutableList<Artist> = persistentListOf(),
    val biography: String? = null,
    val lastFmUrl: String? = null,
    val currentSongId: String? = null,
    val isLoading: Boolean = true,
    val refreshFailed: Boolean = false,
    /** Collecting the songs of all albums for "shuffle all". */
    val isPreparingPlayback: Boolean = false,
)

@HiltViewModel(assistedFactory = ArtistViewModel.Factory::class)
class ArtistViewModel @AssistedInject constructor(
    @Assisted artistId: String,
    private val artists: ArtistRepository,
    private val albums: AlbumRepository,
    private val favorites: FavoritesRepository,
    private val playback: PlaybackController,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(artistId: String): ArtistViewModel
    }

    private val id = ArtistId(artistId)
    private val refreshFailed = MutableStateFlow(false)
    private val preparing = MutableStateFlow(false)
    private val detail = artists.observeArtist(id)
    private val info = artists.observeArtistInfo(id)
    private val status = combine(refreshFailed, preparing, ::Pair)

    val state: StateFlow<ArtistUiState> = combine(
        detail,
        info,
        playback.state.map { it.current?.songId }.distinctUntilChanged(),
        status,
    ) { d, extras, currentSongId, (failed, isPreparing) ->
        ArtistUiState(
            artist = d?.artist,
            albums = d?.albums.orEmpty().toImmutableList(),
            topSongs = extras.topSongs.mapIndexed { index, song ->
                TrackUi(
                    key = "$index:${song.id.value}",
                    songId = song.id.value,
                    title = song.title,
                    subtitle = listOfNotNull(song.album, song.year?.toString()).joinToString(" · ").ifEmpty { null },
                    durationMs = song.durationSec * 1000L,
                    coverArtId = song.coverArt?.value,
                    trackNumber = null,
                    starred = song.starred,
                )
            }.toImmutableList(),
            similarArtists = extras.similarArtists.toImmutableList(),
            biography = extras.biography,
            lastFmUrl = extras.lastFmUrl,
            currentSongId = currentSongId,
            isLoading = d == null || (d.albums.isEmpty() && d.artist.albumCount > 0),
            refreshFailed = failed,
            isPreparingPlayback = isPreparing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState())

    init {
        viewModelScope.launch {
            refreshFailed.value = artists.refreshArtist(id).isFailure
            // getTopSongs needs the name. A failure here is not "offline": many servers have no Last.fm set up.
            detail.first()?.artist?.name?.let { artists.refreshArtistInfo(id, it) }
        }
    }

    fun toggleStar() {
        val artist = state.value.artist ?: return
        viewModelScope.launch { favorites.setStarred(artist.id, !artist.starred) }
    }

    /** Plays the top songs, starting at [index]. */
    fun playTopSong(index: Int) {
        viewModelScope.launch {
            val songs = info.first().topSongs
            if (index in songs.indices) playback.playSongs(songs, startIndex = index)
        }
    }

    /** Shuffles every song of the artist. Albums whose tracks aren't cached yet are fetched first (4 at a time). */
    fun shuffleAll() {
        if (preparing.value) return
        viewModelScope.launch {
            preparing.value = true
            try {
                val albumList = detail.first()?.albums.orEmpty()
                val limit = Semaphore(4)
                val songs = albumList.map { album ->
                    async {
                        limit.withPermit {
                            val cached = albums.observeAlbum(album.id).first()?.songs.orEmpty()
                            if (cached.size >= album.songCount && cached.isNotEmpty()) {
                                cached
                            } else {
                                albums.refreshAlbum(album.id)
                                albums.observeAlbum(album.id).first()?.songs.orEmpty()
                            }
                        }
                    }
                }.awaitAll().flatten()
                if (songs.isNotEmpty()) playback.playSongs(songs, shuffle = true)
            } finally {
                preparing.value = false
            }
        }
    }
}
