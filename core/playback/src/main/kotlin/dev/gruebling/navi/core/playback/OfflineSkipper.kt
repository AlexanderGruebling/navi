package dev.gruebling.navi.core.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import dev.gruebling.navi.core.download.DownloadFiles
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Without network, a song that isn't downloaded can't load and the player would stop there. This jumps to the next
 * downloaded song in play order instead and keeps playing. With the network back, skipped songs stream as usual.
 */
class OfflineSkipper internal constructor(
    private val isDownloaded: (songId: String) -> Boolean,
    private val messages: PlaybackMessages,
) : Player.Listener {
    @Inject constructor(files: DownloadFiles, messages: PlaybackMessages) :
        this({ files.fileIfDownloaded(it) != null }, messages)

    private lateinit var player: Player

    fun attach(player: Player) {
        this.player = player
        player.addListener(this)
    }

    override fun onPlayerError(error: PlaybackException) {
        if (error.errorCode !in NETWORK_ERRORS) return
        val (index, skipped) = nextDownloaded() ?: return
        player.seekToDefaultPosition(index)
        // play-when-ready is still set from before the error, so this continues playing.
        player.prepare()
        messages.emit(PlaybackMessage.SkippedNotDownloaded(skipped))
    }

    /** The next downloaded item after the current one and how many items that skips; null if there is none. */
    private fun nextDownloaded(): Pair<Int, Int>? {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        val current = player.currentMediaItemIndex
        // Repeat one would only find the failing song again.
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        var index = current
        var skipped = 1
        repeat(timeline.windowCount) {
            index = timeline.getNextWindowIndex(index, repeat, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == current) return null
            if (isDownloaded(player.getMediaItemAt(index).mediaId)) return index to skipped
            skipped++
        }
        return null
    }

    private companion object {
        val NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        )
    }
}

/** Something the playback service tells the user about (shown as a toast by the app). */
sealed interface PlaybackMessage {
    /** [count] songs that aren't downloaded were skipped because there is no network. */
    data class SkippedNotDownloaded(val count: Int) : PlaybackMessage
}

/** From the playback service (same process) to the UI. */
@Singleton
class PlaybackMessages @Inject constructor() {
    private val flow = MutableSharedFlow<PlaybackMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<PlaybackMessage> = flow

    fun emit(message: PlaybackMessage) {
        flow.tryEmit(message)
    }
}
