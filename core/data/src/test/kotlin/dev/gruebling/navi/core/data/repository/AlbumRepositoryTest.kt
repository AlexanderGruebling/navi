package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.testing.FakeClock
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.album
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.song
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.AlbumListType
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: AlbumRepository
    private val clock = FakeClock()

    @Before
    fun setUp() {
        server = FakeSubsonicServer()
        db = inMemoryDatabase()
        repo = AlbumRepository(server.api, db.albumDao(), db.songDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun `album detail sorts songs by disc and track`() = runTest {
        server.on(
            "getAlbum",
            ok(""""album":${album("al1", songs = listOf(song("b", track = 1, disc = 2), song("c", track = 2, disc = 1), song("a", track = 1, disc = 1)))}"""),
        )
        repo.refreshAlbum(AlbumId("al1")).getOrThrow()
        val detail = repo.observeAlbum(AlbumId("al1")).first()!!
        assertEquals(listOf("a", "c", "b"), detail.songs.map { it.id.value })
        assertEquals(3, detail.album.songCount)
    }

    @Test
    fun `home sections keep server order and replace old entries`() = runTest {
        var ids = listOf("x", "y", "z")
        server.on("getAlbumList2") { ok(""""albumList2":{"album":[${ids.joinToString(",") { album(it) }}]}""") }
        repo.refreshAlbumList(AlbumListType.RecentlyPlayed).getOrThrow()
        assertEquals(ids, repo.observeAlbumList(AlbumListType.RecentlyPlayed).first().map { it.id.value })
        ids = listOf("z", "w")
        repo.refreshAlbumList(AlbumListType.RecentlyPlayed).getOrThrow()
        assertEquals(ids, repo.observeAlbumList(AlbumListType.RecentlyPlayed).first().map { it.id.value })
        assertEquals("recent", server.requests.last().queryParameter("type"))
    }

    @Test
    fun `library sync pages through everything and drops deleted albums`() = runTest {
        var library = (1..7).map { "a$it" }
        server.on("getAlbumList2") { url ->
            val size = url.queryParameter("size")!!.toInt()
            val offset = url.queryParameter("offset")!!.toInt()
            ok(""""albumList2":{"album":[${library.drop(offset).take(size).joinToString(",") { album(it) }}]}""")
        }
        assertEquals(7, repo.syncLibrary(pageSize = 3).getOrThrow())
        assertEquals(listOf(0, 3, 6), server.requests.map { it.queryParameter("offset")!!.toInt() })
        assertEquals(7, db.albumDao().count())

        library = library - "a2" - "a5"
        clock.now += 1.hours
        assertEquals(5, repo.syncLibrary(pageSize = 3).getOrThrow())
        assertEquals(5, db.albumDao().count())
    }
}
