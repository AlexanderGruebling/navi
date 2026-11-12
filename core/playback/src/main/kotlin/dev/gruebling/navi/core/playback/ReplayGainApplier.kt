package dev.gruebling.navi.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import dev.gruebling.navi.core.datastore.StreamingSettingsStore
import dev.gruebling.navi.core.model.ReplayGain
import dev.gruebling.navi.core.model.ReplayGainMode
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Volume levelling: at every track change, sets the player's own volume (not the device volume) from the song's
 * ReplayGain tags. The player volume can only turn down, so a quiet track that asks for more than 0 dB plays at
 * 0 dB; the peak keeps a boost from clipping either way.
 */
class ReplayGainApplier @Inject constructor(private val settings: StreamingSettingsStore) : Player.Listener {
    private lateinit var player: Player
    private var mode = ReplayGainMode.Auto

    fun attach(player: Player, scope: CoroutineScope) {
        this.player = player
        player.addListener(this)
        scope.launch {
            settings.replayGainMode.collect {
                mode = it
                apply()
            }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
        ) {
            apply()
        }
    }

    private fun apply() {
        val item = player.currentMediaItem ?: return
        player.volume = ReplayGainMath.volume(mode, MediaItems.replayGain(item), useAlbumGain = useAlbumGain(item))
    }

    /** Album gain in [ReplayGainMode.Album], and in Auto while the songs around this one are from the same album. */
    private fun useAlbumGain(item: androidx.media3.common.MediaItem): Boolean = when (mode) {
        ReplayGainMode.Album -> true
        ReplayGainMode.Auto -> {
            val album = MediaItems.albumId(item)
            album != null && listOf(player.previousMediaItemIndex, player.nextMediaItemIndex).any { i ->
                i != C.INDEX_UNSET && MediaItems.albumId(player.getMediaItemAt(i)) == album
            }
        }
        else -> false
    }
}

internal object ReplayGainMath {
    /**
     * For songs without tags: full volume. A guessed cut would only help if most of the library were tagged with
     * big cuts; on a mixed library (tags around 0 dB, most songs untagged) it makes untagged songs far too quiet.
     */
    const val UNTAGGED_GAIN_DB = 0f

    /** Player volume (0..1) for a song with [gain] tags. */
    fun volume(mode: ReplayGainMode, gain: ReplayGain?, useAlbumGain: Boolean): Float {
        if (mode == ReplayGainMode.Off) return 1f
        val album = useAlbumGain && gain?.albumGain != null
        val db = (if (album) gain?.albumGain else gain?.trackGain ?: gain?.albumGain) ?: UNTAGGED_GAIN_DB
        val peak = if (album) gain?.albumPeak ?: gain?.trackPeak else gain?.trackPeak ?: gain?.albumPeak
        var linear = 10f.pow(db / 20f)
        if (peak != null && peak > 0f) linear = min(linear, 1f / peak)
        return linear.coerceIn(0f, 1f)
    }
}
