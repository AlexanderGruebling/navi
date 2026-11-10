package dev.gruebling.navi.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import dev.gruebling.navi.core.network.CredentialsSource
import dev.gruebling.navi.core.network.NotLoggedInException
import dev.gruebling.navi.core.network.SubsonicUrls
import javax.inject.Inject

/** Rewrites navi:// URIs into authenticated stream / cover URLs and sets an auth-free cache key. */
@OptIn(UnstableApi::class)
class StreamResolver @Inject constructor(
    private val credentials: CredentialsSource,
    private val quality: StreamQualityProvider,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != NaviUris.SCHEME) return dataSpec
        val creds = credentials.current() ?: throw NotLoggedInException()
        val id = uri.lastPathSegment ?: return dataSpec
        return when (uri.authority) {
            NaviUris.STREAM -> {
                val q = quality.current()
                dataSpec.buildUpon()
                    .setUri(SubsonicUrls.streamUrl(creds, id, q.maxBitRate, q.format).toString())
                    .setKey(SubsonicUrls.streamCacheKey(id, q.maxBitRate, q.format))
                    .build()
            }
            NaviUris.COVER -> {
                val size = uri.getQueryParameter("size")?.toIntOrNull() ?: 600
                dataSpec.buildUpon()
                    .setUri(SubsonicUrls.coverArtUrl(creds, id, size).toString())
                    .setKey(SubsonicUrls.coverArtCacheKey(id, size))
                    .build()
            }
            else -> dataSpec
        }
    }
}
