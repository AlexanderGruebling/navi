package dev.gruebling.navi.core.network

import dev.gruebling.navi.core.model.ServerCredentials
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SubsonicApiTest {
    private val server = MockWebServer()
    private var stored: ServerCredentials? = null
    private lateinit var api: SubsonicApi

    @Before
    fun setUp() {
        server.start()
        stored = ServerCredentials(server.url("/").toString().removeSuffix("/"), "anna", "sesame")
        val client = OkHttpClient.Builder()
            .addInterceptor(SubsonicRequestInterceptor { CredentialsSource { stored } })
            .build()
        api = SubsonicClientFactory.createApi(client)
    }

    @After
    fun tearDown() = server.close()

    private fun enqueueFixture(name: String) {
        val body = javaClass.getResource("/fixtures/$name")!!.readText()
        server.enqueue(MockResponse.Builder().body(body).build())
    }

    @Test
    fun `requests go to the stored server with token auth`() = runTest {
        enqueueFixture("ping_ok.json")
        val response = api.ping().ok()
        assertEquals("navidrome", response.type)
        assertTrue(response.openSubsonic)

        val url = server.takeRequest().url
        assertEquals("/rest/ping", url.encodedPath)
        assertEquals("anna", url.queryParameter("u"))
        val salt = url.queryParameter("s")!!
        assertEquals(SubsonicAuth.token("sesame", salt), url.queryParameter("t"))
        assertEquals("json", url.queryParameter("f"))
    }

    @Test
    fun `credentials tag overrides the stored login`() = runTest {
        enqueueFixture("ping_ok.json")
        val candidate = stored!!.copy(username = "bert", password = "other")
        api.ping(candidate)
        val url = server.takeRequest().url
        assertEquals("bert", url.queryParameter("u"))
        assertEquals(SubsonicAuth.token("other", url.queryParameter("s")!!), url.queryParameter("t"))
    }

    @Test
    fun `query parameters are kept`() = runTest {
        enqueueFixture("ping_ok.json")
        api.getAlbumList2(type = "newest", size = 20, offset = 40)
        val url = server.takeRequest().url
        assertEquals("newest", url.queryParameter("type"))
        assertEquals("20", url.queryParameter("size"))
        assertEquals("40", url.queryParameter("offset"))
    }

    @Test
    fun `repeated query parameters for star`() = runTest {
        enqueueFixture("ping_ok.json")
        api.star(songIds = listOf("a", "b"))
        assertEquals(listOf("a", "b"), server.takeRequest().url.queryParameterValues("id"))
    }

    @Test
    fun `failed status becomes SubsonicException`() = runTest {
        enqueueFixture("error_wrong_credentials.json")
        val envelope = api.ping()
        assertFalse(envelope.response.isOk)
        try {
            envelope.ok()
            fail("expected SubsonicException")
        } catch (e: SubsonicException) {
            assertEquals(SubsonicException.WRONG_CREDENTIALS, e.code)
        }
    }

    @Test
    fun `parses playlists with OpenSubsonic fields and ignores unknown ones`() = runTest {
        enqueueFixture("playlists.json")
        val playlists = api.getPlaylists().ok().playlists!!.playlist
        assertEquals(2, playlists.size)
        val charts = playlists[0]
        assertEquals("FM4 Charts", charts.name)
        assertEquals("sheet", charts.owner)
        assertEquals("2026-11-08T02:44:07.000Z", charts.changed)
        assertFalse(charts.readonly)
        val smart = playlists[1]
        assertTrue(smart.readonly)
        assertEquals("2026-11-10T08:05:00Z", smart.validUntil)
    }

    @Test
    fun `no login means no request`() = runTest {
        stored = null
        try {
            api.ping()
            fail("expected NotLoggedInException")
        } catch (e: NotLoggedInException) {
            assertEquals(0, server.requestCount)
        }
    }
}
