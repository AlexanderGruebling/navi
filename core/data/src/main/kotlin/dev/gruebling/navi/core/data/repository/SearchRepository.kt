package dev.gruebling.navi.core.data.repository

import androidx.room.withTransaction
import dev.gruebling.navi.core.data.mapping.toDomain
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.database.dao.AlbumDao
import dev.gruebling.navi.core.database.dao.ArtistDao
import dev.gruebling.navi.core.database.dao.SongDao
import dev.gruebling.navi.core.model.SearchResults
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import javax.inject.Inject
import javax.inject.Singleton

/** Search: instant local results from the cache, then the server's search3 (whose results are cached too). */
@Singleton
class SearchRepository @Inject constructor(
    private val api: SubsonicApi,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val db: NaviDatabase,
) {
    suspend fun searchLocal(query: String): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults.Empty
        return SearchResults(
            artists = artistDao.search(q, 10).map { it.toDomain() },
            albums = albumDao.search(q, 20).map { it.toDomain() },
            songs = songDao.search(q, 30).map { it.toDomain() },
        )
    }

    suspend fun searchRemote(query: String): Result<SearchResults> = suspendRunCatching {
        val q = query.trim()
        if (q.isEmpty()) return@suspendRunCatching SearchResults.Empty
        val result = api.search3(q).ok().searchResult3 ?: return@suspendRunCatching SearchResults.Empty
        val artists = result.artist.map { it.toEntity() }
        val songs = result.song.map { it.toEntity() }
        // One transaction: screens observing the cache update once, not three times.
        val albums = db.withTransaction {
            val existing = albumDao.getAll(result.album.map { it.id }).associateBy({ it.id }, { it.syncedAt })
            result.album.map { it.toEntity(existing[it.id]) }.also { albums ->
                artistDao.upsert(artists)
                albumDao.upsert(albums)
                songDao.upsert(songs)
            }
        }
        SearchResults(artists.map { it.toDomain() }, albums.map { it.toDomain() }, songs.map { it.toDomain() })
    }
}
