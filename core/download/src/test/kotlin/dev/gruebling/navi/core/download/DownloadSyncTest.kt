package dev.gruebling.navi.core.download

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.gruebling.navi.core.datastore.DownloadSettingsStore
import dev.gruebling.navi.core.model.DownloadTarget
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private val album = DownloadTarget(DownloadTarget.Kind.Album, "al1")
    private val playlist = DownloadTarget(DownloadTarget.Kind.Playlist, "pl1")

    private class FakeLibrary : DownloadLibrary {
        val songs = MutableStateFlow(mapOf<DownloadTarget, List<String>>())
        val failingRefresh = HashSet<DownloadTarget>()
        val refreshed = ArrayList<DownloadTarget>()
        override fun observeSongIds(target: DownloadTarget): Flow<List<String>> = songs.map { it[target].orEmpty() }
        override fun observeInfo(target: DownloadTarget): Flow<TargetInfo?> = songs.map { null }
        override suspend fun refresh(target: DownloadTarget): Boolean {
            refreshed += target
            return target !in failingRefresh
        }
    }

    private inner class Setup(scope: TestScope) {
        val settings = DownloadSettingsStore(
            PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler))) {
                tmp.newFile("settings.preferences_pb").also { it.delete() }
            },
        )
        val library = FakeLibrary()
        val files = DownloadFiles(tmp.newFolder("downloads"), StandardTestDispatcher(scope.testScheduler))
        val fetched = ArrayList<String>()
        var onFetch: suspend (String) -> Unit = {}
        val sync = DownloadSync(settings, library, files) { id, _, into ->
            onFetch(id)
            fetched += id
            into.writeText("audio of $id")
        }
    }

    @Test
    fun `downloads every song once and removes what no target needs`() = runTest {
        val s = Setup(this)
        s.files.write("old") { it.writeText("x") }
        s.library.songs.value = mapOf(album to listOf("a", "b"), playlist to listOf("b", "c"))
        s.settings.add(album)
        s.settings.add(playlist)

        assertEquals(DownloadSync.Outcome.Done, s.sync.run())

        assertEquals(listOf("a", "b", "c"), s.fetched)
        assertEquals(setOf("a", "b", "c"), s.files.downloadedNow())
        assertEquals("audio of b", s.files.fileIfDownloaded("b")!!.readText())
    }

    @Test
    fun `keeps unknown files when a target could not be reloaded`() = runTest {
        val s = Setup(this)
        s.files.write("old") { it.writeText("x") }
        s.library.songs.value = mapOf(album to listOf("a"))
        s.library.failingRefresh += album
        s.settings.add(album)

        assertEquals(DownloadSync.Outcome.Incomplete, s.sync.run())
        assertEquals(setOf("a", "old"), s.files.downloadedNow())
    }

    @Test
    fun `a refused song doesn't stop the others`() = runTest {
        val s = Setup(this)
        s.library.songs.value = mapOf(album to listOf("a", "gone", "c"))
        s.settings.add(album)
        s.onFetch = { if (it == "gone") throw SongUnavailableException("404") }

        assertEquals(DownloadSync.Outcome.Incomplete, s.sync.run())
        assertEquals(setOf("a", "c"), s.files.downloadedNow())
    }

    @Test
    fun `lost network stops the run and keeps finished songs`() = runTest {
        val s = Setup(this)
        s.library.songs.value = mapOf(album to listOf("a", "b", "c"))
        s.settings.add(album)
        s.onFetch = { if (it == "b") throw IOException("connection reset") }

        assertEquals(DownloadSync.Outcome.Retry, s.sync.run())
        assertEquals(setOf("a"), s.files.downloadedNow())
        // No half-written file is left behind.
        assertEquals(listOf("a"), File(tmp.root, "downloads").list()!!.toList())
    }

    @Test
    fun `a target added during the run is reloaded and downloaded`() = runTest {
        val s = Setup(this)
        s.library.songs.value = mapOf(album to listOf("a"), playlist to listOf("p"))
        s.settings.add(album)
        s.onFetch = { if (it == "a") s.settings.add(playlist) }

        assertEquals(DownloadSync.Outcome.Done, s.sync.run())
        assertEquals(setOf("a", "p"), s.files.downloadedNow())
        assertEquals(listOf(album, playlist), s.library.refreshed)
    }

    @Test
    fun `song ids with odd characters survive a restart`() = runTest {
        val dir = tmp.newFolder("odd")
        val io = StandardTestDispatcher(testScheduler)
        DownloadFiles(dir, io).write("a/b c:ä") { it.writeText("x") }
        assertEquals(setOf("a/b c:ä"), DownloadFiles(dir, io).downloadedNow())
    }
}
