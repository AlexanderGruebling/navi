package dev.gruebling.navi.core.network

import dev.gruebling.navi.core.network.model.ReleaseFeedDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * The new-releases feed: one JSON file at a URL the user sets (release-watch, `releases.json`). It lives on another
 * host than the Subsonic server, so requests carry no Subsonic credentials (SubsonicRequestInterceptor only touches
 * its placeholder host).
 */
interface ReleaseFeedApi {
    @GET
    suspend fun feed(@Url url: String): ReleaseFeedDto

    companion object {
        fun create(client: OkHttpClient, json: Json): ReleaseFeedApi =
            Retrofit.Builder()
                // Never used: every call passes an absolute @Url.
                .baseUrl(SubsonicUrls.PLACEHOLDER_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ReleaseFeedApi::class.java)
    }
}
