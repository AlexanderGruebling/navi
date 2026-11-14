package dev.gruebling.navi.feature.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.data.repository.FavoritesRepository
import dev.gruebling.navi.core.model.SongId
import dev.gruebling.navi.core.playback.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The widget buttons that Media3's media button receiver can't handle: next, previous and the favorite heart.
 * Playback commands go through [PlaybackController], which connects to (and if needed starts) the playback service.
 */
@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {
    @Inject lateinit var playback: PlaybackController
    @Inject lateinit var favorites: FavoritesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> playback.next()
            ACTION_PREVIOUS -> playback.previous()
            ACTION_STAR -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID) ?: return
                val starred = intent.getBooleanExtra(EXTRA_STARRED, true)
                val pending = goAsync()
                // Optimistic in the database (the heart flips right away), then sent to the server.
                scope.launch {
                    try {
                        favorites.setStarred(SongId(songId), starred)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    internal companion object {
        private const val ACTION_NEXT = "dev.gruebling.navi.widget.NEXT"
        private const val ACTION_PREVIOUS = "dev.gruebling.navi.widget.PREVIOUS"
        private const val ACTION_STAR = "dev.gruebling.navi.widget.STAR"
        private const val EXTRA_SONG_ID = "song_id"
        private const val EXTRA_STARRED = "starred"

        fun next(context: Context) = broadcast(context, ACTION_NEXT, requestCode = 1)

        fun previous(context: Context) = broadcast(context, ACTION_PREVIOUS, requestCode = 2)

        /** Stars or unstars [songId]; the extras are replaced on every redraw, so the heart always does the right thing. */
        fun setStarred(context: Context, songId: String, starred: Boolean) =
            broadcast(context, ACTION_STAR, requestCode = 3) {
                putExtra(EXTRA_SONG_ID, songId)
                putExtra(EXTRA_STARRED, starred)
            }

        private fun broadcast(context: Context, action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action).apply(extras)
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
