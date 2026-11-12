package dev.gruebling.navi.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.data.repository.LyricsRepository
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.playback.PlaybackController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only remote control (no UI yet). Actions:
 *   navi.debug.PLAY_PLAYLIST --es name "FM4 Charts" [--ez shuffle true] [--ei index 3]
 *   navi.debug.TOGGLE | NEXT | PREVIOUS | SEEK --el ms 60000 | STATE
 *   navi.debug.LYRICS_SCAN --es name "FM4 Charts"   (logs which songs of the playlist have lyrics)
 */
@AndroidEntryPoint
class DebugPlaybackReceiver : BroadcastReceiver() {
    @Inject lateinit var playback: PlaybackController
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var lyrics: LyricsRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        // MediaController must be used on the main thread.
        scope.launch(Dispatchers.Main) {
            try {
                handle(intent)
            } catch (e: Exception) {
                Log.w(TAG, "command failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(intent: Intent) {
        playback.connect()
        when (intent.action) {
            "navi.debug.PLAY_PLAYLIST" -> {
                val name = intent.getStringExtra("name") ?: return
                val playlist = playlists.observePlaylists().first().firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: return Log.w(TAG, "no playlist '$name'").let { }
                playlists.refreshPlaylist(playlist.id)
                val songs = playlists.observePlaylist(playlist.id).first()?.songs.orEmpty()
                Log.i(TAG, "playing '${playlist.name}' (${songs.size} songs)")
                playback.playSongs(songs, startIndex = intent.getIntExtra("index", 0), shuffle = intent.getBooleanExtra("shuffle", false))
            }
            "navi.debug.LYRICS_SCAN" -> {
                val name = intent.getStringExtra("name") ?: return
                val playlist = playlists.observePlaylists().first().firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: return Log.w(TAG, "no playlist '$name'").let { }
                playlists.refreshPlaylist(playlist.id)
                val songs = playlists.observePlaylist(playlist.id).first()?.songs.orEmpty()
                songs.forEachIndexed { index, song ->
                    val result = lyrics.lyrics(song.id, song.artist, song.title)
                    val found = result.getOrNull()
                    val summary = when {
                        result.isFailure -> "error ${result.exceptionOrNull()?.message}"
                        found == null -> "none"
                        else -> "${if (found.synced) "synced" else "plain"} ${found.lines.size} lines"
                    }
                    Log.i(TAG, "lyrics #$index ${song.artist} – ${song.title}: $summary")
                }
            }
            "navi.debug.TOGGLE" -> playback.togglePlayPause()
            "navi.debug.NEXT" -> playback.next()
            "navi.debug.PREVIOUS" -> playback.previous()
            "navi.debug.SEEK" -> playback.seekTo(intent.getLongExtra("ms", 0))
            "navi.debug.STATE" -> {
                val s = playback.state.value
                val (volume, gain) = playback.levelling()
                Log.i(
                    TAG,
                    "state: playing=${s.isPlaying} buffering=${s.isBuffering} index=${s.currentIndex}/${s.queue.size} " +
                        "current=${s.current?.artist} – ${s.current?.title} pos=${playback.positionMs()}/${s.durationMs} " +
                        "shuffle=${s.shuffle} repeat=${s.repeat} error=${s.error} volume=$volume gain=$gain",
                )
            }
        }
    }

    private companion object { const val TAG = "NaviDebug" }
}
