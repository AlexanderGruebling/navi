package dev.gruebling.navi.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.data.session.LoginResult
import dev.gruebling.navi.core.ui.component.DotwaveLogo
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.theme.NaviType
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onServerUrlChange = viewModel::onServerUrlChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
    )
}

@Composable
internal fun LoginContent(
    state: LoginUiState,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Brand: the Dotwave mark, the name in dot-matrix type and the tagline.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DotwaveLogo(size = 56.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(UiR.string.app_name), style = NaviType.dotTitle)
                        MonoLabel(stringResource(UiR.string.app_tagline))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text(stringResource(R.string.login_server)) },
                    placeholder = { Text(stringResource(R.string.login_server_hint)) },
                    singleLine = true,
                    enabled = !state.inProgress,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    supportingText = if (state.isPlainHttp) {
                        { Text(stringResource(R.string.login_http_warning)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    enabled = !state.inProgress,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username },
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    enabled = !state.inProgress,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onSubmit() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painterResource(if (showPassword) UiR.drawable.ic_visibility_off else UiR.drawable.ic_visibility),
                                contentDescription = stringResource(
                                    if (showPassword) R.string.login_hide_password else R.string.login_show_password,
                                ),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                )
                state.error?.let { error ->
                    Text(
                        errorText(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = { focus.clearFocus(); onSubmit() },
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.inProgress) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.login_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun errorText(error: LoginResult): String = when (error) {
    LoginResult.InvalidUrl -> stringResource(R.string.login_error_invalid_url)
    LoginResult.WrongCredentials -> stringResource(R.string.login_error_credentials)
    LoginResult.UnknownHost -> stringResource(R.string.login_error_unknown_host)
    LoginResult.Unreachable -> stringResource(R.string.login_error_unreachable)
    LoginResult.TlsError -> stringResource(R.string.login_error_tls)
    LoginResult.NotSubsonic -> stringResource(R.string.login_error_not_subsonic)
    is LoginResult.ServerError -> stringResource(R.string.login_error_server, error.message ?: "?")
    is LoginResult.Success -> ""
}
