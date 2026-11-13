package dev.gruebling.navi.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.Playlist
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Which playlist each playlist widget plays. Kept in its own small preferences file, not in Room, which is only a
 * cache; the name is stored too, so a widget still has a label when the playlist isn't cached.
 */
@Singleton
class PlaylistWidgets @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlists: PlaylistRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("playlist_widgets", Context.MODE_PRIVATE)

    fun choose(widgetId: Int, playlist: Playlist) {
        prefs.edit { putString(key(widgetId), "${playlist.id.value}\t${playlist.name}") }
        update(intArrayOf(widgetId))
    }

    fun forget(widgetIds: IntArray) = prefs.edit { widgetIds.forEach { remove(key(it)) } }

    /** Redraws [widgetIds] with the playlists' current name and song count from the cache. */
    fun update(widgetIds: IntArray) {
        scope.launch {
            val cached = playlists.observePlaylists().first().associateBy { it.id.value }
            val manager = AppWidgetManager.getInstance(context)
            widgetIds.forEach { widgetId ->
                val (playlistId, name) = prefs.getString(key(widgetId), null)?.split('\t', limit = 2)
                    ?.let { it[0] to it.getOrNull(1) } ?: (null to null)
                manager.updateAppWidget(widgetId, WidgetViews.playlist(context, widgetId, playlistId, name, cached[playlistId]))
            }
        }
    }

    private fun key(widgetId: Int) = "widget_$widgetId"
}
