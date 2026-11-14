package dev.gruebling.navi.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Base of the player widgets: all of them are drawn by [NowPlayingWidgets], from the same state. */
abstract class NowPlayingWidget : AppWidgetProvider() {
    @Inject lateinit var nowPlaying: NowPlayingWidgets

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = nowPlaying.refresh()

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = nowPlaying.refresh()
}

/** 4×2 player with the dot-matrix title. */
@AndroidEntryPoint
class PlayerWidget : NowPlayingWidget()

/** 2×2 light player with progress dots. */
@AndroidEntryPoint
class PlayerSmallWidget : NowPlayingWidget()

/** 4×2 media card: logo and server, cover, controls, favorite, waveform progress. */
@AndroidEntryPoint
class MediaCardWidget : NowPlayingWidget()
