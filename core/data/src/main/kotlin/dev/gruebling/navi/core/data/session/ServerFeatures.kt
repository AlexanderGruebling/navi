package dev.gruebling.navi.core.data.session

import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.datastore.ServerFeaturesStore
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Optional server features the app depends on. Checked at every login and app start (a server may have been
 * updated), remembered for offline starts, forgotten at logout.
 */
@Singleton
class ServerFeatures @Inject constructor(
    private val api: SubsonicApi,
    private val store: ServerFeaturesStore,
    session: SessionManager,
    @ApplicationScope scope: CoroutineScope,
) {
    /**
     * Whether the server can start a transcoded stream mid-song (OpenSubsonic `transcodeOffset`), which seeking in
     * transcoded songs needs. Assumed until the first check, since Navidrome has it.
     */
    val transcodeOffset: StateFlow<Boolean> = store.transcodeOffset.map { it ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    init {
        scope.launch {
            session.state.collectLatest { state ->
                when (state) {
                    is SessionState.LoggedIn -> refresh()
                    SessionState.LoggedOut -> store.clear()
                    SessionState.Loading -> Unit
                }
            }
        }
    }

    suspend fun refresh() {
        val supported = try {
            api.getOpenSubsonicExtensions().ok().openSubsonicExtensions.orEmpty().any { it.name == TRANSCODE_OFFSET }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return // offline: keep what was known
        } catch (e: Exception) {
            false // an error answer or HTTP error: not an OpenSubsonic server
        }
        store.setTranscodeOffset(supported)
    }

    private companion object {
        const val TRANSCODE_OFFSET = "transcodeOffset"
    }
}
