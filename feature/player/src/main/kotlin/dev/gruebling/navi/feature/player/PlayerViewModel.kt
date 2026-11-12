package dev.gruebling.navi.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.FavoritesRepository
import dev.gruebling.navi.core.data.repository.LyricsRepository
import dev.gruebling.navi.core.model.Lyrics
import dev.gruebling.navi.core.model.SongId
import dev.gruebling.navi.core.playback.InstantMix
import dev.gruebling.navi.core.playback.PlaybackController
import dev.gruebling.navi.core.playback.model.PlayerState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.gruebling.navi.core.ui.R as UiR

/** Lyrics of the current song, as the player shows them. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Available(val lyrics: Lyrics) : LyricsState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val favorites: FavoritesRepository,
    private val lyricsRepository: LyricsRepository,
    private val instantMix: InstantMix,
) : ViewModel() {
    val state: StateFlow<PlayerState> = playback.state

    private val _isStartingMix = MutableStateFlow(false)
    val isStartingMix: StateFlow<Boolean> = _isStartingMix

    /** Why a mix didn't start, as string resources to show once. */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages

    /** Whether the current track is starred; follows optimistic updates from anywhere in the app. */
    val isCurrentStarred: StateFlow<Boolean> = combine(
        playback.state.map { it.current?.songId }.distinctUntilChanged(),
        favorites.observeFavorites().map { f -> f.songs.mapTo(HashSet()) { it.id.value } },
    ) { id, starred -> id != null && id in starred }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Lyrics of the current song; fetched once per song while anyone collects (the player host does, always). */
    val lyrics: StateFlow<LyricsState> = playback.state
        .map { s -> s.current?.let { Triple(it.songId, it.artist, it.title) } }
        .distinctUntilChanged()
        .flatMapLatest { song ->
            if (song == null) return@flatMapLatest flowOf<LyricsState>(LyricsState.None)
            flow<LyricsState> {
                emit(LyricsState.Loading)
                val lyrics = lyricsRepository.lyrics(SongId(song.first), song.second, song.third).getOrNull()
                emit(if (lyrics != null) LyricsState.Available(lyrics) else LyricsState.None)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsState.None)

    /** Main thread only; polled by the UI while visible. */
    fun positionMs(): Long = playback.positionMs()

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(ms: Long) = playback.seekTo(ms)
    fun toggleShuffle() = playback.setShuffle(!state.value.shuffle)
    fun cycleRepeat() = playback.cycleRepeat()
    fun skipTo(queueIndex: Int) = playback.skipToQueueIndex(queueIndex)
    fun remove(queueIndex: Int) = playback.removeFromQueue(queueIndex)

    /** The current song keeps playing; what follows becomes songs similar to it, and keeps growing. */
    fun startMix() {
        if (_isStartingMix.value) return
        viewModelScope.launch {
            _isStartingMix.value = true
            try {
                when (instantMix.startFromCurrentSong()) {
                    InstantMix.Outcome.Started -> Unit
                    InstantMix.Outcome.NoSongs -> _messages.tryEmit(UiR.string.mix_unavailable)
                    InstantMix.Outcome.Failed -> _messages.tryEmit(UiR.string.mix_failed)
                }
            } finally {
                _isStartingMix.value = false
            }
        }
    }

    fun toggleStar() {
        val id = state.value.current?.songId ?: return
        val starred = isCurrentStarred.value
        viewModelScope.launch { favorites.setStarred(SongId(id), !starred) }
    }
}
