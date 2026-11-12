package dev.gruebling.navi.core.ui.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** Shows each message of [messages] (string resource ids) as a toast, e.g. why an instant mix didn't start. */
@Composable
fun MessageToasts(messages: Flow<Int>) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
}
