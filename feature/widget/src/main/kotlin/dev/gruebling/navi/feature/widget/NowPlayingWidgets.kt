package dev.gruebling.navi.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.data.repository.FavoritesRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    favorites: FavoritesRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : PlayerWatcher, AccountCache {
    private val covers = WidgetCovers(context)
    private var player: Player? = null
    private var serviceScope: CoroutineScope? = null
    private var render: Job? = null
    private var ticker: Job? = null
    private var shown: NowPlaying? = null
    private var shownDots = -1
    private var shownBars = -1
    private var starred: Set<String> = emptySet()

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

    init {
        // The media card's heart follows favorites changed anywhere: in the app, or by the heart itself.
        appScope.launch(Dispatchers.Main) {
            favorites.observeFavorites().map { f -> f.songs.mapTo(HashSet()) { it.id.value } }.distinctUntilChanged().collect { ids ->
                starred = ids
                val current = shown ?: return@collect
                if (current.starred == (current.songId in ids)) return@collect
                val player = player
                show(if (player != null) player.nowPlaying() else current.copy(starred = !current.starred))
            }
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
            // A process started by a widget hasn't restored the login yet; covers and server name need it.
            session.state.first { it != SessionState.Loading }
            val state = now()
            val cover = covers.load(state?.coverArt)
            shown = state
            shownDots = state?.progress(PROGRESS_DOTS) ?: 0
            shownBars = state?.progress(PROGRESS_BARS) ?: 0
            WidgetViews.pushPlayers(context, state, cover, serverName())
        }
    }

    /** While playing, light the next progress mark when it's time; nothing else changes in between. */
    private fun followProgress(player: Player) {
        ticker?.cancel()
        val scope = serviceScope ?: return
        if (!player.isPlaying) return
        ticker = scope.launch {
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                val duration = player.durationMs()
                val dots = progressOf(player.currentPosition, duration, PROGRESS_DOTS)
                val bars = progressOf(player.currentPosition, duration, PROGRESS_BARS)
                if (dots != shownDots || bars != shownBars) {
                    WidgetViews.pushProgress(context, dots.takeIf { it != shownDots }, bars.takeIf { it != shownBars })
                    shownDots = dots
                    shownBars = bars
                }
            }
        }
    }

    private suspend fun saved(): NowPlaying? {
        val saved = queuePersistence.load() ?: return null
        val item = saved.mediaItems.getOrNull(saved.startIndex) ?: return null
        return item.toNowPlaying(isPlaying = false, positionMs = saved.startPositionMs, durationMs = item.mediaMetadata.durationMs ?: 0L)
    }

    private fun Player.nowPlaying(): NowPlaying? {
        val item = currentMediaItem ?: return null
        val playing = playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
        return item.toNowPlaying(playing, currentPosition, durationMs())
    }

    private fun Player.durationMs(): Long = duration.takeIf { it > 0 } ?: currentMediaItem?.mediaMetadata?.durationMs ?: 0L

    private fun MediaItem.toNowPlaying(isPlaying: Boolean, positionMs: Long, durationMs: Long): NowPlaying? = NowPlaying(
        songId = mediaId,
        title = mediaMetadata.title?.toString() ?: return null,
        artist = mediaMetadata.artist?.toString(),
        album = mediaMetadata.albumTitle?.toString(),
        coverArt = MediaItems.coverArtId(this),
        isPlaying = isPlaying,
        positionMs = positionMs,
        positionAtMs = SystemClock.elapsedRealtime(),
        durationMs = durationMs,
        starred = mediaId in starred,
    )

    /** The server's host name for the media card's header, like the design's "music.home". */
    private fun serverName(): String? = session.current()?.serverUrl?.let { Uri.parse(it).host }

    private companion object {
        const val PROGRESS_TICK_MS = 3_000L
    }
}

/** The current cover as a rounded bitmap, from Coil's caches. Keeps the last one, which most updates reuse. */
private class WidgetCovers(private val context: Context) {
    private var id: String? = null
    private var bitmap: Bitmap? = null

    suspend fun load(coverArt: String?): Bitmap? {
        if (coverArt == null) return null
        if (coverArt == id) return bitmap
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
