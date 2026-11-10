package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.toDomain
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.database.dao.AlbumDao
import dev.gruebling.navi.core.database.dao.ArtistDao
import dev.gruebling.navi.core.database.dao.SongDao
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.ArtistId
import dev.gruebling.navi.core.model.Favorites
import dev.gruebling.navi.core.model.SongId
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Starred songs, albums and artists. Starring is optimistic: Room first, server second, rollback on failure. */
@Singleton
class FavoritesRepository @Inject constructor(
    private val api: SubsonicApi,
    private val db: NaviDatabase,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val clock: Clock,
) {
    fun observeFavorites(): Flow<Favorites> =
        combine(songDao.observeStarred(), albumDao.observeStarred(), artistDao.observeStarred()) { s, al, ar ->
            Favorites(s.map { it.toDomain() }, al.map { it.toDomain() }, ar.map { it.toDomain() })
        }.distinctUntilChanged()

    suspend fun refreshFavorites(): Result<Unit> = suspendRunCatching {
        val starred = api.getStarred2().ok().starred2 ?: return@suspendRunCatching
        // Everything in getStarred2 is starred, even if a server leaves out the date.
        val now = clock.now().toEpochMilliseconds()
        db.withTransaction {
            songDao.clearAllStarred()
            albumDao.clearAllStarred()
            artistDao.clearAllStarred()
            songDao.upsert(starred.song.map { s -> s.toEntity().let { it.copy(starredAt = it.starredAt ?: now) } })
            val known = albumDao.getAll(starred.album.map { it.id }).associateBy({ it.id }, { it.syncedAt })
            albumDao.upsert(starred.album.map { a -> a.toEntity(known[a.id]).let { it.copy(starredAt = it.starredAt ?: now) } })
            artistDao.upsert(starred.artist.map { a -> a.toEntity().let { it.copy(starredAt = it.starredAt ?: now) } })
        }
    }

    suspend fun setStarred(id: SongId, starred: Boolean): Result<Unit> = optimistic(
        read = { songDao.starredAt(id.value) },
        write = { songDao.setStarredAt(id.value, it) },
        starred = starred,
        call = { if (starred) api.star(songIds = listOf(id.value)) else api.unstar(songIds = listOf(id.value)) },
    )

    suspend fun setStarred(id: AlbumId, starred: Boolean): Result<Unit> = optimistic(
        read = { albumDao.starredAt(id.value) },
        write = { albumDao.setStarredAt(id.value, it) },
        starred = starred,
        call = { if (starred) api.star(albumIds = listOf(id.value)) else api.unstar(albumIds = listOf(id.value)) },
    )

    suspend fun setStarred(id: ArtistId, starred: Boolean): Result<Unit> = optimistic(
        read = { artistDao.starredAt(id.value) },
        write = { artistDao.setStarredAt(id.value, it) },
        starred = starred,
        call = { if (starred) api.star(artistIds = listOf(id.value)) else api.unstar(artistIds = listOf(id.value)) },
    )

    private suspend inline fun optimistic(
        read: () -> Long?,
        crossinline write: suspend (Long?) -> Unit,
        starred: Boolean,
        crossinline call: suspend () -> dev.gruebling.navi.core.network.model.SubsonicEnvelope,
    ): Result<Unit> {
        val previous = read()
        write(if (starred) clock.now().toEpochMilliseconds() else null)
        return suspendRunCatching { call().ok(); Unit }
            .onFailure { write(previous) }
    }
}
