package dev.gruebling.navi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.gruebling.navi.core.database.entity.ArtistEntity
import dev.gruebling.navi.core.database.entity.ArtistInfoEntity
import dev.gruebling.navi.core.database.entity.ArtistSimilarEntity
import dev.gruebling.navi.core.database.entity.ArtistTopSongEntity
import dev.gruebling.navi.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Upsert suspend fun upsert(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists ORDER BY sortName")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observe(id: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE starredAt IS NOT NULL ORDER BY starredAt DESC")
    fun observeStarred(): Flow<List<ArtistEntity>>

    @Query("SELECT starredAt FROM artists WHERE id = :id")
    suspend fun starredAt(id: String): Long?

    @Query("UPDATE artists SET starredAt = :starredAt WHERE id = :id")
    suspend fun setStarredAt(id: String, starredAt: Long?)

    @Query("UPDATE artists SET starredAt = NULL WHERE starredAt IS NOT NULL")
    suspend fun clearAllStarred()

    @Query("DELETE FROM artists")
    suspend fun deleteAll()

    /** getArtists returns the complete artist index (including starred dates), so the table is replaced. */
    @Transaction
    suspend fun replaceAll(artists: List<ArtistEntity>) {
        deleteAll()
        upsert(artists)
    }

    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY sortName LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<ArtistEntity>

    /** Callers must keep [ids] under SQLite's variable limit (999 on older Android). */
    @Query("SELECT * FROM artists WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<ArtistEntity>

    // --- getArtistInfo2 / getTopSongs extras ---

    @Query("SELECT * FROM artist_info WHERE artistId = :id")
    fun observeInfo(id: String): Flow<ArtistInfoEntity?>

    @Query(
        "SELECT a.* FROM artist_similar s JOIN artists a ON a.id = s.similarId WHERE s.artistId = :id ORDER BY s.position",
    )
    fun observeSimilar(id: String): Flow<List<ArtistEntity>>

    @Query(
        "SELECT s.* FROM artist_top_songs t JOIN songs s ON s.id = t.songId WHERE t.artistId = :id ORDER BY t.position",
    )
    fun observeTopSongs(id: String): Flow<List<SongEntity>>

    @Upsert suspend fun upsertInfo(info: ArtistInfoEntity)

    @Query("DELETE FROM artist_similar WHERE artistId = :id")
    suspend fun clearSimilar(id: String)

    @Upsert suspend fun upsertSimilar(entries: List<ArtistSimilarEntity>)

    @Query("DELETE FROM artist_top_songs WHERE artistId = :id")
    suspend fun clearTopSongs(id: String)

    @Upsert suspend fun upsertTopSongs(entries: List<ArtistTopSongEntity>)

    @Transaction
    suspend fun replaceInfo(info: ArtistInfoEntity, similarIds: List<String>) {
        upsertInfo(info)
        clearSimilar(info.artistId)
        upsertSimilar(similarIds.mapIndexed { i, similarId -> ArtistSimilarEntity(info.artistId, i, similarId) })
    }

    @Transaction
    suspend fun replaceTopSongs(artistId: String, songIds: List<String>) {
        clearTopSongs(artistId)
        upsertTopSongs(songIds.mapIndexed { i, songId -> ArtistTopSongEntity(artistId, i, songId) })
    }
}
