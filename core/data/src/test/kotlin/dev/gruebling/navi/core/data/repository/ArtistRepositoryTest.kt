package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.error
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.song
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.model.ArtistId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtistRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: ArtistRepository

    @Before
    fun setUp() {
        server = FakeSubsonicServer()
        db = inMemoryDatabase()
        repo = ArtistRepository(server.api, db.artistDao(), db.albumDao(), db.songDao())
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun artist(id: String, name: String = "Artist $id") =
        """{"id":"$id","name":"$name","coverArt":"ar-$id","albumCount":2}"""

    private fun info(similar: List<String>, bio: String = "") =
        ok(""""artistInfo2":{"biography":"$bio","lastFmUrl":"https://www.last.fm/music/A","similarArtist":[${similar.joinToString(",")}]}""")

    @Test
    fun `info keeps server order of top songs and similar artists`() = runTest {
        server.on("getArtistInfo2", info(listOf(artist("s2"), artist("s1"), artist("ar1"))))
        server.on("getTopSongs", ok(""""topSongs":{"song":[${song("t3")},${song("t1")},${song("t2")}]}"""))

        repo.refreshArtistInfo(ArtistId("ar1"), "Björk & Co").getOrThrow()

        val result = repo.observeArtistInfo(ArtistId("ar1")).first()
        assertEquals(listOf("t3", "t1", "t2"), result.topSongs.map { it.id.value })
        // The artist itself is never listed as similar.
        assertEquals(listOf("s2", "s1"), result.similarArtists.map { it.id.value })
        assertEquals("https://www.last.fm/music/A", result.lastFmUrl)
        assertEquals("Björk & Co", server.requestsTo("getTopSongs").single().queryParameter("artist"))
    }

    @Test
    fun `a newer answer replaces the old lists`() = runTest {
        var similar = listOf(artist("s1"), artist("s2"))
        server.on("getArtistInfo2") { info(similar) }
        server.on("getTopSongs", ok(""""topSongs":{"song":[]}"""))
        repo.refreshArtistInfo(ArtistId("ar1"), "A").getOrThrow()
        similar = listOf(artist("s3"))
        repo.refreshArtistInfo(ArtistId("ar1"), "A").getOrThrow()
        assertEquals(listOf("s3"), repo.observeArtistInfo(ArtistId("ar1")).first().similarArtists.map { it.id.value })
    }

    @Test
    fun `top songs failing still stores the biography`() = runTest {
        server.on("getArtistInfo2", info(emptyList(), bio = "Line one.\\n\\nLine &amp; two."))
        server.on("getTopSongs", error(0, "Last.fm not configured"))

        assertTrue(repo.refreshArtistInfo(ArtistId("ar1"), "A").isFailure)
        assertEquals("Line one.\n\nLine & two.", repo.observeArtistInfo(ArtistId("ar1")).first().biography)
    }

    @Test
    fun `similar artists keep their index letter`() = runTest {
        server.on("getArtists", ok(""""artists":{"index":[{"name":"S","artist":[${artist("s1")}]}]}"""))
        repo.refreshArtists().getOrThrow()
        server.on("getArtistInfo2", info(listOf(artist("s1"))))
        server.on("getTopSongs", ok(""""topSongs":{"song":[]}"""))
        repo.refreshArtistInfo(ArtistId("ar1"), "A").getOrThrow()
        assertEquals("S", repo.observeArtistInfo(ArtistId("ar1")).first().similarArtists.single().indexLetter)
    }

    @Test
    fun `biography drops the Last_fm link and tags`() {
        val html = "Icelandic singer.\n\n<b>Known</b> for <i>Homogenic</i>. " +
            "<a href=\"https://www.last.fm/music/Bj%C3%B6rk\">Read more on Last.fm</a>"
        assertEquals("Icelandic singer.\n\nKnown for Homogenic.", cleanBiography(html))
        assertNull(cleanBiography("  "))
        assertNull(cleanBiography("<a href=\"x\">Read more on Last.fm</a>"))
    }
}
