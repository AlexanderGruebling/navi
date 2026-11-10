package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.model.SongId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrobbleRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: ScrobbleRepository

    @Before
    fun setUp() {
        server = FakeSubsonicServer()
        server.on("scrobble", ok())
        db = inMemoryDatabase()
        repo = ScrobbleRepository(server.api, db.playbackDao())
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun submitted() = server.requestsTo("scrobble")
        .filter { it.queryParameter("submission") == "true" }
        .map { it.queryParameter("id") to it.queryParameter("time") }

    @Test
    fun `play is sent with its original time`() = runTest {
        repo.submit(SongId("s1"), playedAtMs = 1_000)
        assertEquals(listOf("s1" to "1000"), submitted())
        assertTrue(db.playbackDao().pendingScrobbles(10).isEmpty())
    }

    @Test
    fun `offline plays are kept and sent in order later`() = runTest {
        server.close() // connection refused = IOException in the client
        repo.submit(SongId("s1"), 1_000)
        repo.submit(SongId("s2"), 2_000)
        assertEquals(2, db.playbackDao().pendingScrobbles(10).size)

        server = FakeSubsonicServer().apply { on("scrobble", ok()) }
        repo = ScrobbleRepository(server.api, db.playbackDao())
        repo.flush()
        assertEquals(listOf("s1" to "1000", "s2" to "2000"), submitted())
        assertTrue(db.playbackDao().pendingScrobbles(10).isEmpty())
    }

    @Test
    fun `plays the server rejects are dropped, not retried forever`() = runTest {
        server.on("scrobble", FakeSubsonicServer.error(70, "Song not found"))
        repo.submit(SongId("gone"), 1_000)
        assertTrue(db.playbackDao().pendingScrobbles(10).isEmpty())
    }
}
