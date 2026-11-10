package dev.gruebling.navi.core.data.mapping

import dev.gruebling.navi.core.database.entity.AlbumEntity
import dev.gruebling.navi.core.database.entity.ArtistEntity
import dev.gruebling.navi.core.database.entity.PlaylistEntity
import dev.gruebling.navi.core.database.entity.SongEntity
import dev.gruebling.navi.core.model.Album
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.model.ArtistId
import dev.gruebling.navi.core.model.CoverArtId
import dev.gruebling.navi.core.model.Song
import dev.gruebling.navi.core.model.SongId
import dev.gruebling.navi.core.network.model.Album as NetworkAlbum
import dev.gruebling.navi.core.network.model.Artist as NetworkArtist
import dev.gruebling.navi.core.network.model.Playlist as NetworkPlaylist
import dev.gruebling.navi.core.network.model.Song as NetworkSong
import java.util.Locale

// network -> entity

internal fun NetworkSong.toEntity() = SongEntity(
    id = id,
    title = title,
    albumId = albumId,
    album = album,
    artistId = artistId,
    artist = displayArtist ?: artist,
    track = track,
    disc = discNumber,
    duration = duration,
    year = year?.takeIf { it > 0 },
    genre = genre,
    coverArt = coverArt,
    starredAt = starred.toEpochMillis(),
    suffix = suffix,
    contentType = contentType,
    bitRate = bitRate,
    size = size,
    playCount = playCount,
    created = created.toEpochMillis(),
)

internal fun NetworkAlbum.toEntity(syncedAt: Long? = null) = AlbumEntity(
    id = id,
    name = name,
    sortName = sortKey(sortName ?: name),
    artist = displayArtist ?: artist,
    artistId = artistId,
    coverArt = coverArt,
    songCount = songCount,
    duration = duration,
    year = year?.takeIf { it > 0 },
    genre = genre,
    starredAt = starred.toEpochMillis(),
    created = created.toEpochMillis(),
    playCount = playCount,
    syncedAt = syncedAt,
)

internal fun NetworkArtist.toEntity(indexLetter: String? = null) = ArtistEntity(
    id = id,
    name = name,
    sortName = sortKey(sortName ?: name),
    indexLetter = indexLetter,
    coverArt = coverArt,
    imageUrl = artistImageUrl,
    albumCount = albumCount,
    starredAt = starred.toEpochMillis(),
)

internal fun NetworkPlaylist.toEntity(position: Int, contentSignature: String?) = PlaylistEntity(
    id = id,
    name = name,
    comment = comment?.trim(),
    owner = owner,
    isPublic = public,
    readonly = readonly,
    songCount = songCount,
    duration = duration,
    coverArt = coverArt,
    created = created.toEpochMillis(),
    changed = changed.toEpochMillis(),
    validUntil = validUntil.toEpochMillis(),
    position = position,
    contentSignature = contentSignature,
)

/** Case- and accent-insensitive enough for sorting ("Ärzte" next to "Arzte"); SQLite's NOCASE only knows ASCII. */
internal fun sortKey(name: String): String =
    java.text.Normalizer.normalize(name.trim().lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

// entity -> domain

internal fun SongEntity.toDomain() = Song(
    id = SongId(id),
    title = title,
    albumId = albumId?.let(::AlbumId),
    album = album,
    artistId = artistId?.let(::ArtistId),
    artist = artist,
    track = track,
    disc = disc,
    durationSec = duration,
    year = year,
    coverArt = coverArt?.let(::CoverArtId),
    starred = starredAt != null,
    suffix = suffix,
    bitRate = bitRate,
    playCount = playCount,
)

internal fun AlbumEntity.toDomain() = Album(
    id = AlbumId(id),
    name = name,
    artist = artist,
    artistId = artistId?.let(::ArtistId),
    coverArt = coverArt?.let(::CoverArtId),
    songCount = songCount,
    durationSec = duration,
    year = year,
    genre = genre,
    starred = starredAt != null,
    created = created.toInstant(),
    playCount = playCount,
)

internal fun ArtistEntity.toDomain() = Artist(
    id = ArtistId(id),
    name = name,
    coverArt = coverArt?.let(::CoverArtId),
    imageUrl = imageUrl,
    albumCount = albumCount,
    starred = starredAt != null,
    indexLetter = indexLetter,
)
