package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.testing.FakeClock
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.album
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.song
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.SearchResults
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: SearchRepository

    @Before
    fun setUp() {
        server = FakeSubsonicServer()
        db = inMemoryDatabase()
        repo = SearchRepository(server.api, db.songDao(), db.albumDao(), db.artistDao())
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun `local search finds cached songs and albums without asking the server`() = runTest {
        server.on("getAlbum", ok(""""album":${album("al1", name = "Blue Hour", songs = listOf(song("s1", title = "Blue"), song("s2", title = "Red")))}"""))
        AlbumRepository(server.api, db.albumDao(), db.songDao(), FakeClock()).refreshAlbum(AlbumId("al1")).getOrThrow()

        val results = repo.searchLocal("blue")

        assertEquals(listOf("s1"), results.songs.map { it.id.value })
        assertEquals(listOf("al1"), results.albums.map { it.id.value })
        assertTrue(server.requestsTo("search3").isEmpty())
    }

    @Test
    fun `remote results are returned and cached for the next local search`() = runTest {
        server.on(
            "search3",
            ok(""""searchResult3":{"artist":[{"id":"ar7","name":"Falco","albumCount":1}],"album":[${album("al7", name = "Einzelhaft", artistId = "ar7")}],"song":[${song("s7", title = "Der Kommissar", albumId = "al7")}]}"""),
        )

        val remote = repo.searchRemote("  falco ").getOrThrow()

        assertEquals("falco", server.requestsTo("search3").single().queryParameter("query"))
        assertEquals(listOf("ar7"), remote.artists.map { it.id.value })
        assertEquals(listOf("s7"), remote.songs.map { it.id.value })
        assertEquals(listOf("s7"), repo.searchLocal("kommissar").songs.map { it.id.value })
        assertEquals(listOf("ar7"), repo.searchLocal("falc").artists.map { it.id.value })
    }

    @Test
    fun `server error is a failure, not an empty result`() = runTest {
        server.on("search3", FakeSubsonicServer.error(0, "boom"))
        assertTrue(repo.searchRemote("x").isFailure)
    }

    @Test
    fun `blank query returns nothing and makes no request`() = runTest {
        assertEquals(SearchResults.Empty, repo.searchLocal("   "))
        assertEquals(SearchResults.Empty, repo.searchRemote("").getOrThrow())
        assertTrue(server.requestsTo("search3").isEmpty())
    }
}
