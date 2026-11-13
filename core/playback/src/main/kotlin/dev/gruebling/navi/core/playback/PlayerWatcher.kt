package dev.gruebling.navi.core.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope

/**
 * Follows the player from outside core:playback while [PlaybackService] runs, such as the home screen widgets.
 * Bind implementations with `@IntoSet`. [attach] and [detach] run on the main thread; [scope] ends with the service.
 */
interface PlayerWatcher {
    fun attach(player: Player, scope: CoroutineScope)

    /** The service is going away; the player is still readable, but not for long. */
    fun detach()
}
