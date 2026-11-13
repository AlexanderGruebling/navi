package dev.gruebling.navi.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.data.session.SessionState
import dev.gruebling.navi.core.playback.MediaItems
import dev.gruebling.navi.core.playback.PlayerWatcher
import dev.gruebling.navi.core.playback.QueuePersistence
import dev.gruebling.navi.core.ui.image.CoverArtRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the player widgets in step with playback. While the playback service runs, it follows the player; otherwise
 * (after a reboot, or when a widget was just placed) it shows the current song of the saved queue, paused.
 * Everything except the cover loading runs on the main thread.
 */
@Singleton
class NowPlayingWidgets @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queuePersistence: QueuePersistence,
    private val session: SessionManager,
    @ApplicationScope private val appScope: CoroutineScope,
) : PlayerWatcher, AccountCache {
    private val covers = WidgetCovers(context) {
        // A process started by a widget hasn't restored the login yet; without it no cover URL can be built.
        session.state.first { it != SessionState.Loading }
    }
    private var player: Player? = null
    private var serviceScope: CoroutineScope? = null
    private var render: Job? = null
    private var ticker: Job? = null
    private var shownDots = -1

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                show(player.nowPlaying())
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) followProgress(player)
        }
    }

    override fun attach(player: Player, scope: CoroutineScope) {
        this.player = player
        serviceScope = scope
        player.addListener(listener)
        show(player.nowPlaying())
        followProgress(player)
    }

    override fun detach() {
        val player = player ?: return
        player.removeListener(listener)
        ticker?.cancel()
        show(player.nowPlaying()?.copy(isPlaying = false))
        this.player = null
        serviceScope = null
    }

    /** A widget was placed, resized or the launcher wants fresh views. */
    fun refresh() {
        val player = player
        if (player != null) show(player.nowPlaying()) else showLater { saved() }
    }

    /** Logged out: the queue is gone, show the empty widget. */
    override suspend fun clear() = withContext(Dispatchers.Main) { show(null) }

    private fun show(now: NowPlaying?) = showLater { now }

    private fun showLater(now: suspend () -> NowPlaying?) {
        render?.cancel()
        render = appScope.launch(Dispatchers.Main) {
            val state = now()
            val cover = covers.load(state?.coverArt)
            shownDots = state?.dots ?: 0
            WidgetViews.pushPlayers(context, state, cover)
        }
    }

    /** While playing, light the next progress dot when it's time; nothing else changes in between. */
    private fun followProgress(player: Player) {
        ticker?.cancel()
        val scope = serviceScope ?: return
        if (!player.isPlaying) return
        ticker = scope.launch {
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                val dots = player.progressDots()
                if (dots != shownDots) {
                    shownDots = dots
                    WidgetViews.pushProgress(context, dots)
                }
            }
        }
    }

    private suspend fun saved(): NowPlaying? {
        val saved = queuePersistence.load() ?: return null
        val item = saved.mediaItems.getOrNull(saved.startIndex) ?: return null
        val duration = item.mediaMetadata.durationMs ?: 0L
        return item.toNowPlaying(isPlaying = false, dots = dotsFor(saved.startPositionMs, duration))
    }

    private fun Player.nowPlaying(): NowPlaying? {
        val item = currentMediaItem ?: return null
        val playing = playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
        return item.toNowPlaying(playing, progressDots())
    }

    private fun Player.progressDots(): Int {
        val duration = duration.takeIf { it > 0 } ?: currentMediaItem?.mediaMetadata?.durationMs ?: 0L
        return dotsFor(currentPosition, duration)
    }

    private fun MediaItem.toNowPlaying(isPlaying: Boolean, dots: Int): NowPlaying? = NowPlaying(
        title = mediaMetadata.title?.toString() ?: return null,
        artist = mediaMetadata.artist?.toString(),
        coverArt = MediaItems.coverArtId(this),
        isPlaying = isPlaying,
        dots = dots,
    )

    private companion object {
        const val PROGRESS_TICK_MS = 4_000L

        fun dotsFor(positionMs: Long, durationMs: Long): Int =
            if (durationMs <= 0) 0 else (positionMs * PROGRESS_DOTS / durationMs).toInt().coerceIn(0, PROGRESS_DOTS)
    }
}

/** The current cover as a rounded bitmap, from Coil's caches. Keeps the last one, which most updates reuse. */
private class WidgetCovers(private val context: Context, private val awaitLogin: suspend () -> Unit) {
    private var id: String? = null
    private var bitmap: Bitmap? = null

    suspend fun load(coverArt: String?): Bitmap? {
        if (coverArt == null) return null
        if (coverArt == id) return bitmap
        awaitLogin()
        val request = ImageRequest.Builder(context)
            .data(CoverArtRequest(coverArt, SIZE_PX))
            .allowHardware(false)
            .build()
        val image = SingletonImageLoader.get(context).execute(request).image ?: return null
        val rounded = withContext(Dispatchers.Default) { round(image.toBitmap()) }
        id = coverArt
        bitmap = rounded
        return rounded
    }

    private fun round(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setTranslate(-(source.width - size) / 2f, -(source.height - size) / 2f) })
        }
        val radius = size * CORNER
        return createBitmap(size, size).also {
            Canvas(it).drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        }
    }

    private companion object {
        /** Big enough for the 4×2 widget's cover on dense screens, small enough for the widget's bitmap budget. */
        const val SIZE_PX = 300
        const val CORNER = 0.14f
    }
}
