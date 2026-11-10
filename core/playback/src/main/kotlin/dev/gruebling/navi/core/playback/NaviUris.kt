package dev.gruebling.navi.core.playback

import android.net.Uri

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

    fun cover(coverArtId: String, sizePx: Int): Uri =
        Uri.Builder().scheme(SCHEME).authority(COVER).appendPath(coverArtId)
            .appendQueryParameter("size", sizePx.toString()).build()
}
