package dev.gruebling.navi.feature.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.playback.PlaybackController
import dev.gruebling.navi.core.playback.model.PlayerState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
) : ViewModel() {
    val state: StateFlow<PlayerState> = playback.state

    /** Main thread only; polled by the UI while visible. */
    fun positionMs(): Long = playback.positionMs()

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(ms: Long) = playback.seekTo(ms)
}
