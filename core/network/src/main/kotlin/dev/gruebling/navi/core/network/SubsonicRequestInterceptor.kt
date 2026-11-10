package dev.gruebling.navi.core.network

import dev.gruebling.navi.core.model.ServerCredentials
import javax.inject.Inject
import javax.inject.Provider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Turns Retrofit's placeholder requests (`http://subsonic.invalid/rest/ping`) into real, authenticated requests
 * against the logged-in server. A [ServerCredentials] request tag overrides the stored login (used while logging in).
 * Requests to any other host (e.g. full URLs built by [SubsonicUrls]) pass through untouched.
 */
class SubsonicRequestInterceptor @Inject constructor(
    // Provider: the credentials source (SessionManager) itself depends on the API, so resolve it lazily.
    private val credentialsSource: Provider<CredentialsSource>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != SubsonicUrls.PLACEHOLDER_HOST) return chain.proceed(request)

        val credentials = request.tag(ServerCredentials::class.java)
            ?: credentialsSource.get().current()
            ?: throw NotLoggedInException()

        val query = (0 until request.url.querySize).map { request.url.queryParameterName(it) to request.url.queryParameterValue(it) }
        val url = SubsonicUrls.build(credentials, request.url.pathSegments, query)
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
