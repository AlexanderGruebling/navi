package dev.gruebling.navi.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.gruebling.navi.core.data.testing.inMemoryDatabase
import dev.gruebling.navi.core.database.NaviDatabase
import dev.gruebling.navi.core.datastore.NewReleaseStore
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.ReleaseTrack
import dev.gruebling.navi.core.model.ReleaseType
import dev.gruebling.navi.core.model.ServerCredentials
import dev.gruebling.navi.core.network.CredentialsSource
import dev.gruebling.navi.core.network.ReleaseFeedApi
import dev.gruebling.navi.core.network.SubsonicClientFactory
import dev.gruebling.navi.core.network.SubsonicRequestInterceptor
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewReleaseRepositoryTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<HttpUrl>()
    private var feed = ""
    private lateinit var db: NaviDatabase
    private lateinit var store: NewReleaseStore
    private lateinit var repo: NewReleaseRepository
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @get:Rule val tmp = TemporaryFolder()

    private fun release(
        id: String,
        title: String,
        type: String = "single",
        date: String = "2026-11-06",
        extra: String = "",
    ) = """{"id":"$id","title":"$title","type":"$type","date":"$date","artists":["Artist $id"],"link":"https://www.deezer.com/album/$id",
        "cover_medium":"https://img/$id-250.jpg","cover_big":"https://img/$id-500.jpg","first_seen":"2026-11-14","unknown_field":1$extra}"""

    private fun feedOf(vararg releases: String) = """{"version":1,"generated":"2026-11-14T11:08:35+01:00","releases":[${releases.joinToString(",")}]}"""

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request.url
                return MockResponse.Builder().body(feed).build()
            }
        }
        server.start()
        // The app's real client setup: one OkHttpClient with the Subsonic interceptor for everything.
        val creds = ServerCredentials("http://music.invalid", "sheet", "secret")
        val client = OkHttpClient.Builder().addInterceptor(SubsonicRequestInterceptor { CredentialsSource { creds } }).build()
        db = inMemoryDatabase()
        store = NewReleaseStore(PreferenceDataStoreFactory.create(scope = storeScope) { tmp.newFile("settings.preferences_pb").also { it.delete() } })
        repo = NewReleaseRepository(ReleaseFeedApi.create(client, SubsonicClientFactory.json), db.newReleaseDao(), store, SubsonicClientFactory.json)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
        storeScope.cancel()
    }

    private suspend fun useTestFeed() = store.setFeedUrl(server.url("/releases.json").toString())

    @Test
    fun `refresh maps the feed, newest release first`() = runTest {
        useTestFeed()
        feed = feedOf(
            release("deezer:1", "Older", date = "2026-10-01"),
            release(
                "deezer:2", "Truly, yours", type = "album", date = "2026-11-13",
                extra = ""","track_count":7,"duration":1272,"label":"recordJet","tracks":[{"title":"Phantom","duration":188}]""",
            ),
            release("deezer:3", "BLUE", type = "ep", date = "2026-10-23", extra = ",\"in_library\":true,\"navidrome_album_id\":\"623IP\""),
        )

        assertTrue(repo.refresh().isSuccess)
        val list = repo.observeReleases().first()

        assertEquals(listOf("deezer:2", "deezer:3", "deezer:1"), list.map { it.id })
        val album = list[0]
        assertEquals(ReleaseType.Album, album.type)
        assertEquals(LocalDate.of(2026, 11, 13), album.releaseDate)
        assertEquals(7, album.trackCount)
        assertEquals("recordJet", album.label)
        assertEquals(listOf(ReleaseTrack("Phantom", 188)), album.tracks)
        assertEquals("https://img/deezer:2-500.jpg", album.coverUrlLarge)
        assertFalse(album.inLibrary)
        assertTrue(list[1].inLibrary)
        assertEquals(AlbumId("623IP"), list[1].libraryAlbumId)
        assertTrue(list.none { it.seen || it.saved })
    }

    @Test
    fun `entries of unknown type or with a broken date are skipped`() = runTest {
        useTestFeed()
        feed = feedOf(release("a", "Ok"), release("b", "Compilation", type = "compile"), release("c", "Broken", date = "soon"))

        repo.refresh()

        assertEquals(listOf("a"), repo.observeReleases().first().map { it.id })
    }

    @Test
    fun `saved releases outlive the feed, seen marks of dropped ones are forgotten`() = runTest {
        useTestFeed()
        feed = feedOf(release("keep", "Saved one"), release("seen", "Seen one"), release("stay", "Still here"))
        repo.refresh()
        val list = repo.observeReleases().first()
        repo.setSaved(list.first { it.id == "keep" }, saved = true)
        repo.setSeen(listOf("seen", "stay"), seen = true)

        // Both drop out of the feed's time window.
        feed = feedOf(release("stay", "Still here"))
        repo.refresh()

        val after = repo.observeReleases().first()
        assertEquals(setOf("keep", "stay"), after.map { it.id }.toSet())
        assertTrue(after.first { it.id == "keep" }.saved)
        assertTrue(after.first { it.id == "stay" }.seen)
        assertEquals(setOf("stay"), store.seen.first())
    }

    @Test
    fun `feed requests carry no Subsonic credentials`() = runTest {
        useTestFeed()
        feed = feedOf(release("a", "Ok"))

        repo.refresh()

        val url = requests.single()
        assertEquals("/releases.json", url.encodedPath)
        assertTrue(url.queryParameterNames.isEmpty())
    }

    @Test
    fun `a blank feed URL turns the feature off`() = runTest {
        useTestFeed()
        feed = feedOf(release("a", "Ok"))
        repo.refresh()

        store.setFeedUrl("  ")
        assertTrue(repo.refresh().isSuccess)

        assertFalse(repo.enabled.first())
        assertTrue(repo.observeReleases().first().isEmpty())
        assertEquals(1, requests.size)
    }

    @Test
    fun `a failing feed keeps the cached releases`() = runTest {
        useTestFeed()
        feed = feedOf(release("a", "Ok"))
        repo.refresh()

        feed = "<html>502 Bad Gateway</html>"
        assertTrue(repo.refresh().isFailure)

        assertEquals(listOf("a"), repo.observeReleases().first().map { it.id })
    }

    @Test
    fun `logout forgets the marks but keeps the feed URL`() = runTest {
        useTestFeed()
        feed = feedOf(release("a", "Ok"))
        repo.refresh()
        repo.setSaved(repo.observeReleases().first().single(), saved = true)
        repo.setSeen(listOf("a"), seen = true)

        repo.clear()

        assertTrue(store.seen.first().isEmpty())
        assertTrue(store.saved.first().isEmpty())
        assertEquals(server.url("/releases.json").toString(), store.feedUrl.first())
    }

    @Test
    fun `unset feed URL means the homelab default`() = runTest {
        assertEquals(NewReleaseStore.DEFAULT_FEED_URL, store.feedUrl.first())
        assertNull(repo.observeRelease("nothing").first())
    }
}
