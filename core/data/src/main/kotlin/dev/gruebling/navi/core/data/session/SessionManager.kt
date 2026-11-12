package dev.gruebling.navi.core.data.session

import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.datastore.CredentialStore
import dev.gruebling.navi.core.model.ServerCredentials
import dev.gruebling.navi.core.network.CredentialsSource
import dev.gruebling.navi.core.network.HttpsDowngradeException
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.SubsonicException
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val serverUrl: String, val username: String) : SessionState
}

sealed interface LoginResult {
    data class Success(val serverType: String?, val serverVersion: String?, val openSubsonic: Boolean) : LoginResult
    data object InvalidUrl : LoginResult
    data object WrongCredentials : LoginResult
    data object UnknownHost : LoginResult
    data object Unreachable : LoginResult
    data object TlsError : LoginResult
    /** The address answers, but not like a Subsonic server (wrong URL / path). */
    data object NotSubsonic : LoginResult
    data class ServerError(val message: String?) : LoginResult
}

/** Owns the login: restores it at startup, verifies new logins against the server, and serves it to the HTTP layer. */
@Singleton
class SessionManager @Inject constructor(
    private val api: SubsonicApi,
    private val store: CredentialStore,
    @ApplicationScope scope: CoroutineScope,
) : CredentialsSource {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile private var credentials: ServerCredentials? = null

    init {
        scope.launch {
            val restored = store.load()
            if (_state.value == SessionState.Loading) setCredentials(restored)
        }
    }

    override fun current(): ServerCredentials? = credentials

    suspend fun login(serverUrl: String, username: String, password: String): LoginResult {
        val url = ServerUrl.normalize(serverUrl) ?: return LoginResult.InvalidUrl
        val candidate = ServerCredentials(url, username.trim(), password)
        val result = try {
            val response = api.ping(candidate).response
            when {
                response.isOk -> LoginResult.Success(response.type, response.serverVersion, response.openSubsonic)
                response.error?.code == SubsonicException.WRONG_CREDENTIALS -> LoginResult.WrongCredentials
                else -> LoginResult.ServerError(response.error?.message)
            }
        } catch (e: HttpException) {
            if (e.code() == 404 || e.code() == 405) LoginResult.NotSubsonic else LoginResult.ServerError("HTTP ${e.code()}")
        } catch (e: SerializationException) {
            LoginResult.NotSubsonic
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization reports some malformed input this way (e.g. an HTML page)
            LoginResult.NotSubsonic
        } catch (e: UnknownHostException) {
            LoginResult.UnknownHost
        } catch (e: SSLException) {
            LoginResult.TlsError
        } catch (e: HttpsDowngradeException) {
            // An https address that sends us to plain http: as unsafe as a broken certificate.
            LoginResult.TlsError
        } catch (e: IOException) {
            LoginResult.Unreachable
        }
        if (result is LoginResult.Success) {
            store.save(candidate)
            setCredentials(candidate)
        }
        return result
    }

    suspend fun logout() {
        store.clear()
        setCredentials(null)
    }

    private fun setCredentials(value: ServerCredentials?) {
        credentials = value
        _state.value = value?.let { SessionState.LoggedIn(it.serverUrl, it.username) } ?: SessionState.LoggedOut
    }
}
