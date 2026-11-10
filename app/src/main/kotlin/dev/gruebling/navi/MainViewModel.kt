package dev.gruebling.navi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.data.session.SessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    session: SessionManager,
    debugProbe: DebugServerProbe,
) : ViewModel() {
    val session: StateFlow<SessionState> = session.state

    init {
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                session.state.filterIsInstance<SessionState.LoggedIn>().first()
                debugProbe.run()
            }
        }
    }
}
