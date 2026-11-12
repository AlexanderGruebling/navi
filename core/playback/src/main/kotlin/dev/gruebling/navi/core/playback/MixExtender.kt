package dev.gruebling.navi.core.playback

import androidx.media3.common.Player
import dev.gruebling.navi.core.data.repository.MixRepository
import dev.gruebling.navi.core.model.Song
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps an instant mix going: while the last queue item came from a mix and only a few songs are left, it appends
 * songs similar to the one playing (skipping anything already queued). Runs in the service, so it also works with
 * the app in the background. Adding other songs at the end, shuffle or repeat end the endless part.
 */
class MixExtender internal constructor(
    /** (seed song id, its artist id) → similar songs. */
    private val similarSongs: suspend (String, String?) -> Result<List<Song>>,
) : Player.Listener {
    @Inject constructor(mixes: MixRepository) : this({ seed, artist -> mixes.similarSongs(seed, artist) })

    private lateinit var player: Player
    private lateinit var scope: CoroutineScope
    private var job: Job? = null

    /** Last queue item for which the server had nothing new; not asked again for the same queue end. */
    private var exhaustedAt: String? = null

    fun attach(player: Player, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
        player.addListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) {
            maybeExtend()
        }
    }

    private fun maybeExtend() {
        val count = player.mediaItemCount
        if (count == 0 || job?.isActive == true) return
        if (player.shuffleModeEnabled || player.repeatMode != Player.REPEAT_MODE_OFF) return
        val last = player.getMediaItemAt(count - 1)
        if (!MediaItems.isFromMix(last) || last.mediaId == exhaustedAt) return
        if (count - 1 - player.currentMediaItemIndex >= LOOKAHEAD) return
        val seed = player.currentMediaItem ?: return

        job = scope.launch {
            val queued = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            // A failure (offline) is retried on the next track change; an empty answer is not.
            val result = similarSongs(seed.mediaId, MediaItems.artistId(seed)).getOrNull() ?: return@launch
            val fresh = result.filter { it.id.value !in queued }.take(BATCH_SIZE)
            val end = player.mediaItemCount.takeIf { it > 0 }?.let { player.getMediaItemAt(it - 1).mediaId }
            when {
                end != last.mediaId -> Unit // the queue changed while loading
                fresh.isEmpty() -> exhaustedAt = last.mediaId
                else -> player.addMediaItems(fresh.map { MediaItems.fromSong(it, fromMix = true) })
            }
        }
    }

    private companion object {
        /** Extend when fewer than this many songs come after the current one. */
        const val LOOKAHEAD = 3
        const val BATCH_SIZE = 20
    }
}
