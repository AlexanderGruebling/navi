package dev.gruebling.navi.core.data.repository

import app.cash.turbine.test
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.song
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.database.entity.PlaylistPrefsEntity
import dev.gruebling.navi.core.datastore.PlaylistSettingsStore
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.model.PlaylistSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {
    private lateinit var server: FakeSubsonicServer
    private lateinit var db: NaviDatabase
    private lateinit var repo: PlaylistRepository
    private lateinit var settings: PlaylistSettingsStore
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @get:Rule val tmp = TemporaryFolder()

    // Mirrors what the real server reported (2026-11-10): everything readonly, smart ones have validUntil.
    private var chartsSongs = listOf("s1", "s2", "s3")
    private var mixSongs = listOf("s4", "s5")
    private var chartsChanged = "2026-11-09T08:53:18.396665254+01:00"
    private var serverPlaylists = listOf("charts", "mix", "repeat")
    private var mineSongs = listOf("m1", "m2", "m3")

    private fun playlistJson(id: String): String = when (id) {
        "charts" -> """{"id":"charts","name":"FM4 Charts","comment":"Die aktuellen FM4 Charts","owner":"sheet","public":false,"readonly":true,"songCount":${chartsSongs.size},"duration":600,"changed":"$chartsChanged","coverArt":"pl-charts"}"""
        "mix" -> """{"id":"mix","name":"90s Mix","comment":"From Austropop to Eurodance.","owner":"sheet","readonly":true,"songCount":${mixSongs.size},"duration":400,"changed":"2026-11-10T16:34:44Z","validUntil":"2026-11-10T16:34:49Z"}"""
        "repeat" -> """{"id":"repeat","name":"On Repeat","comment":" The songs you just can't stop playing.","owner":"sheet","public":true,"readonly":true,"songCount":1,"duration":200,"changed":"2026-11-09T08:53:18Z","coverArt":"pl-repeat"}"""
        "mine" -> """{"id":"mine","name":"Road trip","owner":"sheet","public":false,"songCount":${mineSongs.size},"duration":300,"changed":"2026-11-10T10:00:00Z"}"""
        else -> error(id)
    }

    private fun songsOf(id: String) = when (id) {
        "charts" -> chartsSongs
        "mix" -> mixSongs
        "mine" -> mineSongs
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
        settings = PlaylistSettingsStore(PreferenceDataStoreFactory.create(scope = storeScope) { tmp.newFile("settings.preferences_pb").also { it.delete() } })
        repo = PlaylistRepository(server.api, db.playlistDao(), db.songDao(), db, settings)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
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
    fun `manual settings survive a wiped database`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        repo.updateSettings(PlaylistId("repeat"), PlaylistSettings(groupOverride = PlaylistGroup.ForYou, pinned = true))
        db.clearAllTables() // what a schema change does
        repo.refreshPlaylists().getOrThrow()
        val repeat = repo.observePlaylists().first().single { it.id.value == "repeat" }
        assertEquals(PlaylistGroup.ForYou, repeat.group)
        assertTrue(repeat.pinned)
    }

    @Test
    fun `settings from the old database table are taken over once`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        db.playlistDao().upsertPrefs(PlaylistPrefsEntity("repeat", groupOverride = "ForYou", pinned = true, sortOrder = 2))
        val repeat = repo.observePlaylists().first().single { it.id.value == "repeat" }
        assertEquals(PlaylistGroup.ForYou, repeat.groupOverride)
        assertTrue(repeat.pinned)
        assertEquals(2, repeat.sortOrder)

        // Later changes win over the old table, which is never read again.
        repo.updateSettings(PlaylistId("repeat"), PlaylistSettings())
        assertFalse(repo.observePlaylists().first().single { it.id.value == "repeat" }.pinned)
    }

    @Test
    fun `settings of deleted playlists and on logout are dropped`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        repo.updateSettings(PlaylistId("charts"), PlaylistSettings(pinned = true))
        repo.updateSettings(PlaylistId("mix"), PlaylistSettings(pinned = true))
        serverPlaylists = listOf("mix", "repeat")
        repo.refreshPlaylists().getOrThrow()
        assertEquals(setOf("mix"), settings.settings.first().keys)
        PlaylistSettingsCleaner(settings).clear()
        assertTrue(settings.settings.first().isEmpty())
    }

    @Test
    fun `group override is exposed and can be reset to automatic`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        repo.updateSettings(PlaylistId("charts"), PlaylistSettings(groupOverride = PlaylistGroup.Mine))
        val charts = repo.observePlaylists().first().single { it.id.value == "charts" }
        assertEquals(PlaylistGroup.Mine, charts.group)
        assertEquals(PlaylistGroup.Mine, charts.groupOverride)

        repo.updateSettings(PlaylistId("charts"), PlaylistSettings(groupOverride = null))
        val reset = repo.observePlaylists().first().single { it.id.value == "charts" }
        assertEquals(PlaylistGroup.Charts, reset.group)
        assertEquals(null, reset.groupOverride)
    }

    @Test
    fun `reorder sets the order within a group and keeps other settings`() = runTest {
        repo.refreshPlaylists().getOrThrow()
        // "On Repeat" joins "90s Mix" in For you, pinned off, then the user puts it second, then first.
        repo.updateSettings(PlaylistId("repeat"), PlaylistSettings(groupOverride = PlaylistGroup.ForYou))
        repo.reorder(listOf(PlaylistId("mix"), PlaylistId("repeat")))
        fun forYou() = repo.observePlaylists().map { list -> list.filter { it.group == PlaylistGroup.ForYou }.map { it.name } }
        assertEquals(listOf("90s Mix", "On Repeat"), forYou().first())
        repo.reorder(listOf(PlaylistId("repeat"), PlaylistId("mix")))
        assertEquals(listOf("On Repeat", "90s Mix"), forYou().first())
        assertEquals(PlaylistGroup.ForYou, repo.observePlaylists().first().single { it.id.value == "repeat" }.groupOverride)
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

    @Test
    fun `saving a long queue as a playlist sends the songs in order, in chunks`() = runTest {
        server.on("createPlaylist") { ok(""""playlist":{"id":"new","name":"${it.queryParameter("name")}","songCount":0}""") }
        server.on("updatePlaylist", ok())
        val ids = (1..250).map { "q$it" }

        assertEquals("new", repo.createPlaylist("My mix", ids).getOrThrow().value)

        val create = server.requestsTo("createPlaylist").single()
        assertEquals("My mix", create.queryParameter("name"))
        val updates = server.requestsTo("updatePlaylist")
        assertEquals(listOf("new", "new"), updates.map { it.queryParameter("playlistId") })
        val sent = create.queryParameterValues("songId") + updates.flatMap { it.queryParameterValues("songIdToAdd") }
        assertEquals(ids, sent)
        assertEquals(1, server.requestsTo("getPlaylists").size) // the list is reloaded so Home shows it
    }

    @Test
    fun `only the user's own plain playlists are editable`() = runTest {
        serverPlaylists = listOf("charts", "mix", "mine")
        repo.refreshPlaylists().getOrThrow()
        val editable = repo.observePlaylists().first().associate { it.id.value to it.isEditable }
        assertEquals(mapOf("charts" to false, "mix" to false, "mine" to true), editable)
    }

    @Test
    fun `adding songs appends them and reloads the playlist`() = runTest {
        serverPlaylists = listOf("mine")
        repo.refreshPlaylists().getOrThrow()
        server.on("updatePlaylist") { url ->
            mineSongs = mineSongs + url.queryParameterValues("songIdToAdd").filterNotNull()
            ok()
        }
        repo.addSongs(PlaylistId("mine"), listOf("x1", "x2")).getOrThrow()
        val songs = repo.observePlaylist(PlaylistId("mine")).first()!!.songs.map { it.id.value }
        assertEquals(listOf("m1", "m2", "m3", "x1", "x2"), songs)
    }

    @Test
    fun `saving an edit replaces the songs in their new order and renames`() = runTest {
        serverPlaylists = listOf("mine")
        repo.refreshPlaylists().getOrThrow()
        server.on("createPlaylist") { url ->
            mineSongs = url.queryParameterValues("songId").filterNotNull()
            ok()
        }
        server.on("updatePlaylist", ok())
        repo.saveEdit(PlaylistId("mine"), "Long road trip", listOf("m3", "m1")).getOrThrow()

        val replace = server.requestsTo("createPlaylist").single()
        assertEquals("mine", replace.queryParameter("playlistId"))
        assertEquals(null, replace.queryParameter("name"))
        assertEquals("Long road trip", server.requestsTo("updatePlaylist").single().queryParameter("name"))
        assertEquals(listOf("m3", "m1"), repo.observePlaylist(PlaylistId("mine")).first()!!.songs.map { it.id.value })
    }

    @Test
    fun `saving without a new name doesn't rename`() = runTest {
        serverPlaylists = listOf("mine")
        repo.refreshPlaylists().getOrThrow()
        server.on("createPlaylist", ok())
        repo.saveEdit(PlaylistId("mine"), "Road trip", listOf("m1")).getOrThrow()
        assertTrue(server.requestsTo("updatePlaylist").isEmpty())
    }

    @Test
    fun `deleting removes the playlist and its settings`() = runTest {
        serverPlaylists = listOf("mix", "mine")
        repo.refreshPlaylists().getOrThrow()
        repo.updateSettings(PlaylistId("mine"), PlaylistSettings(pinned = true))
        server.on("deletePlaylist", ok())
        repo.deletePlaylist(PlaylistId("mine")).getOrThrow()
        assertEquals(listOf("mix"), repo.observePlaylists().first().map { it.id.value })
        assertTrue(settings.settings.first().isEmpty())
        assertEquals("mine", server.requestsTo("deletePlaylist").single().queryParameter("id"))
    }
}
