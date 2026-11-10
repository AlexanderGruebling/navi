package dev.gruebling.navi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
                // Animate on the screen, not on `session`: the outgoing content is recomposed with the
                // new state, so branching on `session` inside it drew the login screen while fading out.
                val screen = when (session) {
                    SessionState.Loading -> Screen.Loading
                    SessionState.LoggedOut -> Screen.Login
                    is SessionState.LoggedIn -> Screen.App
                }
                AnimatedContent(
                    targetState = screen,
                    label = "session",
                    // Leaving the splash: show the screen right away. Login <-> app (login, logout): fade.
                    transitionSpec = {
                        val transform = if (initialState == Screen.Loading) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn() togetherWith fadeOut()
                        }
                        // No size animation: Loading draws nothing, the app would grow out of a zero-size box.
                        transform using null
                    },
                    // Test tags become resource ids, so UI Automator (benchmarks, baseline profiles) can find them.
                    modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                ) { target ->
                    when (target) {
                        Screen.Loading -> Unit
                        Screen.Login -> LoginScreen()
                        Screen.App -> NaviApp()
                    }
                }
            }
        }
    }
}

private enum class Screen { Loading, Login, App }
