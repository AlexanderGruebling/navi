package dev.gruebling.navi.core.playback

import android.net.Uri
import dev.gruebling.navi.core.model.StreamQuality

/**
 * Media items carry `navi://stream/<songId>` and `navi://cover/<coverArtId>?size=` instead of real URLs.
 * [StreamResolver] turns them into authenticated server URLs when they are opened, so a persisted queue never
 * holds credentials and always uses the current login and quality setting.
 */
object NaviUris {
    const val SCHEME = "navi"
    const val STREAM = "stream"
    const val COVER = "cover"

    fun stream(songId: String): Uri = Uri.Builder().scheme(SCHEME).authority(STREAM).appendPath(songId).build()

    /**
     * A transcoded stream that starts [offsetSec] into the song, with the quality pinned to what was playing (a
     * changed setting must not turn it into an original file, which the server can't start mid-way).
     */
    fun streamFrom(songId: String, offsetSec: Long, quality: StreamQuality): Uri =
        Uri.Builder().scheme(SCHEME).authority(STREAM).appendPath(songId)
            .appendQueryParameter(OFFSET, offsetSec.toString())
            .appendQueryParameter(BITRATE, quality.maxBitRate.toString())
            .apply { quality.format?.let { appendQueryParameter(FORMAT, it) } }
            .build()

    /** Seconds into the song this stream starts at; 0 for a normal stream. */
    fun offsetSec(uri: Uri?): Long =
        if (uri?.scheme == SCHEME && uri.authority == STREAM) uri.getQueryParameter(OFFSET)?.toLongOrNull() ?: 0 else 0

    /** The quality an offset stream ([streamFrom]) is pinned to; null for a normal stream. */
    fun pinnedQuality(uri: Uri?): StreamQuality? {
        if (offsetSec(uri) <= 0) return null
        return StreamQuality(uri?.getQueryParameter(BITRATE)?.toIntOrNull() ?: 0, uri?.getQueryParameter(FORMAT))
    }

    fun isStream(uri: Uri?): Boolean = uri?.scheme == SCHEME && uri.authority == STREAM

    internal const val OFFSET = "offset"
    internal const val BITRATE = "br"
    internal const val FORMAT = "fmt"

    fun cover(coverArtId: String, sizePx: Int): Uri =
        Uri.Builder().scheme(SCHEME).authority(COVER).appendPath(coverArtId)
            .appendQueryParameter("size", sizePx.toString()).build()
}
