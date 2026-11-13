package dev.gruebling.navi.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.download.Downloads
import dev.gruebling.navi.core.model.DownloadTarget
import dev.gruebling.navi.core.model.EnqueuePosition
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
import dev.gruebling.navi.core.model.DownloadStatus
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel @AssistedInject constructor(
    @Assisted playlistId: String,
    private val repository: PlaylistRepository,
    private val playback: PlaybackController,
    private val clock: Clock,
    private val downloads: Downloads,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playlistId: String): PlaylistViewModel
    }

    private val id = PlaylistId(playlistId)
    private val refreshFailed = MutableStateFlow(false)
    private val target = DownloadTarget.playlist(id)

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
        downloads.ui(target),
    ) { d: PlaylistDetail?, songs, currentSongId, failed, download ->
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
            download = download,
            canEdit = playlist?.isEditable == true,
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

    fun download() = viewModelScope.toggleDownload(downloads, target)

    fun removeDownload() = viewModelScope.removeDownload(downloads, target)

    fun enqueue(index: Int, position: EnqueuePosition) {
        interacted = true
        shown.value?.getOrNull(index)?.let { playback.enqueue(listOf(it), position) }
    }

    // Editing (own playlists only).

    private val _editor = MutableStateFlow<PlaylistEditState?>(null)
    val editor: StateFlow<PlaylistEditState?> = _editor.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    fun startEdit() {
        val s = state.value
        if (!s.canEdit) return
        interacted = true
        _editor.value = PlaylistEditState(name = s.header?.title.orEmpty(), tracks = latest.toTrackUi(showCovers = true))
    }

    fun cancelEdit() {
        _editor.value = null
    }

    fun renameInEdit(name: String) = _editor.update { it?.copy(name = name, changed = true) }

    fun moveInEdit(fromKey: Any, toKey: Any) = _editor.update { e ->
        val tracks = e?.tracks ?: return@update e
        val from = tracks.indexOfFirst { it.key == fromKey }
        val to = tracks.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0) return@update e
        e.copy(tracks = tracks.toMutableList().apply { add(to, removeAt(from)) }.toImmutableList(), changed = true)
    }

    fun removeInEdit(key: String) = _editor.update { e ->
        e?.copy(tracks = e.tracks.filterNot { it.key == key }.toImmutableList(), changed = true)
    }

    fun saveEdit() {
        val e = _editor.value ?: return
        if (e.isSaving || e.name.isBlank()) return
        if (!e.changed) return cancelEdit()
        _editor.value = e.copy(isSaving = true)
        viewModelScope.launch {
            val saved = repository.saveEdit(id, e.name.trim(), e.tracks.map { it.songId }).isSuccess
            if (saved) {
                _editor.value = null
                downloads.sync() // a downloaded playlist gets its new songs
            } else {
                _editor.update { it?.copy(isSaving = false) }
                _messages.send(R.string.playlist_edit_save_failed)
            }
        }
    }

    /** Deletes the playlist (and its download); [onDeleted] leaves the screen. */
    fun deletePlaylist(onDeleted: () -> Unit) {
        val downloaded = state.value.download.status != DownloadStatus.None
        viewModelScope.launch {
            if (repository.deletePlaylist(id).isSuccess) {
                if (downloaded) {
                    downloads.remove(target)
                    // The cache no longer knows the songs; a sync deletes the files nothing else needs.
                    downloads.sync()
                }
                _editor.value = null
                onDeleted()
            } else {
                _messages.send(R.string.playlist_edit_delete_failed)
            }
        }
    }
}
