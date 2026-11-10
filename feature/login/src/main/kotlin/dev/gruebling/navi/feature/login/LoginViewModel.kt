package dev.gruebling.navi.feature.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.session.LoginResult
import dev.gruebling.navi.core.data.session.SessionManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val inProgress: Boolean = false,
    val error: LoginResult? = null,
) {
    val canSubmit: Boolean get() = !inProgress && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
    val isPlainHttp: Boolean get() = serverUrl.trim().startsWith("http://", ignoreCase = true)
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    /** On success the session state flips to LoggedIn and the app leaves this screen by itself. */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(inProgress = true, error = null) }
        viewModelScope.launch {
            val result = session.login(current.serverUrl, current.username, current.password)
            _state.update {
                it.copy(inProgress = false, error = result.takeUnless { r -> r is LoginResult.Success })
            }
        }
    }
}
