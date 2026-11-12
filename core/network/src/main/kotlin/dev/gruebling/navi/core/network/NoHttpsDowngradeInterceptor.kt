package dev.gruebling.navi.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Refuses redirects from https to http. Every request URL carries the auth token (which servers accept again and again),
 * and OkHttp would resend it in clear text to the redirect target. http → https stays allowed: servers often send
 * plain-http visitors to their https address.
 *
 * Must be a network interceptor, so it sees each redirect before OkHttp follows it.
 */
class NoHttpsDowngradeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!request.url.isHttps || !response.isRedirect) return response
        val target = response.header("Location")?.let(request.url::resolve) ?: return response
        if (target.isHttps) return response
        response.close()
        throw HttpsDowngradeException()
    }
}

class HttpsDowngradeException : IOException("The server redirected from https to http; not following it")
