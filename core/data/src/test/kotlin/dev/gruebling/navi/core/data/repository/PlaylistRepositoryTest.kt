package dev.gruebling.navi.core.data.repository

import app.cash.turbine.test
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.song
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.model.PlaylistSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: PlaylistRepository

    // Mirrors what the real server reported (2026-11-10): everything readonly, smart ones have validUntil.
    private var chartsSongs = listOf("s1", "s2", "s3")
    private var mixSongs = listOf("s4", "s5")
    private var chartsChanged = "2026-11-09T08:53:18.396665254+01:00"
    private var serverPlaylists = listOf("charts", "mix", "repeat")

    private fun playlistJson(id: String): String = when (id) {
        "charts" -> """{"id":"charts","name":"FM4 Charts","comment":"Die aktuellen FM4 Charts","owner":"sheet","public":false,"readonly":true,"songCount":${chartsSongs.size},"duration":600,"changed":"$chartsChanged","coverArt":"pl-charts"}"""
        "mix" -> """{"id":"mix","name":"90s Mix","comment":"From Austropop to Eurodance.","owner":"sheet","readonly":true,"songCount":${mixSongs.size},"duration":400,"changed":"2026-11-10T16:34:44Z","validUntil":"2026-11-10T16:34:49Z"}"""
        "repeat" -> """{"id":"repeat","name":"On Repeat","comment":" The songs you just can't stop playing.","owner":"sheet","public":true,"readonly":true,"songCount":1,"duration":200,"changed":"2026-11-09T08:53:18Z","coverArt":"pl-repeat"}"""
        else -> error(id)
    }

    private fun songsOf(id: String) = when (id) {
        "charts" -> chartsSongs
        "mix" -> mixSongs
        else -> listOf("s9")
    }

    @Before
    fun setUp() {
        server = FakeSubsonicServer()
        server.on("getPlaylists") { ok(""""playlists":{"playlist":[${serverPlaylists.joinToString(",") { playlistJson(it) }}]}""") }
        server.on("getPlaylist") { url ->
            val id = url.queryParameter("id")!!
            val entries = songsOf(id).mapIndexed { i, s -> song(s, albumId = "al${i % 5}") }.joinToString(",")
            ok(""""playlist":${playlistJson(id).dropLast(1)},"entry":[$entries]}""")
        }
        db = inMemoryDatabase()
        repo = PlaylistRepository(server.api, db.playlistDao(), db.songDao())
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun `refresh stores playlists grouped and in Home order`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        val playlists = repo.observePlaylists().first()
        assertEquals(listOf("FM4 Charts", "90s Mix", "On Repeat"), playlists.map { it.name })
        assertEquals(listOf(PlaylistGroup.Charts, PlaylistGroup.ForYou, PlaylistGroup.Mine), playlists.map { it.group })
        assertTrue(playlists[1].isSmart)
        assertEquals("The songs you just can't stop playing.", playlists[2].comment)
        assertTrue(playlists.none { it.hasNewContent }) // first sync is the baseline
    }

    @Test
    fun `cached data is shown first, then the refreshed data`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        repo.observePlaylists().test {
            assertEquals(3, awaitItem().size) // from cache
            serverPlaylists = listOf("charts", "mix")
            repo.refreshPlaylists().getOrThrow()
            assertEquals(listOf("FM4 Charts", "90s Mix"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new chart week gets the Neu badge until opened`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        chartsSongs = listOf("s3", "s1", "s7")
        chartsChanged = "2026-11-16T02:44:00+01:00"
        repo.refreshPlaylists().getOrThrow()
        val charts = repo.observePlaylists().first().first { it.name == "FM4 Charts" }
        assertTrue(charts.hasNewContent)

        repo.markSeen(charts.id)
        assertFalse(repo.observePlaylists().first().first { it.name == "FM4 Charts" }.hasNewContent)
    }

    @Test
    fun `rescan without content change gives no badge`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        chartsChanged = "2026-11-10T08:00:00+01:00" // only the timestamp moved
        repo.refreshPlaylists().getOrThrow()
        assertFalse(repo.observePlaylists().first().first { it.name == "FM4 Charts" }.hasNewContent)
    }

    @Test
    fun `reshuffled smart playlist never gets the badge`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        mixSongs = listOf("s5", "s4")
        repo.refreshPlaylist(PlaylistId("mix")).getOrThrow()
        assertFalse(repo.observePlaylists().first().first { it.name == "90s Mix" }.hasNewContent)
    }

    @Test
    fun `unchanged non-smart playlists are not reloaded`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        server.requests.clear()
        repo.refreshPlaylists().getOrThrow()
        val reloaded = server.requestsTo("getPlaylist").map { it.queryParameter("id") }
        assertEquals(listOf("mix"), reloaded) // only the smart one
    }

    @Test
    fun `playlist detail has songs in order and a mosaic of distinct covers`() = runTest {
        chartsSongs = listOf("a", "b", "c", "d", "e", "f", "g")
        repo.refreshPlaylist(PlaylistId("charts")).getOrThrow()
        val detail = repo.observePlaylist(PlaylistId("charts")).first()!!
        assertEquals(chartsSongs, detail.songs.map { it.id.value })
        assertEquals(listOf("al0", "al1", "al2", "al3"), detail.mosaic.map { it.value })
    }

    @Test
    fun `manual settings survive refreshes`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        repo.updateSettings(PlaylistId("repeat"), PlaylistSettings(groupOverride = PlaylistGroup.ForYou, pinned = true))
        repo.refreshPlaylists().getOrThrow()
        val forYou = repo.observePlaylists().first().filter { it.group == PlaylistGroup.ForYou }
        assertEquals(listOf("On Repeat", "90s Mix"), forYou.map { it.name }) // pinned first
    }

    @Test
    fun `deleted playlists disappear`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        serverPlaylists = listOf("mix")
        repo.refreshPlaylists().getOrThrow()
        assertEquals(listOf("90s Mix"), repo.observePlaylists().first().map { it.name })
        assertTrue(db.playlistDao().observeSongs("charts").first().isEmpty())
    }

    @Test
    fun `server error keeps the cache`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        server.on("getPlaylists", FakeSubsonicServer.error(0, "boom"))
        assertTrue(repo.refreshPlaylists().isFailure)
        assertEquals(3, repo.observePlaylists().first().size)
    }
}
