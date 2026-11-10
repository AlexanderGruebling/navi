package dev.gruebling.navi.core.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.gruebling.navi.core.data.repository.ScrobbleRepository
import dev.gruebling.navi.core.model.SongId
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reports plays like Last.fm counts them: "now playing" when a track starts, a play once it was actually heard for
 * half its length or 4 minutes (tracks under 30 s never count). Seeking does not count as listening.
 */
class Scrobbler @Inject constructor(
    private val repository: ScrobbleRepository,
    private val clock: Clock,
) : Player.Listener {
    private lateinit var player: Player
    private lateinit var scope: CoroutineScope

    private var songId: String? = null
    private var startedAt = 0L
    private var listenedMs = 0L
    private var resumedAt: Long? = null
    private var nowPlayingSent = false
    private var submitted = false
    private var ticker: Job? = null

    fun attach(player: Player, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
        player.addListener(this)
        scope.launch { repository.flush() } // plays left over from an offline session
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Same song swapped in place (seeking in a transcoded stream): still the same play.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem?.mediaId == songId) return
        pauseClock()
        songId = mediaItem?.mediaId
        startedAt = clock.now().toEpochMilliseconds()
        listenedMs = 0
        nowPlayingSent = false
        submitted = false
        if (player.isPlaying) onIsPlayingChanged(true)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            resumedAt = SystemClock.elapsedRealtime()
            val id = songId
            if (id != null && !nowPlayingSent) {
                nowPlayingSent = true
                scope.launch { repository.nowPlaying(SongId(id)) }
            }
            ticker?.cancel()
            ticker = scope.launch {
                while (isActive) {
                    delay(5_000)
                    checkThreshold()
                }
            }
        } else {
            ticker?.cancel()
            pauseClock()
            checkThreshold()
        }
    }

    private fun pauseClock() {
        resumedAt?.let { listenedMs += SystemClock.elapsedRealtime() - it }
        resumedAt = null
    }

    private fun heardMs(): Long = listenedMs + (resumedAt?.let { SystemClock.elapsedRealtime() - it } ?: 0)

    private fun checkThreshold() {
        val id = songId ?: return
        if (submitted) return
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: player.currentMediaItem?.mediaMetadata?.durationMs
            ?: return
        if (durationMs < 30_000) return
        if (heardMs() >= minOf(durationMs / 2, 240_000)) {
            submitted = true
            val playedAt = startedAt
            scope.launch { repository.submit(SongId(id), playedAt) }
        }
    }
}
