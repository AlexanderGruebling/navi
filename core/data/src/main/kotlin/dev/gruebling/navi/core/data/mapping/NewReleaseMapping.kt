package dev.gruebling.navi.core.data.mapping

import dev.gruebling.navi.core.database.entity.NewReleaseEntity
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.NewRelease
import dev.gruebling.navi.core.model.ReleaseTrack
import dev.gruebling.navi.core.model.ReleaseType
import dev.gruebling.navi.core.network.model.ReleaseDto
import dev.gruebling.navi.core.network.model.ReleaseTrackDto
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val NAMES = ListSerializer(String.serializer())
private val TRACKS = ListSerializer(ReleaseTrackDto.serializer())

internal fun releaseTypeOf(feedType: String): ReleaseType? = when (feedType.lowercase()) {
    "album" -> ReleaseType.Album
    "ep" -> ReleaseType.Ep
    "single" -> ReleaseType.Single
    else -> null
}

private val ReleaseType.feedName: String
    get() = when (this) {
        ReleaseType.Album -> "album"
        ReleaseType.Ep -> "ep"
        ReleaseType.Single -> "single"
    }

private fun dateOrNull(iso: String?): LocalDate? =
    try { iso?.let(LocalDate::parse) } catch (_: DateTimeParseException) { null }

/** Null for entries this app can't show (unknown type, broken date): the feed may grow new kinds. */
internal fun ReleaseDto.toDomainOrNull(): NewRelease? {
    val type = releaseTypeOf(type) ?: return null
    val released = dateOrNull(date) ?: return null
    return NewRelease(
        id = id,
        title = title,
        type = type,
        artists = artists,
        releaseDate = released,
        firstSeen = dateOrNull(firstSeen) ?: released,
        link = link,
        coverUrl = coverMedium?.ifBlank { null },
        coverUrlLarge = coverBig?.ifBlank { null } ?: coverMedium?.ifBlank { null },
        trackCount = trackCount,
        durationSec = duration,
        label = label?.ifBlank { null },
        tracks = tracks.map { ReleaseTrack(it.title, it.duration) },
        inLibrary = inLibrary && !navidromeAlbumId.isNullOrBlank(),
        libraryAlbumId = navidromeAlbumId?.ifBlank { null }?.let(::AlbumId),
    )
}

internal fun NewRelease.toDto(): ReleaseDto = ReleaseDto(
    id = id,
    title = title,
    type = type.feedName,
    date = releaseDate.toString(),
    artists = artists,
    link = link,
    coverMedium = coverUrl,
    coverBig = coverUrlLarge,
    firstSeen = firstSeen.toString(),
    trackCount = trackCount,
    duration = durationSec,
    label = label,
    tracks = tracks.map { ReleaseTrackDto(it.title, it.durationSec) },
    inLibrary = inLibrary,
    navidromeAlbumId = libraryAlbumId?.value,
)

internal fun NewRelease.toEntity(position: Int, json: Json) = NewReleaseEntity(
    id = id,
    title = title,
    type = type.feedName,
    artistsJson = json.encodeToString(NAMES, artists),
    releaseDate = releaseDate.toString(),
    firstSeen = firstSeen.toString(),
    link = link,
    coverUrl = coverUrl,
    coverUrlLarge = coverUrlLarge,
    trackCount = trackCount,
    duration = durationSec,
    label = label,
    tracksJson = json.encodeToString(TRACKS, tracks.map { ReleaseTrackDto(it.title, it.durationSec) }),
    inLibrary = inLibrary,
    libraryAlbumId = libraryAlbumId?.value,
    position = position,
)

internal fun NewReleaseEntity.toDomainOrNull(json: Json): NewRelease? {
    val type = releaseTypeOf(type) ?: return null
    val released = dateOrNull(releaseDate) ?: return null
    return NewRelease(
        id = id,
        title = title,
        type = type,
        artists = json.decodeFromString(NAMES, artistsJson),
        releaseDate = released,
        firstSeen = dateOrNull(firstSeen) ?: released,
        link = link,
        coverUrl = coverUrl,
        coverUrlLarge = coverUrlLarge,
        trackCount = trackCount,
        durationSec = duration,
        label = label,
        tracks = json.decodeFromString(TRACKS, tracksJson).map { ReleaseTrack(it.title, it.duration) },
        inLibrary = inLibrary,
        libraryAlbumId = libraryAlbumId?.let(::AlbumId),
    )
}
