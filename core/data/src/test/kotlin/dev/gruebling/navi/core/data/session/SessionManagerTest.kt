package dev.gruebling.navi.core.data.session

import dev.gruebling.navi.core.datastore.CredentialStore
import dev.gruebling.navi.core.model.ServerCredentials
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.SubsonicClientFactory
import dev.gruebling.navi.core.network.SubsonicRequestInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {
    private val server = MockWebServer()
    private val store = FakeCredentialStore()
    private lateinit var api: SubsonicApi
    private var session: SessionManager? = null

    private class FakeCredentialStore(var saved: ServerCredentials? = null) : CredentialStore {
        override suspend fun load() = saved
        override suspend fun save(credentials: ServerCredentials) { saved = credentials }
        override suspend fun clear() { saved = null }
    }

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .addInterceptor(SubsonicRequestInterceptor { session!! })
            .build()
        api = SubsonicClientFactory.createApi(client)
    }

    @After
    fun tearDown() = server.close()

    private fun TestScope.newSession(): SessionManager =
        SessionManager(api, store, this).also { session = it }

    private val serverUrl get() = server.url("/").toString()

    private fun respond(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private val ok = """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.64.2","openSubsonic":true}}"""
    private val wrong = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}"""

    @Test
    fun `restores a saved login at startup`() = runTest {
        store.saved = ServerCredentials("https://music.example.com", "anna", "pw")
        val s = newSession()
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn("https://music.example.com", "anna"), s.state.value)
        assertEquals("pw", s.current()?.password)
    }

    @Test
    fun `logged out when nothing is saved`() = runTest {
        val s = newSession()
        advanceUntilIdle()
        assertEquals(SessionState.LoggedOut, s.state.value)
        assertNull(s.current())
    }

    @Test
    fun `successful login is saved and used for later requests`() = runTest {
        val s = newSession(); advanceUntilIdle()
        respond(ok)
        val result = s.login(serverUrl, " anna ", "pw")
        assertEquals(LoginResult.Success("navidrome", "0.64.2", true), result)
        assertEquals("anna", store.saved?.username)
        assertTrue(s.state.value is SessionState.LoggedIn)

        respond(ok)
        api.ping() // uses the stored login now
        server.takeRequest()
        assertEquals("anna", server.takeRequest().url.queryParameter("u"))
    }

    @Test
    fun `wrong password is reported and not saved`() = runTest {
        val s = newSession(); advanceUntilIdle()
        respond(wrong)
        assertEquals(LoginResult.WrongCredentials, s.login(serverUrl, "anna", "nope"))
        assertNull(store.saved)
        assertEquals(SessionState.LoggedOut, s.state.value)
    }

    @Test
    fun `HTML page means not a Subsonic server`() = runTest {
        val s = newSession(); advanceUntilIdle()
        respond("<html><body>Caddy works!</body></html>")
        assertEquals(LoginResult.NotSubsonic, s.login(serverUrl, "anna", "pw"))
    }

    @Test
    fun `404 means not a Subsonic server`() = runTest {
        val s = newSession(); advanceUntilIdle()
        respond("not found", code = 404)
        assertEquals(LoginResult.NotSubsonic, s.login(serverUrl, "anna", "pw"))
    }

    @Test
    fun `server that is down is unreachable`() = runTest {
        val s = newSession(); advanceUntilIdle()
        val url = serverUrl
        server.close()
        assertEquals(LoginResult.Unreachable, s.login(url, "anna", "pw"))
    }

    @Test
    fun `invalid address is rejected without a request`() = runTest {
        val s = newSession(); advanceUntilIdle()
        assertEquals(LoginResult.InvalidUrl, s.login("  ", "anna", "pw"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `logout clears the login`() = runTest {
        store.saved = ServerCredentials("https://music.example.com", "anna", "pw")
        val s = newSession(); advanceUntilIdle()
        s.logout()
        assertNull(store.saved)
        assertNull(s.current())
        assertEquals(SessionState.LoggedOut, s.state.value)
    }
}
