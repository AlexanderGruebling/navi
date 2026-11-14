package dev.gruebling.navi.core.model

import java.time.LocalDate

/** Kind of a new release, as the release feed names it. */
enum class ReleaseType { Album, Ep, Single }

data class ReleaseTrack(val title: String, val durationSec: Int)

/**
 * A new release by an artist the user listens to, from the release feed (release-watch on the homelab). Info only:
 * it is usually not on the server. Once it is ([inLibrary]), [libraryAlbumId] opens it like any album.
 */
data class NewRelease(
    /** Stable id from the feed ("deezer:123"). */
    val id: String,
    val title: String,
    val type: ReleaseType,
    val artists: List<String>,
    val releaseDate: LocalDate,
    /** When the feed first listed it. */
    val firstSeen: LocalDate,
    /** Web page of the release (Deezer). */
    val link: String,
    val coverUrl: String?,
    val coverUrlLarge: String?,
    val trackCount: Int?,
    val durationSec: Int?,
    val label: String?,
    val tracks: List<ReleaseTrack>,
    val inLibrary: Boolean,
    val libraryAlbumId: AlbumId?,
    /** Marked seen by the user. */
    val seen: Boolean = false,
    /** Saved for later by the user. Saved releases stay even after the feed drops them. */
    val saved: Boolean = false,
) {
    val artist: String get() = artists.joinToString(", ")
}
