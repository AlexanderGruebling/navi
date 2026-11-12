package dev.gruebling.navi.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.gruebling.navi.core.database.entity.QueueItemEntity
import dev.gruebling.navi.core.database.entity.SongEntity
import dev.gruebling.navi.core.model.Song

/** Song ⇄ MediaItem. The media id is the Subsonic song id. */
object MediaItems {
    private const val EXTRA_ALBUM_ID = "albumId"
    private const val EXTRA_ARTIST_ID = "artistId"
    private const val EXTRA_COVER_ART = "coverArt"
    private const val EXTRA_FROM_MIX = "fromMix"
    const val ARTWORK_SIZE = 600

    /** [fromMix]: added by an instant mix (see MixExtender). */
    fun fromSong(song: Song, fromMix: Boolean = false): MediaItem = build(
        songId = song.id.value,
        title = song.title,
        artist = song.artist,
        album = song.album,
        albumId = song.albumId?.value,
        artistId = song.artistId?.value,
        coverArt = song.coverArt?.value,
        durationMs = song.durationSec * 1000L,
        fromMix = fromMix,
    )

    fun fromEntity(song: SongEntity): MediaItem = build(
        song.id, song.title, song.artist, song.album, song.albumId, song.artistId, song.coverArt, song.duration * 1000L,
    )

    fun fromQueue(item: QueueItemEntity): MediaItem = build(
        item.songId, item.title, item.artist, item.album, item.albumId, item.artistId, item.coverArt, item.durationMs,
        item.fromMix,
    )

    fun toQueue(position: Int, item: MediaItem): QueueItemEntity {
        val m = item.mediaMetadata
        return QueueItemEntity(
            position = position,
            songId = item.mediaId,
            title = m.title?.toString().orEmpty(),
            artist = m.artist?.toString(),
            album = m.albumTitle?.toString(),
            albumId = m.extras?.getString(EXTRA_ALBUM_ID),
            artistId = m.extras?.getString(EXTRA_ARTIST_ID),
            coverArt = m.extras?.getString(EXTRA_COVER_ART),
            durationMs = m.durationMs ?: 0L,
            fromMix = isFromMix(item),
        )
    }

    /** The controller strips URIs when sending items to the session; this puts the stream URI back. */
    fun withStreamUri(item: MediaItem): MediaItem = item.buildUpon().setUri(NaviUris.stream(item.mediaId)).build()

    fun coverArtId(item: MediaItem): String? = item.mediaMetadata.extras?.getString(EXTRA_COVER_ART)
    fun albumId(item: MediaItem): String? = item.mediaMetadata.extras?.getString(EXTRA_ALBUM_ID)
    fun artistId(item: MediaItem): String? = item.mediaMetadata.extras?.getString(EXTRA_ARTIST_ID)
    fun isFromMix(item: MediaItem): Boolean = item.mediaMetadata.extras?.getBoolean(EXTRA_FROM_MIX) == true

    private fun build(
        songId: String,
        title: String,
        artist: String?,
        album: String?,
        albumId: String?,
        artistId: String?,
        coverArt: String?,
        durationMs: Long,
        fromMix: Boolean = false,
    ): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_ALBUM_ID, albumId)
            putString(EXTRA_ARTIST_ID, artistId)
            putString(EXTRA_COVER_ART, coverArt)
            if (fromMix) putBoolean(EXTRA_FROM_MIX, true)
        }
        return MediaItem.Builder()
            .setMediaId(songId)
            .setUri(NaviUris.stream(songId))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(coverArt?.let { NaviUris.cover(it, ARTWORK_SIZE) })
                    .setDurationMs(durationMs.takeIf { it > 0 })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}
