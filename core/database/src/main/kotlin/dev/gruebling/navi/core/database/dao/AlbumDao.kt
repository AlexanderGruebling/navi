package dev.gruebling.navi.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.gruebling.navi.core.database.entity.AlbumEntity
import dev.gruebling.navi.core.database.entity.AlbumListEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Upsert suspend fun upsert(albums: List<AlbumEntity>)

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observe(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun get(id: String): AlbumEntity?

    /** Any number of [ids]: asks in chunks, below SQLite's 999-variable limit on Android 11 and older. */
    suspend fun getAll(ids: List<String>): List<AlbumEntity> = ids.chunked(MAX_IN_LIST).flatMap { getChunk(it) }

    @Query("SELECT * FROM albums WHERE id IN (:ids)")
    suspend fun getChunk(ids: List<String>): List<AlbumEntity>

    @Query("SELECT * FROM albums ORDER BY sortName")
    fun pagingByName(): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums ORDER BY created DESC")
    fun pagingByAdded(): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums ORDER BY artist IS NULL, artist COLLATE NOCASE, IFNULL(year, 9999), sortName")
    fun pagingByArtist(): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY IFNULL(year, 9999), sortName")
    fun observeByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE starredAt IS NOT NULL ORDER BY starredAt DESC")
    fun observeStarred(): Flow<List<AlbumEntity>>

    @Query("SELECT starredAt FROM albums WHERE id = :id")
    suspend fun starredAt(id: String): Long?

    @Query("UPDATE albums SET starredAt = :starredAt WHERE id = :id")
    suspend fun setStarredAt(id: String, starredAt: Long?)

    @Query("UPDATE albums SET starredAt = NULL WHERE starredAt IS NOT NULL")
    suspend fun clearAllStarred()

    @Query("DELETE FROM albums WHERE syncedAt IS NULL OR syncedAt < :syncStartedAt")
    suspend fun deleteNotSyncedSince(syncStartedAt: Long): Int

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun count(): Int

    @Query("SELECT * FROM albums WHERE name LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY playCount DESC, sortName LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<AlbumEntity>

    // --- Home sections ---

    @Query(
        "SELECT a.* FROM album_lists l JOIN albums a ON a.id = l.albumId WHERE l.type = :type ORDER BY l.position",
    )
    fun observeList(type: String): Flow<List<AlbumEntity>>

    @Query("DELETE FROM album_lists WHERE type = :type")
    suspend fun clearList(type: String)

    @Upsert suspend fun upsertListEntries(entries: List<AlbumListEntryEntity>)

    @Transaction
    suspend fun replaceList(type: String, albums: List<AlbumEntity>) {
        upsert(albums)
        clearList(type)
        upsertListEntries(albums.mapIndexed { i, a -> AlbumListEntryEntity(type, i, a.id) })
    }
}
