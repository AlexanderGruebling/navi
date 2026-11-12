package dev.gruebling.navi.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Network models for the Subsonic JSON API (1.16.1) plus OpenSubsonic extensions.
// They mirror the wire format; core:data maps them to domain models. Dates stay ISO-8601 strings here.

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse,
)

@Serializable
data class SubsonicResponse(
    val status: String,
    val version: String,
    /** OpenSubsonic: server name, e.g. "navidrome". */
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean = false,
    val error: SubsonicError? = null,

    val openSubsonicExtensions: List<OpenSubsonicExtension>? = null,
    val user: User? = null,
    val playlists: Playlists? = null,
    val playlist: Playlist? = null,
    val albumList2: AlbumList2? = null,
    val album: Album? = null,
    val artists: Artists? = null,
    val artist: Artist? = null,
    val artistInfo2: ArtistInfo2? = null,
    val topSongs: Songs? = null,
    val similarSongs: Songs? = null,
    val searchResult3: SearchResult3? = null,
    val starred2: Starred2? = null,
    val randomSongs: Songs? = null,
    val lyricsList: LyricsList? = null,
    val lyrics: PlainLyrics? = null,
) {
    val isOk: Boolean get() = status == "ok"
}

/** OpenSubsonic `getLyricsBySongId`: one entry per language / variant. */
@Serializable
data class LyricsList(val structuredLyrics: List<StructuredLyrics> = emptyList())

@Serializable
data class StructuredLyrics(
    val lang: String? = null,
    val synced: Boolean = false,
    /** Milliseconds; positive means the lines should show sooner. */
    val offset: Long = 0,
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val line: List<LyricsLineDto> = emptyList(),
)

@Serializable
data class LyricsLineDto(
    /** Milliseconds; absent for unsynced lyrics. */
    val start: Long? = null,
    val value: String = "",
)

/** Classic `getLyrics`. */
@Serializable
data class PlainLyrics(val artist: String? = null, val title: String? = null, val value: String? = null)

@Serializable
data class SubsonicError(val code: Int, val message: String? = null)

@Serializable
data class OpenSubsonicExtension(val name: String, val versions: List<Int> = emptyList())

@Serializable
data class User(
    val username: String,
    val scrobblingEnabled: Boolean = false,
    val adminRole: Boolean = false,
    val streamRole: Boolean = true,
)

@Serializable
data class Playlists(val playlist: List<Playlist> = emptyList())

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    val public: Boolean = false,
    val songCount: Int = 0,
    /** Seconds. */
    val duration: Int = 0,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
    /** OpenSubsonic: true for playlists the user can't edit (Navidrome smart playlists, other users' playlists). */
    val readonly: Boolean = false,
    /** OpenSubsonic: when a smart playlist's content expires and is re-evaluated. */
    val validUntil: String? = null,
    val entry: List<Song> = emptyList(),
)

@Serializable
data class AlbumList2(val album: List<Album> = emptyList())

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val playCount: Long = 0,
    val created: String? = null,
    val starred: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val played: String? = null,
    val userRating: Int? = null,
    val displayArtist: String? = null,
    val isCompilation: Boolean = false,
    val sortName: String? = null,
    val song: List<Song> = emptyList(),
)

@Serializable
data class Artists(
    val ignoredArticles: String? = null,
    val index: List<ArtistIndex> = emptyList(),
)

@Serializable
data class ArtistIndex(val name: String, val artist: List<Artist> = emptyList())

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val sortName: String? = null,
    val album: List<Album> = emptyList(),
)

@Serializable
data class ArtistInfo2(
    /** HTML from Last.fm, usually ending in a "Read more on Last.fm" link. */
    val biography: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
    val similarArtist: List<Artist> = emptyList(),
)

/** A track ("Child" in the Subsonic spec). */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val parent: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val displayArtist: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Int = 0,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val playCount: Long = 0,
    val played: String? = null,
    val created: String? = null,
    val starred: String? = null,
    val userRating: Int? = null,
    val replayGain: ReplayGain? = null,
)

@Serializable
data class ReplayGain(
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Starred2(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Songs(val song: List<Song> = emptyList())
