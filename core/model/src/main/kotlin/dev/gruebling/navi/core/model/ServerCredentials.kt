package dev.gruebling.navi.core.model

/** Login data for one Subsonic server. [serverUrl] is normalized: scheme + host (+ port, path prefix), no trailing slash. */
data class ServerCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    // Never print the password, not even in debug logs or crash reports.
    override fun toString(): String = "ServerCredentials(serverUrl=$serverUrl, username=$username, password=***)"
}
