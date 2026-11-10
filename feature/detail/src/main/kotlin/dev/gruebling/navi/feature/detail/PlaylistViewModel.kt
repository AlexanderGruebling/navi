package dev.gruebling.navi.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.PlaylistDetail
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.model.Song
import dev.gruebling.navi.core.playback.PlaybackController
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel @AssistedInject constructor(
    @Assisted playlistId: String,
    private val repository: PlaylistRepository,
    private val playback: PlaybackController,
    private val clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playlistId: String): PlaylistViewModel
    }

    private val id = PlaylistId(playlistId)
    private val refreshFailed = MutableStateFlow(false)

    /**
     * The track list on screen. For smart playlists it is frozen once the user interacted (scrolled or played), so a
     * re-evaluated server mix doesn't reshuffle the list under their finger; the newer mix waits behind a chip.
     */
    private val shown = MutableStateFlow<List<Song>?>(null)
    private var latest: List<Song> = emptyList()
    private var interacted = false

    private val detail = repository.observePlaylist(id).onEach { d ->
        latest = d?.songs.orEmpty()
        val current = shown.value
        if (current.isNullOrEmpty() || d?.playlist?.isSmart != true || !interacted) shown.value = latest
    }

    val state: StateFlow<CollectionUiState> = combine(
        detail,
        shown,
        playback.state.map { it.current?.songId },
        refreshFailed,
    ) { d: PlaylistDetail?, songs, currentSongId, failed ->
        val playlist = d?.playlist
        CollectionUiState(
            header = playlist?.let {
                CollectionHeader(
                    title = it.name,
                    subtitle = it.owner,
                    songCount = it.songCount,
                    durationSec = it.durationSec,
                    comment = it.comment,
                    coverArtId = it.coverArt?.value,
                    mosaic = d.mosaic.map { m -> m.value }.toImmutableList(),
                )
            },
            tracks = songs.orEmpty().toTrackUi(showCovers = true),
            currentSongId = currentSongId,
            refreshFailed = failed,
            hasNewerContent = songs != null && songs != latest && latest.isNotEmpty(),
            isLoading = d == null || (songs.isNullOrEmpty() && (playlist?.songCount ?: 0) > 0),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    init {
        viewModelScope.launch {
            val cached = repository.observePlaylist(id).first()?.playlist
            // A smart playlist that Home just prefetched is fresh: reloading would only reshuffle it again.
            val freshSmart = cached != null && cached.isSmart && cached.changed != null &&
                clock.now() - cached.changed!! < 10.minutes
            if (!freshSmart) refreshFailed.value = repository.refreshPlaylist(id).isFailure
            repository.markSeen(id)
        }
    }

    fun onUserInteraction() {
        interacted = true
    }

    fun showNewerContent() {
        shown.value = latest
    }

    fun play(index: Int) {
        interacted = true
        val songs = shown.value.orEmpty()
        if (songs.isNotEmpty()) playback.playSongs(songs, startIndex = index)
    }

    fun shuffle() {
        interacted = true
        val songs = shown.value.orEmpty()
        if (songs.isNotEmpty()) playback.playSongs(songs, shuffle = true)
    }
}
