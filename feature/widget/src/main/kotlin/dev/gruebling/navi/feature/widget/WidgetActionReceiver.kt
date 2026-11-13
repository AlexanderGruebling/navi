package dev.gruebling.navi.feature.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.data.session.SessionState
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.playback.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The widget buttons that Media3's media button receiver can't handle: next, previous, and playing a playlist.
 * Commands go through [PlaybackController], which connects to (and if needed starts) the playback service.
 */
@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {
    @Inject lateinit var playback: PlaybackController
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var session: SessionManager
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> playback.next()
            ACTION_PREVIOUS -> playback.previous()
            ACTION_PLAY_PLAYLIST -> {
                val id = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: return
                val pending = goAsync()
                scope.launch(Dispatchers.Main) {
                    try {
                        // goAsync gives us 10 s; a playlist that isn't cached yet has to come from the server.
                        withTimeoutOrNull(9_000) { playPlaylist(PlaylistId(id)) } ?: Log.w(TAG, "playlist $id took too long")
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun playPlaylist(id: PlaylistId) {
        var songs = playlists.observePlaylist(id).first()?.songs.orEmpty()
        if (songs.isEmpty()) {
            session.state.first { it != SessionState.Loading } // the login, in a process the widget just started
            playlists.refreshPlaylist(id)
            songs = playlists.observePlaylist(id).first()?.songs.orEmpty()
        }
        if (songs.isNotEmpty()) playback.playSongs(songs)
    }

    internal companion object {
        private const val TAG = "NaviWidget"
        private const val ACTION_NEXT = "dev.gruebling.navi.widget.NEXT"
        private const val ACTION_PREVIOUS = "dev.gruebling.navi.widget.PREVIOUS"
        private const val ACTION_PLAY_PLAYLIST = "dev.gruebling.navi.widget.PLAY_PLAYLIST"
        private const val EXTRA_PLAYLIST_ID = "playlist_id"

        fun next(context: Context) = broadcast(context, ACTION_NEXT, requestCode = 1)

        fun previous(context: Context) = broadcast(context, ACTION_PREVIOUS, requestCode = 2)

        /** One PendingIntent per playlist (the request code), so widgets for different playlists don't share extras. */
        fun playPlaylist(context: Context, playlistId: String) =
            broadcast(context, ACTION_PLAY_PLAYLIST, requestCode = playlistId.hashCode()) { putExtra(EXTRA_PLAYLIST_ID, playlistId) }

        private fun broadcast(context: Context, action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action).apply(extras)
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
