package dev.gruebling.navi.core.network

import dev.gruebling.navi.core.model.ServerCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SubsonicUrlsTest {
    private val creds = ServerCredentials("https://example.com", "anna", "sesame")

    @Test
    fun `adds all auth parameters`() {
        val url = SubsonicUrls.build(creds, listOf("rest", "ping"), salt = "c19b2d")
        assertEquals("/rest/ping", url.encodedPath)
        assertEquals("anna", url.queryParameter("u"))
        assertEquals("26719a1196d2a940705a59634eb18eab", url.queryParameter("t"))
        assertEquals("c19b2d", url.queryParameter("s"))
        assertEquals(SubsonicAuth.API_VERSION, url.queryParameter("v"))
        assertEquals(SubsonicAuth.CLIENT_NAME, url.queryParameter("c"))
        assertEquals("json", url.queryParameter("f"))
        assertEquals(null, url.queryParameter("p")) // never the plain password
    }

    @Test
    fun `keeps a path prefix of the server`() {
        val prefixed = creds.copy(serverUrl = "https://example.com/navidrome")
        assertEquals("/navidrome/rest/ping", SubsonicUrls.build(prefixed, listOf("rest", "ping")).encodedPath)
    }

    @Test
    fun `cover sizes round up to fixed buckets`() {
        assertEquals(150, SubsonicUrls.coverSizeFor(48))
        assertEquals(300, SubsonicUrls.coverSizeFor(151))
        assertEquals(600, SubsonicUrls.coverSizeFor(600))
        assertEquals(1200, SubsonicUrls.coverSizeFor(5000))
        assertEquals("600", SubsonicUrls.coverArtUrl(creds, "al-1", 420).queryParameter("size"))
    }

    @Test
    fun `cache keys are stable and contain no auth`() {
        assertEquals(SubsonicUrls.coverArtCacheKey("al-1", 420), SubsonicUrls.coverArtCacheKey("al-1", 500))
        assertFalse(SubsonicUrls.coverArtCacheKey("al-1", 420).contains("sesame"))
        assertEquals("stream:s1:320:mp3", SubsonicUrls.streamCacheKey("s1", 320, "mp3"))
        assertEquals("stream:s1:0:raw", SubsonicUrls.streamCacheKey("s1"))
    }

    @Test
    fun `time offset is sent only when set and gets its own cache key`() {
        val from = SubsonicUrls.streamUrl(creds, "s1", 128, "opus", timeOffsetSec = 60)
        assertEquals("60", from.queryParameter("timeOffset"))
        assertEquals("opus", from.queryParameter("format"))
        assertFalse("timeOffset" in SubsonicUrls.streamUrl(creds, "s1", 128, "opus").queryParameterNames)
        assertFalse("format" in SubsonicUrls.streamUrl(creds, "s1").queryParameterNames)
        assertEquals("stream:s1:128:opus@60", SubsonicUrls.streamCacheKey("s1", 128, "opus", 60))
        assertEquals("stream:s1:128:opus", SubsonicUrls.streamCacheKey("s1", 128, "opus", 0))
    }
}
