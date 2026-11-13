package dev.gruebling.navi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.gruebling.navi.core.database.entity.PlaylistEntity
import dev.gruebling.navi.core.database.entity.PlaylistPrefsEntity
import dev.gruebling.navi.core.database.entity.PlaylistSongEntity
import dev.gruebling.navi.core.database.entity.PlaylistWithPrefs
import dev.gruebling.navi.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.*, pr.seenSignature
        FROM playlists p LEFT JOIN playlist_prefs pr ON pr.playlistId = p.id
        ORDER BY p.position
        """,
    )
    fun observeAll(): Flow<List<PlaylistWithPrefs>>

    @Query(
        """
        SELECT p.*, pr.seenSignature
        FROM playlists p LEFT JOIN playlist_prefs pr ON pr.playlistId = p.id
        WHERE p.id = :id
        """,
    )
    fun observe(id: String): Flow<PlaylistWithPrefs?>

    @Query("SELECT * FROM playlists")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Query(
        "SELECT s.* FROM playlist_songs ps JOIN songs s ON s.id = ps.songId WHERE ps.playlistId = :id ORDER BY ps.position",
    )
    fun observeSongs(id: String): Flow<List<SongEntity>>

    @Query(
        """
        SELECT s.coverArt FROM playlist_songs ps JOIN songs s ON s.id = ps.songId
        WHERE ps.playlistId = :id AND s.coverArt IS NOT NULL
        GROUP BY s.coverArt ORDER BY MIN(ps.position) LIMIT 4
        """,
    )
    fun observeMosaic(id: String): Flow<List<String>>

    @Upsert suspend fun upsert(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearSongs(id: String)

    @Upsert suspend fun upsertSongs(entries: List<PlaylistSongEntity>)

    @Query("UPDATE playlists SET contentSignature = :signature, songCount = :songCount WHERE id = :id")
    suspend fun setContent(id: String, signature: String, songCount: Int)

    // --- local prefs ---

    @Query("SELECT * FROM playlist_prefs WHERE playlistId = :id")
    suspend fun prefs(id: String): PlaylistPrefsEntity?

    @Upsert suspend fun upsertPrefs(prefs: PlaylistPrefsEntity)

    /** For taking over the old playlist settings once (see [PlaylistPrefsEntity]). */
    @Query("SELECT * FROM playlist_prefs")
    suspend fun allPrefs(): List<PlaylistPrefsEntity>

    @Query("DELETE FROM playlist_prefs WHERE playlistId = :id")
    suspend fun deletePrefs(id: String)

    /** Replaces the playlist list; local prefs of deleted playlists go too. */
    @Transaction
    suspend fun replaceAll(playlists: List<PlaylistEntity>) {
        val keep = playlists.mapTo(HashSet()) { it.id }
        getAll().filter { it.id !in keep }.forEach {
            delete(it.id)
            clearSongs(it.id)
            deletePrefs(it.id)
        }
        upsert(playlists)
    }

    @Transaction
    suspend fun replaceSongs(id: String, songIds: List<String>, signature: String) {
        clearSongs(id)
        upsertSongs(songIds.mapIndexed { i, songId -> PlaylistSongEntity(id, i, songId) })
        setContent(id, signature, songIds.size)
    }
}
