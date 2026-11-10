package dev.gruebling.navi.core.network

import dev.gruebling.navi.core.model.ServerCredentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Builds authenticated Subsonic URLs.
 *
 * Every URL carries a fresh salt and token, so the URL itself is useless as a cache key.
 * Image and audio caches must use [coverArtCacheKey] / [streamCacheKey] instead.
 */
object SubsonicUrls {
    /** Placeholder host used as Retrofit base URL; [SubsonicRequestInterceptor] swaps in the real server. */
    const val PLACEHOLDER_HOST = "subsonic.invalid"
    const val PLACEHOLDER_BASE_URL = "http://$PLACEHOLDER_HOST/"

    /** Sizes requested from getCoverArt. A few fixed sizes keep the disk cache small and reusable. */
    val COVER_SIZES = intArrayOf(150, 300, 600, 1200)

    fun build(
        credentials: ServerCredentials,
        pathSegments: List<String>,
        query: List<Pair<String, String?>> = emptyList(),
        salt: String = SubsonicAuth.newSalt(),
    ): HttpUrl {
        val builder = credentials.serverUrl.toHttpUrl().newBuilder()
        pathSegments.filter { it.isNotEmpty() }.forEach(builder::addPathSegment)
        // null = leave the parameter out (OkHttp would add it without a value)
        query.forEach { (name, value) -> if (value != null) builder.addQueryParameter(name, value) }
        return builder
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", SubsonicAuth.token(credentials.password, salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", SubsonicAuth.API_VERSION)
            .addQueryParameter("c", SubsonicAuth.CLIENT_NAME)
            .addQueryParameter("f", "json")
            .build()
    }

    /** Rounds a requested pixel size up to the next of [COVER_SIZES]. */
    fun coverSizeFor(px: Int): Int = COVER_SIZES.firstOrNull { it >= px } ?: COVER_SIZES.last()

    fun coverArtUrl(credentials: ServerCredentials, coverArtId: String, sizePx: Int): HttpUrl =
        build(credentials, listOf("rest", "getCoverArt"), listOf("id" to coverArtId, "size" to coverSizeFor(sizePx).toString()))

    fun coverArtCacheKey(coverArtId: String, sizePx: Int): String = "cover:$coverArtId:${coverSizeFor(sizePx)}"

    /** [maxBitRate] 0 = original. [format] "raw" = no transcoding. */
    /**
     * [timeOffsetSec] > 0 starts a transcoded stream later in the song (OpenSubsonic `transcodeOffset`); the server
     * ignores it for files it sends unchanged, so only use it when transcoding.
     */
    fun streamUrl(
        credentials: ServerCredentials,
        songId: String,
        maxBitRate: Int = 0,
        format: String? = null,
        timeOffsetSec: Long = 0,
    ): HttpUrl =
        build(
            credentials,
            listOf("rest", "stream"),
            listOf(
                "id" to songId,
                "maxBitRate" to maxBitRate.toString(),
                "format" to format,
                "timeOffset" to timeOffsetSec.takeIf { it > 0 }?.toString(),
            ),
        )

    fun streamCacheKey(songId: String, maxBitRate: Int = 0, format: String? = null, timeOffsetSec: Long = 0): String =
        "stream:$songId:$maxBitRate:${format ?: "raw"}" + if (timeOffsetSec > 0) "@$timeOffsetSec" else ""
}
