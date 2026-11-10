package dev.gruebling.navi.core.playback

import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.data.session.LocalDataCleaner
import dev.gruebling.navi.core.data.session.SessionManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Logout: stop playback, delete the account's cached data, then forget the credentials (which switches the UI to
 * the login screen). Runs in the application scope so leaving the settings screen can't cut it short.
 */
class SignOut @Inject constructor(
    private val playback: PlaybackController,
    private val cleaner: LocalDataCleaner,
    private val session: SessionManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    operator fun invoke(): Job = scope.launch {
        playback.stopAndClear()
        cleaner.clear()
        session.logout()
    }
}
