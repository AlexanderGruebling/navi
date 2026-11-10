package dev.gruebling.navi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.gruebling.navi.core.data.session.SessionState
import dev.gruebling.navi.core.ui.theme.NaviTheme
import dev.gruebling.navi.feature.login.LoginScreen
import dev.gruebling.navi.ui.NaviApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash until we know whether a login exists (one small DataStore read), so there is no login flash.
        installSplashScreen().setKeepOnScreenCondition { viewModel.session.value == SessionState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NaviTheme {
                val session by viewModel.session.collectAsStateWithLifecycle()
                Crossfade(targetState = session is SessionState.LoggedIn, label = "session") { loggedIn ->
                    when {
                        loggedIn -> NaviApp()
                        session == SessionState.Loading -> Unit
                        else -> LoginScreen()
                    }
                }
            }
        }
    }
}
