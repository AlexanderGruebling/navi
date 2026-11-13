package dev.gruebling.navi.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.model.EnqueuePosition
import dev.gruebling.navi.core.model.ReplayGain
import dev.gruebling.navi.core.model.Song
import dev.gruebling.navi.core.playback.model.PlayerState
import dev.gruebling.navi.core.playback.model.QueueEntry
import dev.gruebling.navi.core.playback.model.RepeatMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's handle on playback. Connects lazily to [PlaybackService] through a MediaController and mirrors the
 * player into [state]. All commands are fire-and-forget; the state updates when the player has applied them.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var queueCache: List<QueueEntry> = emptyList()
    private var orderCache: List<Int> = emptyList()
    private var orderShuffle: Boolean? = null

    private val connection = scope.async(Dispatchers.Main, start = CoroutineStart.LAZY) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync().await().also { c ->
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    publish(rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
                }
            })
            publish(rebuildQueue = true)
        }
    }

    /** Connects (and thereby starts the service, which restores the last queue). Call after the first frame. */
    fun connect() {
        connection.start()
    }

    /** Current position; main thread only. Read it on demand (e.g. every 250 ms while the player is visible). */
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun bufferedPositionMs(): Long = controller?.bufferedPosition ?: 0L

    /** Player volume set by volume levelling, and the tags it came from (debug output); main thread only. */
    fun levelling(): Pair<Float, ReplayGain?> =
        (controller?.volume ?: 1f) to controller?.currentMediaItem?.let(MediaItems::replayGain)

    /** [fromMix]: the songs are an instant mix, so the queue keeps growing at the end (see MixExtender). */
    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false, fromMix: Boolean = false) = command {
        setMediaItems(songs.map { MediaItems.fromSong(it, fromMix) }, if (shuffle) C.INDEX_UNSET else startIndex, 0)
        shuffleModeEnabled = shuffle
        if (shuffle) seekToDefaultPosition(currentTimeline.getFirstWindowIndex(true).coerceAtLeast(0))
        prepare()
        play()
    }

    fun togglePlayPause() = command { if (isPlaying) pause() else { if (playbackState == Player.STATE_IDLE) prepare(); play() } }
    fun play() = command { if (playbackState == Player.STATE_IDLE) prepare(); play() }
    fun pause() = command { pause() }
    fun next() = command { seekToNext() }
    fun previous() = command { seekToPrevious() }
    fun seekTo(positionMs: Long) = command { seekTo(positionMs) }
    fun skipToQueueIndex(index: Int) = command { seekToDefaultPosition(index); play() }
    fun setShuffle(enabled: Boolean) = command { shuffleModeEnabled = enabled }

    fun cycleRepeat() = command {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Keeps the current track playing and replaces everything before and after it with [songs], an instant mix.
     * Shuffle goes off: a mix is already in order of similarity.
     */
    fun continueWithMix(songs: List<Song>) = command {
        if (mediaItemCount == 0) {
            setMediaItems(songs.map { MediaItems.fromSong(it, fromMix = true) })
            prepare()
            play()
            return@command
        }
        val current = currentMediaItemIndex
        val oldCount = mediaItemCount
        // Add first, then remove: the queue never ends near the current song in between, which would make
        // MixExtender fetch a batch for nothing.
        shuffleModeEnabled = false
        addMediaItems(songs.map { MediaItems.fromSong(it, fromMix = true) })
        if (current + 1 < oldCount) removeMediaItems(current + 1, oldCount)
        if (current > 0) removeMediaItems(0, current)
        if (playbackState == Player.STATE_IDLE) prepare()
        play()
    }

    fun enqueue(songs: List<Song>, position: EnqueuePosition) = when (position) {
        EnqueuePosition.Next -> playNext(songs)
        EnqueuePosition.End -> addToQueue(songs)
    }

    /** Inserts right after the current track. */
    fun playNext(songs: List<Song>) = command {
        val at = if (mediaItemCount == 0) 0 else nextMediaItemIndexOrEnd()
        addMediaItems(at, songs.map(MediaItems::fromSong))
        if (playbackState == Player.STATE_IDLE) prepare()
    }

    fun addToQueue(songs: List<Song>) = command {
        addMediaItems(songs.map(MediaItems::fromSong))
        if (playbackState == Player.STATE_IDLE) prepare()
    }

    fun removeFromQueue(index: Int) = command { removeMediaItem(index) }

    /** Stops playback and empties the queue (logout). Suspends until the player has applied it. */
    suspend fun stopAndClear() {
        if (!connection.isActive && !connection.isCompleted) return
        withContext(Dispatchers.Main.immediate) {
            connection.await().run {
                stop()
                clearMediaItems()
            }
        }
    }
    fun moveInQueue(from: Int, to: Int) = command { moveMediaItem(from, to) }

    private fun Player.nextMediaItemIndexOrEnd(): Int =
        if (shuffleModeEnabled) currentMediaItemIndex + 1 else nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: mediaItemCount

    private fun command(block: MediaController.() -> Unit) {
        scope.launch(Dispatchers.Main.immediate) { connection.await().block() }
    }

    private fun publish(rebuildQueue: Boolean) {
        val c = controller ?: return
        if (rebuildQueue) queueCache = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toEntry() }
        if (rebuildQueue || orderShuffle != c.shuffleModeEnabled) {
            orderCache = c.playOrder()
            orderShuffle = c.shuffleModeEnabled
        }
        val index = c.currentMediaItemIndex
        _state.value = PlayerState(
            current = c.currentMediaItem?.toEntry(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: c.currentMediaItem?.mediaMetadata?.durationMs ?: 0,
            queue = queueCache,
            currentIndex = if (queueCache.isEmpty()) -1 else index,
            playOrder = orderCache,
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                else -> RepeatMode.Off
            },
            hasNext = c.hasNextMediaItem(),
            // Not "a previous item, or past 3 s": state is only published on player events, so a position check
            // would go stale. "Previous" is always useful anyway (it restarts the song or goes back).
            hasPrevious = c.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            error = c.playerError?.let { "${it.errorCodeName}: ${it.message}" },
        )
    }

    private fun Player.playOrder(): List<Int> {
        val timeline = currentTimeline
        if (timeline.isEmpty) return emptyList()
        val order = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffleModeEnabled)
        while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
        }
        return order
    }

    private fun MediaItem.toEntry(): QueueEntry {
        val m = mediaMetadata
        return QueueEntry(
            songId = mediaId,
            title = m.title?.toString().orEmpty(),
            artist = m.artist?.toString(),
            album = m.albumTitle?.toString(),
            albumId = MediaItems.albumId(this),
            artistId = MediaItems.artistId(this),
            coverArt = MediaItems.coverArtId(this),
            durationMs = m.durationMs ?: 0,
        )
    }

}
