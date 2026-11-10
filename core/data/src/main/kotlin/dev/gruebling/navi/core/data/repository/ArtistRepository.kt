package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.toDomain
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.dao.AlbumDao
import dev.gruebling.navi.core.database.dao.ArtistDao
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.model.ArtistDetail
import dev.gruebling.navi.core.model.ArtistId
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class ArtistRepository @Inject constructor(
    private val api: SubsonicApi,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
) {
    fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeAll().map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    suspend fun refreshArtists(): Result<Unit> = suspendRunCatching {
        val index = api.getArtists().ok().artists?.index.orEmpty()
        artistDao.replaceAll(index.flatMap { group -> group.artist.map { it.toEntity(indexLetter = group.name) } })
    }

    fun observeArtist(id: ArtistId): Flow<ArtistDetail?> =
        combine(artistDao.observe(id.value), albumDao.observeByArtist(id.value)) { artist, albums ->
            artist?.let { ArtistDetail(it.toDomain(), albums.map { a -> a.toDomain() }) }
        }.distinctUntilChanged()

    suspend fun refreshArtist(id: ArtistId): Result<Unit> = suspendRunCatching {
        val remote = api.getArtist(id.value).ok().artist ?: error("Artist ${id.value} missing in response")
        artistDao.upsert(listOf(remote.toEntity()))
        val existing = albumDao.getAll(remote.album.map { it.id }).associateBy({ it.id }, { it.syncedAt })
        albumDao.upsert(remote.album.map { it.toEntity(existing[it.id]) })
    }
}
