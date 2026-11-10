package dev.gruebling.navi.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dev.gruebling.navi.core.data.mapping.toDomain
import dev.gruebling.navi.core.data.mapping.toEntity
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.dao.AlbumDao
import dev.gruebling.navi.core.database.dao.SongDao
import dev.gruebling.navi.core.model.Album
import dev.gruebling.navi.core.model.AlbumDetail
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.AlbumListType
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class AlbumSort { RecentlyAdded, Name, Artist }

@Singleton
class AlbumRepository @Inject constructor(
    private val api: SubsonicApi,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
    private val clock: Clock,
) {
    fun observeAlbum(id: AlbumId): Flow<AlbumDetail?> =
        combine(albumDao.observe(id.value), songDao.observeByAlbum(id.value)) { album, songs ->
            album?.let { AlbumDetail(it.toDomain(), songs.map { s -> s.toDomain() }) }
        }.distinctUntilChanged()

    suspend fun refreshAlbum(id: AlbumId): Result<Unit> = suspendRunCatching {
        val remote = api.getAlbum(id.value).ok().album ?: error("Album ${id.value} missing in response")
        val syncedAt = albumDao.get(id.value)?.syncedAt
        albumDao.upsert(listOf(remote.toEntity(syncedAt)))
        songDao.upsert(remote.song.map { it.toEntity() })
    }

    /** Home sections (recently played / added, most played). */
    fun observeAlbumList(type: AlbumListType): Flow<List<Album>> =
        albumDao.observeList(type.apiValue).map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    suspend fun refreshAlbumList(type: AlbumListType, size: Int = 20): Result<Unit> = suspendRunCatching {
        val remote = api.getAlbumList2(type.apiValue, size = size).ok().albumList2?.album.orEmpty()
        val existing = albumDao.getAll(remote.map { it.id }).associateBy({ it.id }, { it.syncedAt })
        albumDao.replaceList(type.apiValue, remote.map { it.toEntity(existing[it.id]) })
    }

    /** The whole library, paged from Room (sorting and scrolling never wait for the network). */
    fun pagedAlbums(sort: AlbumSort): Flow<PagingData<Album>> =
        Pager(PagingConfig(pageSize = 60, prefetchDistance = 60, enablePlaceholders = true)) {
            when (sort) {
                AlbumSort.Name -> albumDao.pagingByName()
                AlbumSort.RecentlyAdded -> albumDao.pagingByAdded()
                AlbumSort.Artist -> albumDao.pagingByArtist()
            }
        }.flow.map { page -> page.map { it.toDomain() } }

    /**
     * Pulls the full album index (500 per request) into Room and removes albums the server no longer has.
     * Returns the number of albums.
     */
    suspend fun syncLibrary(pageSize: Int = 500): Result<Int> = suspendRunCatching {
        val started = clock.now().toEpochMilliseconds()
        var offset = 0
        while (true) {
            val page = api.getAlbumList2("alphabeticalByName", size = pageSize, offset = offset).ok()
                .albumList2?.album.orEmpty()
            albumDao.upsert(page.map { it.toEntity(syncedAt = started) })
            offset += page.size
            if (page.size < pageSize) break
        }
        albumDao.deleteNotSyncedSince(started)
        offset
    }
}
