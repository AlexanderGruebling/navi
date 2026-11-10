package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.testing.FakeClock
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.album
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.song
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.SongId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: FavoritesRepository

    @Before
    fun setUp() = runTest {
        server = FakeSubsonicServer()
        db = inMemoryDatabase()
        repo = FavoritesRepository(server.api, db, db.songDao(), db.albumDao(), db.artistDao(), FakeClock())
        server.on("getAlbum", ok(""""album":${album("al1", songs = listOf(song("s1"), song("s2")))}"""))
        AlbumRepository(server.api, db.albumDao(), db.songDao(), FakeClock()).refreshAlbum(AlbumId("al1")).getOrThrow()
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private suspend fun starredSongs() = repo.observeFavorites().first().songs.map { it.id.value }

    @Test
    fun `starring is saved locally and sent to the server`() = runTest {
        server.on("star", ok())
        assertTrue(repo.setStarred(SongId("s1"), true).isSuccess)
        assertEquals(listOf("s1"), starredSongs())
        assertEquals("s1", server.requestsTo("star").single().queryParameter("id"))
    }

    @Test
    fun `failed star is rolled back`() = runTest {
        server.on("star", FakeSubsonicServer.error(50, "not allowed"))
        assertTrue(repo.setStarred(SongId("s1"), true).isFailure)
        assertEquals(emptyList<String>(), starredSongs())
    }

    @Test
    fun `failed unstar restores the star`() = runTest {
        server.on("star", ok())
        repo.setStarred(SongId("s2"), true)
        server.on("unstar", FakeSubsonicServer.error(0, "offline"))
        assertTrue(repo.setStarred(SongId("s2"), false).isFailure)
        assertEquals(listOf("s2"), starredSongs())
    }

    @Test
    fun `refresh replaces local stars with the server state`() = runTest {
        server.on("star", ok())
        repo.setStarred(SongId("s1"), true)
        server.on("getStarred2", ok(""""starred2":{"song":[${song("s2", starred = "2026-11-01T10:00:00Z")}],"album":[${album("al9")}],"artist":[]}"""))
        repo.refreshFavorites().getOrThrow()
        val favorites = repo.observeFavorites().first()
        assertEquals(listOf("s2"), favorites.songs.map { it.id.value })
        // listed in getStarred2 without a date still counts as starred
        assertEquals(listOf("al9"), favorites.albums.map { it.id.value })
    }
}
