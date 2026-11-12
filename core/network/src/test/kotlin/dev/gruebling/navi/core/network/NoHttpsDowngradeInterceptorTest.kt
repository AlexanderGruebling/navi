package dev.gruebling.navi.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoHttpsDowngradeInterceptorTest {
    private val https = MockWebServer()
    private val http = MockWebServer()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        https.useHttps(serverCerts.sslSocketFactory())
        https.start()
        http.start()
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .addNetworkInterceptor(NoHttpsDowngradeInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        https.close()
        http.close()
    }

    private fun redirectTo(url: String) = MockResponse.Builder().code(302).addHeader("Location", url).build()

    private fun get(url: String) = client.newCall(Request.Builder().url(url).build()).execute()

    @Test
    fun `https to http redirect is refused and the token is not sent`() {
        https.enqueue(redirectTo(http.url("/rest/stream?t=token").toString()))
        assertThrows(HttpsDowngradeException::class.java) { get(https.url("/rest/stream?t=token").toString()) }
        assertEquals(0, http.requestCount)
    }

    @Test
    fun `http to https and https to https redirects are followed`() {
        http.enqueue(redirectTo(https.url("/a").toString()))
        https.enqueue(redirectTo("/b"))
        https.enqueue(MockResponse.Builder().body("ok").build())
        get(http.url("/start").toString()).use { response ->
            assertTrue(response.isSuccessful)
            assertEquals("/b", response.request.url.encodedPath)
        }
    }
}
