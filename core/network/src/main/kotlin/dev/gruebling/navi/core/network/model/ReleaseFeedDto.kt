package dev.gruebling.navi.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `releases.json` of release-watch (version 1). Unknown fields are ignored, so the feed can grow. */
@Serializable
data class ReleaseFeedDto(
    val version: Int = 1,
    val generated: String? = null,
    val releases: List<ReleaseDto> = emptyList(),
)

@Serializable
data class ReleaseDto(
    val id: String,
    val title: String,
    /** "album", "ep" or "single". */
    val type: String,
    /** ISO date, "2026-11-13". */
    val date: String,
    val artists: List<String> = emptyList(),
    val link: String = "",
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    val duration: Int? = null,
    val label: String? = null,
    val tracks: List<ReleaseTrackDto> = emptyList(),
    @SerialName("in_library") val inLibrary: Boolean = false,
    @SerialName("navidrome_album_id") val navidromeAlbumId: String? = null,
)

@Serializable
data class ReleaseTrackDto(val title: String, val duration: Int = 0)
