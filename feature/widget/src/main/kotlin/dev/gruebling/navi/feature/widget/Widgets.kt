package dev.gruebling.navi.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 4×2 player. All player widgets are drawn by [NowPlayingWidgets]. */
@AndroidEntryPoint
class PlayerWidget : AppWidgetProvider() {
    @Inject lateinit var nowPlaying: NowPlayingWidgets

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = nowPlaying.refresh()

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = nowPlaying.refresh()
}

/** 2×2 light player. */
@AndroidEntryPoint
class PlayerSmallWidget : AppWidgetProvider() {
    @Inject lateinit var nowPlaying: NowPlayingWidgets

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = nowPlaying.refresh()

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = nowPlaying.refresh()
}

/** 4×1 pinned playlist; which one is picked in [PlaylistWidgetConfigActivity]. */
@AndroidEntryPoint
class PlaylistWidget : AppWidgetProvider() {
    @Inject lateinit var playlists: PlaylistWidgets

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = playlists.update(ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        playlists.update(intArrayOf(id))

    override fun onDeleted(context: Context, ids: IntArray) = playlists.forget(ids)
}
