package dev.gruebling.navi.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.FavoritesRepository
import dev.gruebling.navi.core.model.SongId
import dev.gruebling.navi.core.playback.PlaybackController
import dev.gruebling.navi.core.playback.model.PlayerState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val favorites: FavoritesRepository,
) : ViewModel() {
    val state: StateFlow<PlayerState> = playback.state

    /** Whether the current track is starred; follows optimistic updates from anywhere in the app. */
    val isCurrentStarred: StateFlow<Boolean> = combine(
        playback.state.map { it.current?.songId }.distinctUntilChanged(),
        favorites.observeFavorites().map { f -> f.songs.mapTo(HashSet()) { it.id.value } },
    ) { id, starred -> id != null && id in starred }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    fun toggleStar() {
        val id = state.value.current?.songId ?: return
        val starred = isCurrentStarred.value
        viewModelScope.launch { favorites.setStarred(SongId(id), !starred) }
    }
}
