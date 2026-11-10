package dev.gruebling.navi.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import dev.gruebling.navi.core.data.di.IoDispatcher
import dev.gruebling.navi.core.database.dao.PlaybackDao
import dev.gruebling.navi.core.database.entity.QueueStateEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saves the queue to Room (debounced, plus position every 15 s while playing) and restores it. */
class QueuePersistence @Inject constructor(
    private val dao: PlaybackDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : Player.Listener {
    private lateinit var player: Player
    private lateinit var scope: CoroutineScope
    private var pendingSave: Job? = null
    private var positionSaver: Job? = null
    private var queueDirty = true

    fun attach(player: Player, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
        player.addListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_TIMELINE_CHANGED)) queueDirty = true
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) {
            scheduleSave()
        }
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
            positionSaver?.cancel()
            if (player.isPlaying) {
                positionSaver = scope.launch {
                    while (isActive) {
                        delay(15_000)
                        save()
                    }
                }
            }
        }
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(1_000)
            save()
        }
    }

    /** Reads the player on the main thread, writes on IO. Only rewrites the item table when the queue changed. */
    suspend fun save() {
        val count = player.mediaItemCount
        val items = if (queueDirty) (0 until count).map { MediaItems.toQueue(it, player.getMediaItemAt(it)) } else null
        queueDirty = false
        val state = QueueStateEntity(
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        withContext(io) {
            if (items != null) dao.replaceQueue(items, state) else dao.upsertQueueState(state)
        }
    }

    suspend fun load(): MediaSession.MediaItemsWithStartPosition? = withContext(io) {
        val items = dao.queue()
        if (items.isEmpty()) return@withContext null
        val state = dao.queueState()
        val index = (state?.currentIndex ?: 0).coerceIn(0, items.lastIndex)
        MediaSession.MediaItemsWithStartPosition(
            items.map(MediaItems::fromQueue),
            index,
            state?.positionMs ?: C.TIME_UNSET,
        )
    }

    /** At service start: put the last queue back without starting playback (the mini player shows it). */
    suspend fun restoreInto(player: Player) {
        if (player.mediaItemCount > 0) return
        val saved = load() ?: return
        val state = withContext(io) { dao.queueState() }
        player.setMediaItems(saved.mediaItems, saved.startIndex, saved.startPositionMs)
        state?.let {
            player.shuffleModeEnabled = it.shuffle
            player.repeatMode = it.repeatMode
        }
        queueDirty = false
    }

}
