package dev.gruebling.navi

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.data.session.SessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MainViewModel @Inject constructor(session: SessionManager) : ViewModel() {
    val session: StateFlow<SessionState> = session.state
}
