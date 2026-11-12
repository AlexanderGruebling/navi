package dev.gruebling.navi.core.model

import kotlin.time.Instant

data class Song(
    val id: SongId,
    val title: String,
    val albumId: AlbumId?,
    val album: String?,
    val artistId: ArtistId?,
    val artist: String?,
    val track: Int?,
    val disc: Int?,
    val durationSec: Int,
    val year: Int?,
    val coverArt: CoverArtId?,
    val starred: Boolean,
    val suffix: String?,
    val bitRate: Int?,
    val playCount: Long,
    val replayGain: ReplayGain? = null,
)

/** ReplayGain tags of a song (from the server): gains in dB, peaks as linear sample values (1.0 = full scale). */
data class ReplayGain(
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
) {
    val isEmpty: Boolean get() = trackGain == null && albumGain == null
}

data class Album(
    val id: AlbumId,
    val name: String,
    val artist: String?,
    val artistId: ArtistId?,
    val coverArt: CoverArtId?,
    val songCount: Int,
    val durationSec: Int,
    val year: Int?,
    val genre: String?,
    val starred: Boolean,
    val created: Instant?,
    val playCount: Long,
)

data class Artist(
    val id: ArtistId,
    val name: String,
    val coverArt: CoverArtId?,
    val imageUrl: String?,
    val albumCount: Int,
    val starred: Boolean,
    /** First letter group from the server index ("A", "B", "#"). */
    val indexLetter: String?,
)

data class AlbumDetail(val album: Album, val songs: List<Song>)

data class ArtistDetail(val artist: Artist, val albums: List<Album>)

/** Extras from Last.fm (via the server). Every part may be empty: not every server or artist has them. */
data class ArtistInfo(
    /** Plain text. */
    val biography: String?,
    val lastFmUrl: String?,
    val topSongs: List<Song>,
    val similarArtists: List<Artist>,
) {
    companion object {
        val Empty = ArtistInfo(null, null, emptyList(), emptyList())
    }
}

/** Where songs go when added to the queue without replacing it. */
enum class EnqueuePosition {
    /** Right after the playing song. */
    Next,

    /** At the end of the queue. */
    End,
}

/** Server-side album lists used on Home. */
enum class AlbumListType(val apiValue: String) {
    RecentlyPlayed("recent"),
    RecentlyAdded("newest"),
    MostPlayed("frequent"),
    Random("random"),
}

data class Favorites(val songs: List<Song>, val albums: List<Album>, val artists: List<Artist>)

data class SearchResults(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()

    companion object {
        val Empty = SearchResults(emptyList(), emptyList(), emptyList())
    }
}
