package dev.gruebling.navi.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Dates are stored as epoch milliseconds. No foreign keys: endpoints deliver songs, albums and artists in any
// order and partially, and the cache must accept each piece as it arrives.

@Entity(tableName = "songs", indices = [Index("albumId"), Index("artistId"), Index("starredAt")])
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val albumId: String?,
    val album: String?,
    val artistId: String?,
    val artist: String?,
    val track: Int?,
    val disc: Int?,
    val duration: Int,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val starredAt: Long?,
    val suffix: String?,
    val contentType: String?,
    val bitRate: Int?,
    val size: Long?,
    val playCount: Long,
    val created: Long?,
)

@Entity(tableName = "albums", indices = [Index("sortName"), Index("artistId"), Index("starredAt")])
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortName: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val duration: Int,
    val year: Int?,
    val genre: String?,
    val starredAt: Long?,
    val created: Long?,
    val playCount: Long,
    /** Set by the full library sync; rows older than the latest sync were deleted on the server. */
    val syncedAt: Long?,
)

@Entity(tableName = "artists", indices = [Index("sortName"), Index("starredAt")])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortName: String,
    val indexLetter: String?,
    val coverArt: String?,
    val imageUrl: String?,
    val albumCount: Int,
    val starredAt: Long?,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val comment: String?,
    val owner: String?,
    val isPublic: Boolean,
    val readonly: Boolean,
    val songCount: Int,
    val duration: Int,
    val coverArt: String?,
    val created: Long?,
    val changed: Long?,
    /** Non-null = Navidrome smart playlist. */
    val validUntil: Long?,
    /** Position in the server's getPlaylists answer. */
    val position: Int,
    /** Hash of the ordered song ids from the last getPlaylist; null until the tracks were loaded once. */
    val contentSignature: String?,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("songId")],
)
data class PlaylistSongEntity(
    val playlistId: String,
    val position: Int,
    val songId: String,
)

/** Local-only settings. Separate table so server refreshes never overwrite them. */
@Entity(tableName = "playlist_prefs")
data class PlaylistPrefsEntity(
    @PrimaryKey val playlistId: String,
    val groupOverride: String? = null,
    val pinned: Boolean = false,
    val sortOrder: Int? = null,
    /** Content signature the user last saw; differs from the playlist's = "Neu" badge. */
    val seenSignature: String? = null,
)

/** Home sections (recently played, recently added, …): server order of album ids per list type. */
@Entity(tableName = "album_lists", primaryKeys = ["type", "position"])
data class AlbumListEntryEntity(
    val type: String,
    val position: Int,
    val albumId: String,
)

data class PlaylistWithPrefs(
    @Embedded val playlist: PlaylistEntity,
    val groupOverride: String?,
    val pinned: Boolean?,
    val sortOrder: Int?,
    val seenSignature: String?,
)
