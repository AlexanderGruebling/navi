package dev.gruebling.navi.core.network

import dev.gruebling.navi.core.model.ServerCredentials
import dev.gruebling.navi.core.network.model.SubsonicEnvelope
import dev.gruebling.navi.core.network.model.SubsonicResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * Subsonic REST endpoints. Paths are relative to the placeholder base URL; the server, auth parameters and
 * `f=json` are added by [SubsonicRequestInterceptor]. Unwrap results with [ok].
 */
interface SubsonicApi {
    /** [credentials] overrides the stored login, to test a server before saving it. */
    @GET("rest/ping")
    suspend fun ping(@Tag credentials: ServerCredentials? = null): SubsonicEnvelope

    @GET("rest/getOpenSubsonicExtensions")
    suspend fun getOpenSubsonicExtensions(@Tag credentials: ServerCredentials? = null): SubsonicEnvelope

    @GET("rest/getUser")
    suspend fun getUser(@Query("username") username: String): SubsonicEnvelope

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(): SubsonicEnvelope

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicEnvelope

    /**
     * Creates a playlist owned by the user, or with [playlistId] replaces that playlist's songs by [songIds]. Since
     * Subsonic 1.14 the answer contains the playlist.
     */
    @GET("rest/createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String? = null,
        @Query("songId") songIds: List<String>,
        @Query("playlistId") playlistId: String? = null,
    ): SubsonicEnvelope

    /** Renames, appends [songIdsToAdd] and removes the songs at [songIndexesToRemove] (positions before the change). */
    @GET("rest/updatePlaylist")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("songIdToAdd") songIdsToAdd: List<String> = emptyList(),
        @Query("songIndexToRemove") songIndexesToRemove: List<Int> = emptyList(),
    ): SubsonicEnvelope

    @GET("rest/deletePlaylist")
    suspend fun deletePlaylist(@Query("id") playlistId: String): SubsonicEnvelope

    /** [type]: newest, recent, frequent, random, alphabeticalByName, alphabeticalByArtist, starred, highest. */
    @GET("rest/getAlbumList2")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SubsonicEnvelope

    @GET("rest/getAlbum")
    suspend fun getAlbum(@Query("id") id: String): SubsonicEnvelope

    @GET("rest/getArtists")
    suspend fun getArtists(): SubsonicEnvelope

    @GET("rest/getArtist")
    suspend fun getArtist(@Query("id") id: String): SubsonicEnvelope

    /** Biography and similar artists, which Navidrome takes from Last.fm. [includeNotPresent] false = only artists in the library. */
    @GET("rest/getArtistInfo2")
    suspend fun getArtistInfo2(
        @Query("id") id: String,
        @Query("count") count: Int = 12,
        @Query("includeNotPresent") includeNotPresent: Boolean = false,
    ): SubsonicEnvelope

    /** The artist's most popular songs that are in the library (Last.fm ranking). Takes the artist's name, not its id. */
    @GET("rest/getTopSongs")
    suspend fun getTopSongs(@Query("artist") artist: String, @Query("count") count: Int = 10): SubsonicEnvelope

    /**
     * Songs similar to a song, album or artist ([id] may be any of them), for an instant mix. Navidrome builds the
     * list from Last.fm similarity and returns only songs in the library.
     */
    @GET("rest/getSimilarSongs")
    suspend fun getSimilarSongs(@Query("id") id: String, @Query("count") count: Int = 50): SubsonicEnvelope

    @GET("rest/search3")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 10,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 30,
    ): SubsonicEnvelope

    @GET("rest/getStarred2")
    suspend fun getStarred2(): SubsonicEnvelope

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(@Query("size") size: Int = 50): SubsonicEnvelope

    @GET("rest/star")
    suspend fun star(
        @Query("id") songIds: List<String> = emptyList(),
        @Query("albumId") albumIds: List<String> = emptyList(),
        @Query("artistId") artistIds: List<String> = emptyList(),
    ): SubsonicEnvelope

    @GET("rest/unstar")
    suspend fun unstar(
        @Query("id") songIds: List<String> = emptyList(),
        @Query("albumId") albumIds: List<String> = emptyList(),
        @Query("artistId") artistIds: List<String> = emptyList(),
    ): SubsonicEnvelope

    /** OpenSubsonic `songLyrics` extension: structured (possibly synced) lyrics from the song's tags or sidecar files. */
    @GET("rest/getLyricsBySongId")
    suspend fun getLyricsBySongId(@Query("id") id: String): SubsonicEnvelope

    /** Classic Subsonic lyrics lookup by artist and title: plain text (Navidrome may keep LRC timestamps in it). */
    @GET("rest/getLyrics")
    suspend fun getLyrics(@Query("artist") artist: String?, @Query("title") title: String?): SubsonicEnvelope

    /** [submission] false = "now playing", true = counts as a play. [time] in ms since epoch. */
    @GET("rest/scrobble")
    suspend fun scrobble(
        @Query("id") songId: String,
        @Query("submission") submission: Boolean,
        @Query("time") time: Long? = null,
    ): SubsonicEnvelope
}

/** Subsonic reports errors with HTTP 200 and `status: failed`. */
class SubsonicException(val code: Int, message: String?) : Exception("Subsonic error $code: $message") {
    companion object {
        const val WRONG_CREDENTIALS = 40
        const val NOT_AUTHORIZED = 50
        const val NOT_FOUND = 70
    }
}

/** Returns the response, or throws [SubsonicException] if the server reported an error. */
fun SubsonicEnvelope.ok(): SubsonicResponse {
    if (!response.isOk) throw SubsonicException(response.error?.code ?: 0, response.error?.message)
    return response
}
