package dev.gruebling.navi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.gruebling.navi.core.database.entity.ArtistEntity
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
}
