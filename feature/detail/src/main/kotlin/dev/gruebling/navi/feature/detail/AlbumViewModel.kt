package dev.gruebling.navi.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.EnqueuePosition
import dev.gruebling.navi.core.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel @AssistedInject constructor(
    @Assisted albumId: String,
    private val repository: AlbumRepository,
    private val playback: PlaybackController,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(albumId: String): AlbumViewModel
    }

    private val id = AlbumId(albumId)
    private val refreshFailed = MutableStateFlow(false)
    private val detail = repository.observeAlbum(id)

    val state: StateFlow<CollectionUiState> = combine(
        detail,
        playback.state.map { it.current?.songId },
        refreshFailed,
    ) { d, currentSongId, failed ->
        val album = d?.album
        CollectionUiState(
            header = album?.let {
                CollectionHeader(
                    title = it.name,
                    subtitle = listOfNotNull(it.artist, it.year?.toString()).joinToString(" · ").ifEmpty { null },
                    songCount = it.songCount,
                    durationSec = it.durationSec,
                    comment = null,
                    coverArtId = it.coverArt?.value,
                )
            },
            tracks = d?.songs.orEmpty().toTrackUi(showCovers = false),
            currentSongId = currentSongId,
            refreshFailed = failed,
            isLoading = d == null || (d.songs.isEmpty() && album?.songCount != 0),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    init {
        viewModelScope.launch { refreshFailed.value = repository.refreshAlbum(id).isFailure }
    }

    fun play(index: Int) {
        viewModelScope.launch {
            val songs = detail.first()?.songs.orEmpty()
            if (songs.isNotEmpty()) playback.playSongs(songs, startIndex = index)
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            val songs = detail.first()?.songs.orEmpty()
            if (songs.isNotEmpty()) playback.playSongs(songs, shuffle = true)
        }
    }

    fun enqueue(index: Int, position: EnqueuePosition) {
        viewModelScope.launch {
            detail.first()?.songs?.getOrNull(index)?.let { playback.enqueue(listOf(it), position) }
        }
    }
}
