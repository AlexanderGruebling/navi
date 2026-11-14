package dev.gruebling.navi.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.graphics.createBitmap
import androidx.media3.session.MediaButtonReceiver
import dev.gruebling.navi.core.ui.component.dotwaveLogoBitmap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import dev.gruebling.navi.core.ui.R as UiR

/** What the player widgets show. null where a [NowPlaying] is expected = nothing in the queue. */
internal data class NowPlaying(
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val coverArt: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    /** [SystemClock.elapsedRealtime] when [positionMs] was read; the media card's clock counts on from there. */
    val positionAtMs: Long,
    val durationMs: Long,
    val starred: Boolean,
) {
    /** How many of [steps] progress marks are lit. */
    fun progress(steps: Int): Int = progressOf(positionMs, durationMs, steps)
}

internal fun progressOf(positionMs: Long, durationMs: Long, steps: Int): Int =
    if (durationMs <= 0) 0 else (positionMs * steps / durationMs).toInt().coerceIn(0, steps)

/** Progress dots of the small player. */
internal const val PROGRESS_DOTS = 12

/** Bars of the media card's waveform. */
internal const val PROGRESS_BARS = 40

private const val WHITE = 0xFFFFFFFF.toInt()
private const val ACCENT = 0xFFD71921.toInt()

private val DOT_IDS = intArrayOf(
    R.id.widget_dot_0, R.id.widget_dot_1, R.id.widget_dot_2, R.id.widget_dot_3, R.id.widget_dot_4, R.id.widget_dot_5,
    R.id.widget_dot_6, R.id.widget_dot_7, R.id.widget_dot_8, R.id.widget_dot_9, R.id.widget_dot_10, R.id.widget_dot_11,
)

/** Builds the widgets' RemoteViews and hands them to the launcher. */
internal object WidgetViews {
    private var logo: Bitmap? = null

    fun pushPlayers(context: Context, now: NowPlaying?, cover: Bitmap?, source: String?) {
        val manager = AppWidgetManager.getInstance(context)
        // Per widget: the dot-matrix title is an image sized to that widget's width.
        manager.idsOf<PlayerWidget>(context)?.forEach { id ->
            val (width, height) = DotText.sizeDp(manager, id, fallback = 320 to 150)
            // Text column = widget - padding - square cover - gap.
            val title = DotText.render(context, now?.title ?: context.getString(UiR.string.app_name), 22f, width - height - 16, 2, WHITE)
            manager.updateAppWidget(id, player(context, R.layout.widget_player, now, cover).withDotTitle(title))
        }
        manager.idsOf<PlayerSmallWidget>(context)?.let {
            manager.updateAppWidget(it, player(context, R.layout.widget_player_small, now, cover).withDots(now?.progress(PROGRESS_DOTS) ?: 0))
        }
        manager.idsOf<MediaCardWidget>(context)?.forEach { id ->
            manager.updateAppWidget(id, mediaCard(context, manager, id, now, cover, source))
        }
    }

    /** Only the progress marks; cheap enough to run every few seconds while playing. */
    fun pushProgress(context: Context, dots: Int?, bars: Int?) {
        val manager = AppWidgetManager.getInstance(context)
        if (dots != null) {
            manager.idsOf<PlayerSmallWidget>(context)?.let {
                manager.partiallyUpdateAppWidget(it, RemoteViews(context.packageName, R.layout.widget_player_small).withDots(dots))
            }
        }
        if (bars != null) {
            manager.idsOf<MediaCardWidget>(context)?.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_media_card)
                views.setImageViewBitmap(R.id.widget_bars, ProgressBars.render(context, barsWidthDp(manager, id), bars))
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
    }

    private fun player(context: Context, @LayoutRes layout: Int, now: NowPlaying?, cover: Bitmap?) =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_title, now?.title ?: context.getString(UiR.string.app_name))
            setTextViewText(R.id.widget_artist, if (now == null) context.getString(R.string.widget_nothing_playing) else now.artist.orEmpty())
            if (layout == R.layout.widget_player) setViewVisibility(R.id.widget_label, if (now != null) View.VISIBLE else View.GONE)
            if (cover != null) setImageViewBitmap(R.id.widget_cover, cover)
            controls(context, now, withSkip = layout != R.layout.widget_player_small)
        }

    private fun mediaCard(context: Context, manager: AppWidgetManager, id: Int, now: NowPlaying?, cover: Bitmap?, source: String?) =
        RemoteViews(context.packageName, R.layout.widget_media_card).apply {
            setImageViewBitmap(R.id.widget_logo, logo(context))
            val name = context.getString(UiR.string.app_name)
            setTextViewText(R.id.widget_source, if (source != null) "$name · $source" else name)
            setTextViewText(R.id.widget_title, now?.title ?: name)
            setTextViewText(
                R.id.widget_artist,
                if (now == null) context.getString(R.string.widget_nothing_playing) else listOfNotNull(now.artist, now.album).joinToString(" · "),
            )
            if (cover != null) setImageViewBitmap(R.id.widget_cover, cover)
            controls(context, now, withSkip = true)
            if (now != null) {
                setViewVisibility(R.id.widget_like, View.VISIBLE)
                setImageViewResource(R.id.widget_like, if (now.starred) UiR.drawable.ic_heart else UiR.drawable.ic_heart_outline)
                setContentDescription(R.id.widget_like, context.getString(if (now.starred) UiR.string.cd_unfavorite else UiR.string.cd_favorite))
                setOnClickPendingIntent(R.id.widget_like, WidgetActionReceiver.setStarred(context, now.songId, !now.starred))
            }
            // The clock runs by itself in the launcher; only a pause, seek or song change needs a new base.
            setChronometer(R.id.widget_elapsed, (now?.positionAtMs ?: SystemClock.elapsedRealtime()) - (now?.positionMs ?: 0), null, now?.isPlaying == true)
            setTextViewText(R.id.widget_duration, formatTime(now?.durationMs ?: 0))
            setImageViewBitmap(R.id.widget_bars, ProgressBars.render(context, barsWidthDp(manager, id), now?.progress(PROGRESS_BARS) ?: 0))
        }

    private fun RemoteViews.controls(context: Context, now: NowPlaying?, withSkip: Boolean) {
        val playing = now?.isPlaying == true
        setImageViewResource(R.id.widget_play_pause, if (playing) UiR.drawable.ic_pause else UiR.drawable.ic_play)
        setContentDescription(R.id.widget_play_pause, context.getString(if (playing) UiR.string.cd_pause else UiR.string.cd_play))
        setOnClickPendingIntent(R.id.widget_play_pause, playPause(context))
        if (withSkip) {
            setOnClickPendingIntent(R.id.widget_previous, WidgetActionReceiver.previous(context))
            setOnClickPendingIntent(R.id.widget_next, WidgetActionReceiver.next(context))
        }
        setOnClickPendingIntent(android.R.id.background, openApp(context))
    }

    /** Shows the Doto [title] in place of the plain title TextView; keeps the text if there's no image. */
    private fun RemoteViews.withDotTitle(title: Bitmap?) = apply {
        if (title == null) return@apply
        setImageViewBitmap(R.id.widget_title_image, title)
        setViewVisibility(R.id.widget_title_image, View.VISIBLE)
        setViewVisibility(R.id.widget_title, View.GONE)
    }

    private fun RemoteViews.withDots(dots: Int) = apply {
        DOT_IDS.forEachIndexed { i, id ->
            setImageViewResource(id, if (i < dots) R.drawable.widget_dot_on else R.drawable.widget_dot_off)
        }
    }

    /** The waveform's share of the card: width minus paddings, the two times and their gaps. */
    private fun barsWidthDp(manager: AppWidgetManager, id: Int) = DotText.sizeDp(manager, id, fallback = 320 to 150).first - 130

    /** The logo in its black, outlined circle, as in the design's card header. */
    private fun logo(context: Context): Bitmap = logo ?: run {
        val density = context.resources.displayMetrics.density
        val size = (24 * density).toInt()
        createBitmap(size, size).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val half = size / 2f
            paint.color = 0xFF000000.toInt()
            canvas.drawCircle(half, half, half, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = 0xFF333333.toInt()
            canvas.drawCircle(half, half, half - density / 2, paint)
            val mark = dotwaveLogoBitmap((14 * density).toInt())
            canvas.drawBitmap(mark, (size - mark.width) / 2f, (size - mark.height) / 2f, null)
        }.also { logo = it }
    }

    private fun openApp(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: Intent()
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Through Media3's media button receiver, like a headset button: it starts the service if needed, and "play"
     * then resumes the saved queue. Next/previous can't go this way (Media3 only starts the service for "play").
     */
    private fun playPause(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(context, MediaButtonReceiver::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private inline fun <reified T> AppWidgetManager.idsOf(context: Context): IntArray? =
        getAppWidgetIds(ComponentName(context, T::class.java)).takeIf { it.isNotEmpty() }
}

/** The media card's progress: a row of bars of a fixed, wave-like height pattern, the played ones in red. */
private object ProgressBars {
    fun render(context: Context, widthDp: Int, lit: Int): Bitmap {
        val metrics = context.resources.displayMetrics
        fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
        val width = dp(widthDp.coerceAtLeast(PROGRESS_BARS * 3).toFloat())
        val height = dp(22f)
        val gap = dp(3f)
        val barWidth = (width - gap * (PROGRESS_BARS - 1)) / PROGRESS_BARS
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        return createBitmap(width.toInt(), height.toInt()).also { bitmap ->
            val canvas = Canvas(bitmap)
            for (i in 0 until PROGRESS_BARS) {
                val barHeight = dp(min(22f, 5f + abs(sin(i * 0.9f) * 9f + sin(i * 0.37f) * 7f)))
                val left = i * (barWidth + gap)
                rect.set(left, (height - barHeight) / 2, left + barWidth, (height + barHeight) / 2)
                paint.color = if (i < lit) ACCENT else 0xFF3A3A3A.toInt()
                canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), paint)
            }
        }
    }
}
