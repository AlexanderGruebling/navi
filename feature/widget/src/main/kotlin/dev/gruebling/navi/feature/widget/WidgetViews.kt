package dev.gruebling.navi.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.media3.session.MediaButtonReceiver
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.ui.R as UiR

/** What the player widgets show. null where a [NowPlaying] is expected = nothing in the queue. */
internal data class NowPlaying(
    val title: String,
    val artist: String?,
    val coverArt: String?,
    val isPlaying: Boolean,
    /** Lit progress dots of the small player, 0..[PROGRESS_DOTS]. */
    val dots: Int,
)

internal const val PROGRESS_DOTS = 12

private const val WHITE = 0xFFFFFFFF.toInt()

private val DOT_IDS = intArrayOf(
    R.id.widget_dot_0, R.id.widget_dot_1, R.id.widget_dot_2, R.id.widget_dot_3, R.id.widget_dot_4, R.id.widget_dot_5,
    R.id.widget_dot_6, R.id.widget_dot_7, R.id.widget_dot_8, R.id.widget_dot_9, R.id.widget_dot_10, R.id.widget_dot_11,
)

/** Builds the widgets' RemoteViews and hands them to the launcher. */
internal object WidgetViews {

    fun pushPlayers(context: Context, now: NowPlaying?, cover: Bitmap?) {
        val manager = AppWidgetManager.getInstance(context)
        // Per widget: the dot-matrix title is an image sized to that widget's width.
        manager.idsOf<PlayerWidget>(context)?.forEach { id ->
            val (width, height) = DotText.sizeDp(manager, id, fallback = 320 to 150)
            // Text column = widget - padding - square cover - gap.
            val title = DotText.render(context, now?.title ?: context.getString(UiR.string.app_name), 22f, width - height - 16, 2, WHITE)
            manager.updateAppWidget(id, player(context, R.layout.widget_player, now, cover).withDotTitle(R.id.widget_title, title))
        }
        manager.idsOf<PlayerSmallWidget>(context)?.let {
            manager.updateAppWidget(it, player(context, R.layout.widget_player_small, now, cover).withDots(now?.dots ?: 0))
        }
    }

    /** Only the dots of the small player; cheap enough to run every few seconds while playing. */
    fun pushProgress(context: Context, dots: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.idsOf<PlayerSmallWidget>(context) ?: return
        manager.partiallyUpdateAppWidget(ids, RemoteViews(context.packageName, R.layout.widget_player_small).withDots(dots))
    }

    fun playlist(context: Context, widgetId: Int, playlistId: String?, name: String?, playlist: Playlist?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_playlist).apply {
            val shownName = playlist?.name ?: name ?: context.getString(R.string.widget_playlist_label)
            setTextViewText(R.id.widget_playlist_name, shownName)
            val (width, _) = DotText.sizeDp(AppWidgetManager.getInstance(context), widgetId, fallback = 320 to 80)
            // Text column = widget - paddings - play button.
            withDotTitle(R.id.widget_playlist_name, DotText.render(context, shownName, 24f, width - 92, 1, WHITE))
            setTextViewText(
                R.id.widget_playlist_info,
                playlist?.let { context.resources.getQuantityString(UiR.plurals.song_count, it.songCount, it.songCount) }.orEmpty(),
            )
            setContentDescription(R.id.widget_playlist_play, context.getString(R.string.widget_play_playlist, shownName))
            setOnClickPendingIntent(android.R.id.background, openApp(context))
            if (playlistId != null) setOnClickPendingIntent(R.id.widget_playlist_play, WidgetActionReceiver.playPlaylist(context, playlistId))
        }

    private fun player(context: Context, @LayoutRes layout: Int, now: NowPlaying?, cover: Bitmap?) =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_title, now?.title ?: context.getString(UiR.string.app_name))
            setTextViewText(R.id.widget_artist, if (now == null) context.getString(R.string.widget_nothing_playing) else now.artist.orEmpty())
            if (layout == R.layout.widget_player) {
                setViewVisibility(R.id.widget_label, if (now != null) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.widget_previous, WidgetActionReceiver.previous(context))
                setOnClickPendingIntent(R.id.widget_next, WidgetActionReceiver.next(context))
            }
            if (cover != null) setImageViewBitmap(R.id.widget_cover, cover)
            val playing = now?.isPlaying == true
            setImageViewResource(R.id.widget_play_pause, if (playing) UiR.drawable.ic_pause else UiR.drawable.ic_play)
            setContentDescription(R.id.widget_play_pause, context.getString(if (playing) UiR.string.cd_pause else UiR.string.cd_play))
            setOnClickPendingIntent(R.id.widget_play_pause, playPause(context))
            setOnClickPendingIntent(android.R.id.background, openApp(context))
        }

    /** Shows [title] in place of the plain TextView [textId] (its "_image" sibling); keeps the text if there's no image. */
    private fun RemoteViews.withDotTitle(textId: Int, title: Bitmap?) = apply {
        if (title == null) return@apply
        val imageId = if (textId == R.id.widget_title) R.id.widget_title_image else R.id.widget_playlist_name_image
        setImageViewBitmap(imageId, title)
        setViewVisibility(imageId, View.VISIBLE)
        setViewVisibility(textId, View.GONE)
    }

    private fun RemoteViews.withDots(dots: Int) = apply {
        DOT_IDS.forEachIndexed { i, id ->
            setImageViewResource(id, if (i < dots) R.drawable.widget_dot_on else R.drawable.widget_dot_off)
        }
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

    private inline fun <reified T> AppWidgetManager.idsOf(context: Context): IntArray? =
        getAppWidgetIds(ComponentName(context, T::class.java)).takeIf { it.isNotEmpty() }
}
