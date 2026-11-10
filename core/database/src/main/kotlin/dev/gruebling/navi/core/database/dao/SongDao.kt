package dev.gruebling.navi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.gruebling.navi.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Upsert suspend fun upsert(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY IFNULL(disc, 1), IFNULL(track, 0), title")
    fun observeByAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE starredAt IS NOT NULL ORDER BY starredAt DESC")
    fun observeStarred(): Flow<List<SongEntity>>

    @Query("SELECT starredAt FROM songs WHERE id = :id")
    suspend fun starredAt(id: String): Long?

    @Query("UPDATE songs SET starredAt = :starredAt WHERE id = :id")
    suspend fun setStarredAt(id: String, starredAt: Long?)

    @Query("UPDATE songs SET starredAt = NULL WHERE starredAt IS NOT NULL")
    suspend fun clearAllStarred()

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' ORDER BY playCount DESC, title LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<SongEntity>
}
