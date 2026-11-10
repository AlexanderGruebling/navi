package dev.gruebling.navi.core.data.testing

import dev.gruebling.navi.core.model.ServerCredentials
import dev.gruebling.navi.core.network.CredentialsSource
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.SubsonicClientFactory
import dev.gruebling.navi.core.network.SubsonicRequestInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** MockWebServer that answers Subsonic endpoints from a map; unknown endpoints get a Subsonic error 70. */
class FakeSubsonicServer : AutoCloseable {
    private val server = MockWebServer()
    private val responses = ConcurrentHashMap<String, (HttpUrl) -> String>()
    val requests = CopyOnWriteArrayList<HttpUrl>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                requests += url
                val endpoint = url.pathSegments.last()
                val body = responses[endpoint]?.invoke(url)
                    ?: error(70, "Not found: $endpoint")
                return MockResponse.Builder().body(body).build()
            }
        }
        server.start()
    }

    fun on(endpoint: String, body: (HttpUrl) -> String) { responses[endpoint] = body }
    fun on(endpoint: String, body: String) = on(endpoint) { body }

    fun requestsTo(endpoint: String) = requests.filter { it.pathSegments.last() == endpoint }

    val api: SubsonicApi by lazy {
        val creds = ServerCredentials(server.url("/").toString().removeSuffix("/"), "sheet", "pw")
        SubsonicClientFactory.createApi(
            OkHttpClient.Builder().addInterceptor(SubsonicRequestInterceptor { CredentialsSource { creds } }).build(),
        )
    }

    override fun close() = server.close()

    companion object {
        fun ok(payload: String = "") =
            """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","openSubsonic":true${if (payload.isEmpty()) "" else ",$payload"}}}"""

        fun error(code: Int, message: String) =
            """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":$code,"message":"$message"}}}"""

        fun song(id: String, title: String = "Song $id", albumId: String = "al1", track: Int? = null, disc: Int? = null, starred: String? = null) =
            """{"id":"$id","title":"$title","album":"Album","albumId":"$albumId","artist":"Artist","artistId":"ar1","duration":200,"coverArt":"$albumId"${track?.let { ",\"track\":$it" } ?: ""}${disc?.let { ",\"discNumber\":$it" } ?: ""}${starred?.let { ",\"starred\":\"$it\"" } ?: ""}}"""

        fun album(id: String, name: String = "Album $id", artistId: String = "ar1", songs: List<String> = emptyList()) =
            """{"id":"$id","name":"$name","artist":"Artist","artistId":"$artistId","coverArt":"$id","songCount":${songs.size},"duration":600,"created":"2026-01-0${(id.hashCode() and 7) + 1}T10:00:00Z","song":[${songs.joinToString(",")}]}"""
    }
}
