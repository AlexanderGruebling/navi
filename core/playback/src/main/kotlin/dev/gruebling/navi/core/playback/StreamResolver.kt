package dev.gruebling.navi.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import android.net.Uri
import dev.gruebling.navi.core.download.DownloadFiles
import dev.gruebling.navi.core.network.CredentialsSource
import dev.gruebling.navi.core.network.NotLoggedInException
import dev.gruebling.navi.core.network.SubsonicUrls
import dev.gruebling.navi.core.model.StreamQuality
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites navi:// URIs into authenticated stream / cover URLs and sets an auth-free cache key. Downloaded songs
 * play from their file instead.
 */
@OptIn(UnstableApi::class)
@Singleton
class StreamResolver @Inject constructor(
    private val credentials: CredentialsSource,
    private val quality: StreamQualityProvider,
    private val downloads: DownloadFiles,
) : ResolvingDataSource.Resolver {

    /** Quality each song was last opened with (resolving runs on loader threads, reads on the main thread). */
    private val lastQuality = ConcurrentHashMap<String, StreamQuality>()

    /** The quality [songId] is currently streamed in, if it was opened in this process. */
    fun qualityOf(songId: String): StreamQuality? = lastQuality[songId]

    /** The quality [songId] would be opened in now: original for a downloaded file, else the current preset. */
    fun qualityForNewStream(songId: String): StreamQuality =
        if (downloads.fileIfDownloaded(songId) != null) StreamQuality.Original else quality.current()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != NaviUris.SCHEME) return dataSpec
        val creds = credentials.current() ?: throw NotLoggedInException()
        val id = uri.lastPathSegment ?: return dataSpec
        return when (uri.authority) {
            NaviUris.STREAM -> {
                val offset = NaviUris.offsetSec(uri)
                if (offset == 0L) {
                    downloads.fileIfDownloaded(id)?.let { file ->
                        // A complete file: seeking works by bytes, whatever quality it was downloaded in.
                        lastQuality[id] = StreamQuality.Original
                        return dataSpec.withUri(Uri.fromFile(file))
                    }
                }
                val q = if (offset > 0) {
                    StreamQuality(uri.getQueryParameter(NaviUris.BITRATE)?.toIntOrNull() ?: 0, uri.getQueryParameter(NaviUris.FORMAT))
                } else {
                    quality.current()
                }
                lastQuality[id] = q
                dataSpec.buildUpon()
                    .setUri(SubsonicUrls.streamUrl(creds, id, q.maxBitRate, q.format, offset).toString())
                    .setKey(SubsonicUrls.streamCacheKey(id, q.maxBitRate, q.format, offset))
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
