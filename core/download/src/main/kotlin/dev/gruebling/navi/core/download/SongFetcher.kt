package dev.gruebling.navi.core.download

import dev.gruebling.navi.core.data.di.IoDispatcher
import dev.gruebling.navi.core.model.StreamQuality
import dev.gruebling.navi.core.network.CredentialsSource
import dev.gruebling.navi.core.network.NotLoggedInException
import dev.gruebling.navi.core.network.SubsonicUrls
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** The server refused one song (gone, no permission, transcoding failed). Other songs may still work. */
class SongUnavailableException(message: String) : IOException(message)

/** Loads one song into a file. Network trouble is an [IOException]; a refused song a [SongUnavailableException]. */
fun interface SongFetcher {
    suspend fun fetch(songId: String, quality: StreamQuality, into: File)
}

class HttpSongFetcher @Inject constructor(
    private val client: OkHttpClient,
    private val credentials: CredentialsSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SongFetcher {
    override suspend fun fetch(songId: String, quality: StreamQuality, into: File) = withContext(io) {
        val creds = credentials.current() ?: throw NotLoggedInException()
        val url = SubsonicUrls.streamUrl(creds, songId, quality.maxBitRate, quality.format)
        val call = client.newCall(Request.Builder().url(url).build())
        val cancel = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (response.code in 500..599) throw IOException("HTTP ${response.code}")
                if (!response.isSuccessful) throw SongUnavailableException("HTTP ${response.code} for $songId")
                // Subsonic answers errors with HTTP 200 and an XML / JSON body instead of audio.
                val type = response.body.contentType()
                if (type != null && (type.subtype.contains("xml") || type.subtype.contains("json"))) {
                    throw SongUnavailableException("Server error for $songId: ${response.body.string().take(200)}")
                }
                into.outputStream().use { out ->
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                        }
                    }
                }
            }
        } finally {
            cancel.dispose()
        }
    }
}
