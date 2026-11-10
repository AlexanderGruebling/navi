package dev.gruebling.navi.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.gruebling.navi.core.model.ServerCredentials
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedCredentialStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Reversible stand-in for the Keystore cipher. */
    private class XorCipher(var key: Byte = 0x5A) : SecretCipher {
        override fun encrypt(plain: ByteArray) = byteArrayOf(key) + plain.map { (it.toInt() xor key.toInt()).toByte() }
        override fun decrypt(encrypted: ByteArray): ByteArray {
            require(encrypted[0] == key) { "wrong key" }
            return encrypted.drop(1).map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
        }
    }

    private fun TestScope.store(cipher: SecretCipher = XorCipher()): Pair<EncryptedCredentialStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "session.preferences_pb") }
        return EncryptedCredentialStore(ds, cipher, Base64Codec()) to ds
    }

    private val creds = ServerCredentials("https://music.example.com", "anna", "sesame & \$ecret")

    @Test
    fun `round trip`() = runTest {
        val (store, _) = store()
        assertNull(store.load())
        store.save(creds)
        assertEquals(creds, store.load())
    }

    @Test
    fun `password is not stored in plain text`() = runTest {
        val (store, ds) = store()
        store.save(creds)
        val raw = ds.data.first()[stringPreferencesKey("password_enc")]!!
        assertFalse(raw.contains("sesame"))
    }

    @Test
    fun `undecryptable password means logged out`() = runTest {
        val cipher = XorCipher()
        val (store, _) = store(cipher)
        store.save(creds)
        cipher.key = 0x11 // simulates a lost Keystore key
        assertNull(store.load())
    }

    @Test
    fun `clear removes everything`() = runTest {
        val (store, _) = store()
        store.save(creds)
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `toString never shows the password`() {
        assertFalse(creds.toString().contains("sesame"))
    }
}
