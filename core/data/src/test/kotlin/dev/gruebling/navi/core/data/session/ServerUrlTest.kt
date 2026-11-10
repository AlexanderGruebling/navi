package dev.gruebling.navi.core.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test fun `adds https`() = assertEquals("https://music.example.com", ServerUrl.normalize("music.example.com"))
    @Test fun `trims and drops trailing slash`() = assertEquals("https://music.example.com", ServerUrl.normalize("  https://music.example.com/ "))
    @Test fun `keeps http and port`() = assertEquals("http://192.168.0.88:4533", ServerUrl.normalize("http://192.168.0.88:4533/"))
    @Test fun `strips pasted web UI path`() = assertEquals("https://music.example.com", ServerUrl.normalize("https://music.example.com/app/#/album/123/show"))
    @Test fun `strips pasted rest path and query`() = assertEquals("https://music.example.com", ServerUrl.normalize("https://music.example.com/rest/ping?u=x"))
    @Test fun `keeps a path prefix`() = assertEquals("https://example.com/navidrome", ServerUrl.normalize("https://example.com/navidrome/app/"))
    @Test fun `rejects garbage`() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("   "))
        assertNull(ServerUrl.normalize("ht!tp://"))
    }
}
