package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.error
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.song
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MixRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: MixRepository

    @Before
    fun setUp() {
        server = FakeSubsonicServer()
        db = inMemoryDatabase()
        repo = MixRepository(server.api, db.songDao())
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun similar(vararg ids: String) = ok(""""similarSongs":{"song":[${ids.joinToString(",") { song(it) }}]}""")

    @Test
    fun `keeps server order without the seed and duplicates, and caches the songs`() = runTest {
        server.on("getSimilarSongs", similar("b", "seed", "a", "b"))
        val songs = repo.similarSongs("seed").getOrThrow()
        assertEquals(listOf("b", "a"), songs.map { it.id.value })
        assertEquals(2, db.songDao().getAll(listOf("a", "b")).size)
    }

    @Test
    fun `a song without similar songs falls back to its artist`() = runTest {
        server.on("getSimilarSongs") { url -> if (url.queryParameter("id") == "ar1") similar("x") else similar() }
        assertEquals(listOf("x"), repo.similarSongs("s1", fallbackArtistId = "ar1").getOrThrow().map { it.id.value })
        assertEquals(listOf("s1", "ar1"), server.requestsTo("getSimilarSongs").map { it.queryParameter("id") })
    }

    @Test
    fun `server errors are failures`() = runTest {
        server.on("getSimilarSongs", error(0, "no agents"))
        assertTrue(repo.similarSongs("s1").isFailure)
    }
}
