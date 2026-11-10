package dev.gruebling.navi.core.model

/** Subsonic ids are opaque strings. Typed wrappers stop an album id being passed where a song id belongs. */
@JvmInline value class AlbumId(val value: String)
@JvmInline value class ArtistId(val value: String)
@JvmInline value class PlaylistId(val value: String)
@JvmInline value class SongId(val value: String)
@JvmInline value class CoverArtId(val value: String)
