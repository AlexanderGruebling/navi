package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.toDomain
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.dao.SongDao
import dev.gruebling.navi.core.model.Song
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import javax.inject.Inject
import javax.inject.Singleton

/** Instant mixes: songs similar to a song or artist, from the server's getSimilarSongs (Last.fm via Navidrome). */
@Singleton
class MixRepository @Inject constructor(
    private val api: SubsonicApi,
    private val songDao: SongDao,
) {
    /**
     * Songs similar to [seedId] (a song or artist id), without the seed itself. When a song has no similar songs of
     * its own, [fallbackArtistId] (usually the song's artist) is asked instead. Results go into the song cache too.
     */
    suspend fun similarSongs(seedId: String, fallbackArtistId: String? = null, count: Int = 50): Result<List<Song>> =
        suspendRunCatching {
            var songs = fetch(seedId, count)
            if (songs.isEmpty() && fallbackArtistId != null && fallbackArtistId != seedId) {
                songs = fetch(fallbackArtistId, count)
            }
            songs.filter { it.id != seedId }.distinctBy { it.id }.also { list ->
                songDao.upsert(list.map { it.toEntity() })
            }.map { it.toEntity().toDomain() }
        }

    private suspend fun fetch(id: String, count: Int) = api.getSimilarSongs(id, count).ok().similarSongs?.song.orEmpty()
}
