package dev.gruebling.navi.core.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer
import dev.gruebling.navi.core.data.testing.FakeSubsonicServer.Companion.ok
import dev.gruebling.navi.core.datastore.CredentialStore
import dev.gruebling.navi.core.datastore.ServerFeaturesStore
import dev.gruebling.navi.core.model.ServerCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerFeaturesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = FakeSubsonicServer()

    @After
    fun tearDown() = server.close()

    private object NoLogin : CredentialStore {
        override suspend fun load(): ServerCredentials? = null
        override suspend fun save(credentials: ServerCredentials) = Unit
        override suspend fun clear() = Unit
    }

    private fun TestScope.features(): Pair<ServerFeatures, ServerFeaturesStore> {
        val store = ServerFeaturesStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(tmp.root, "session.preferences_pb") })
        val session = SessionManager(server.api, NoLogin, backgroundScope)
        return ServerFeatures(server.api, store, session, backgroundScope) to store
    }

    @Test
    fun `a server with the extension supports mid-song starts`() = runTest {
        server.on("getOpenSubsonicExtensions", ok(""""openSubsonicExtensions":[{"name":"formPost","versions":[1]},{"name":"transcodeOffset","versions":[1]}]"""))
        val (features, store) = features()
        features.refresh()
        assertEquals(true, store.transcodeOffset.first())
    }

    @Test
    fun `an OpenSubsonic server without it does not`() = runTest {
        server.on("getOpenSubsonicExtensions", ok(""""openSubsonicExtensions":[{"name":"formPost","versions":[1]}]"""))
        val (features, store) = features()
        features.refresh()
        assertEquals(false, store.transcodeOffset.first())
    }

    @Test
    fun `a plain Subsonic server does not`() = runTest {
        // The fake server answers unknown endpoints with error 70, like a server without OpenSubsonic.
        val (features, store) = features()
        features.refresh()
        assertEquals(false, store.transcodeOffset.first())
    }

    @Test
    fun `unknown until checked, and assumed then`() = runTest {
        val (features, store) = features()
        assertNull(store.transcodeOffset.first())
        assertTrue(features.transcodeOffset.value)
        store.setTranscodeOffset(false)
        assertFalse(store.transcodeOffset.first()!!)
    }
}
