package dev.gruebling.navi

import android.util.Log
import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.data.repository.ArtistRepository
import dev.gruebling.navi.core.data.repository.FavoritesRepository
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.AlbumListType
import javax.inject.Inject
import kotlin.time.measureTimedValue
import kotlinx.coroutines.flow.first

/**
 * Debug builds only: runs the repositories against the real server and logs the result to logcat under the tag
 * "NaviProbe" (counts, timings, playlist grouping). Logs no credentials. Removed once the real screens exist.
 */
class DebugServerProbe @Inject constructor(
    private val playlists: PlaylistRepository,
    private val albums: AlbumRepository,
    private val artists: ArtistRepository,
    private val favorites: FavoritesRepository,
) {
    suspend fun run() {
        step("playlists") { playlists.refreshPlaylists() }
        playlists.observePlaylists().first().forEach {
            Log.i(TAG, "  [${it.group}] ${it.name} smart=${it.isSmart} songs=${it.songCount} new=${it.hasNewContent} pinned=${it.pinned}")
        }
        AlbumListType.entries.forEach { type -> step("albumList $type") { albums.refreshAlbumList(type) } }
        AlbumListType.entries.forEach { type ->
            Log.i(TAG, "  $type: " + albums.observeAlbumList(type).first().take(5).joinToString { "${it.artist} – ${it.name}" })
        }
        step("library sync") { albums.syncLibrary().map { Log.i(TAG, "  albums: $it") } }
        step("artists") { artists.refreshArtists() }
        Log.i(TAG, "  artists: ${artists.observeArtists().first().size}")
        step("favorites") { favorites.refreshFavorites() }
        favorites.observeFavorites().first().let {
            Log.i(TAG, "  starred: ${it.songs.size} songs, ${it.albums.size} albums, ${it.artists.size} artists")
        }
    }

    private suspend fun step(name: String, block: suspend () -> Result<*>) {
        val (result, time) = measureTimedValue { block() }
        result.onSuccess { Log.i(TAG, "$name ok in $time") }.onFailure { Log.w(TAG, "$name FAILED after $time", it) }
    }

    private companion object { const val TAG = "NaviProbe" }
}
