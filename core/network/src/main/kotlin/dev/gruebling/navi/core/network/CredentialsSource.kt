package dev.gruebling.navi.core.network

import dev.gruebling.navi.core.model.ServerCredentials
import java.io.IOException

/** Synchronous access to the logged-in server, for OkHttp interceptors (which run on OkHttp's threads). */
fun interface CredentialsSource {
    fun current(): ServerCredentials?
}

class NotLoggedInException : IOException("No server configured")
