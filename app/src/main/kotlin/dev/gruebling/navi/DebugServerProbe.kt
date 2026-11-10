package dev.gruebling.navi

import android.util.Log
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import javax.inject.Inject

/**
 * Debug builds only: logs what the server reports (extensions, playlist fields) to logcat under the tag "NaviProbe",
 * so the playlist grouping can be checked against real data. Logs no credentials.
 */
class DebugServerProbe @Inject constructor(private val api: SubsonicApi) {
    suspend fun run() {
        runCatching {
            val ping = api.ping().ok()
            Log.i(TAG, "server type=${ping.type} version=${ping.serverVersion} api=${ping.version} openSubsonic=${ping.openSubsonic}")
            val extensions = api.getOpenSubsonicExtensions().ok().openSubsonicExtensions.orEmpty()
            Log.i(TAG, "extensions: " + extensions.joinToString { "${it.name}${it.versions}" })
            val playlists = api.getPlaylists().ok().playlists?.playlist.orEmpty()
            Log.i(TAG, "playlists: ${playlists.size}")
            playlists.forEach { p ->
                Log.i(
                    TAG,
                    "playlist name=\"${p.name}\" owner=${p.owner} public=${p.public} readonly=${p.readonly} " +
                        "validUntil=${p.validUntil} changed=${p.changed} songs=${p.songCount} " +
                        "coverArt=${p.coverArt != null} comment=\"${p.comment?.take(60)}\"",
                )
            }
        }.onFailure { Log.w(TAG, "probe failed", it) }
    }

    private companion object { const val TAG = "NaviProbe" }
}
