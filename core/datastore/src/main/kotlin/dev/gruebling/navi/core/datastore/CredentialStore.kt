package dev.gruebling.navi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.gruebling.navi.core.model.ServerCredentials
import javax.inject.Inject
import kotlinx.coroutines.flow.first

interface CredentialStore {
    suspend fun load(): ServerCredentials?
    suspend fun save(credentials: ServerCredentials)
    suspend fun clear()
}

/** Server URL and user name in plain text, password encrypted with [SecretCipher]. */
class EncryptedCredentialStore @Inject constructor(
    @SessionPreferences private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val base64: Base64Codec,
) : CredentialStore {

    override suspend fun load(): ServerCredentials? {
        val prefs = dataStore.data.first()
        val url = prefs[SERVER_URL] ?: return null
        val user = prefs[USERNAME] ?: return null
        val encrypted = prefs[PASSWORD] ?: return null
        val password = runCatching { String(cipher.decrypt(base64.decode(encrypted)), Charsets.UTF_8) }
            .getOrElse { return null } // key gone (e.g. backup restored on a new phone): log in again
        return ServerCredentials(url, user, password)
    }

    override suspend fun save(credentials: ServerCredentials) {
        val encrypted = base64.encode(cipher.encrypt(credentials.password.toByteArray(Charsets.UTF_8)))
        dataStore.edit {
            it[SERVER_URL] = credentials.serverUrl
            it[USERNAME] = credentials.username
            it[PASSWORD] = encrypted
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(SERVER_URL)
            it.remove(USERNAME)
            it.remove(PASSWORD)
        }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password_enc")
    }
}

/** android.util.Base64 isn't available in plain JVM unit tests; java.util.Base64 needs API 26, which is our minSdk. */
class Base64Codec @Inject constructor() {
    fun encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)
}
