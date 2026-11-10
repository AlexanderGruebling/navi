package dev.gruebling.navi.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object SubsonicClientFactory {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** [okHttpClient] must contain [SubsonicRequestInterceptor]. */
    fun createApi(okHttpClient: OkHttpClient, json: Json = this.json): SubsonicApi =
        Retrofit.Builder()
            .baseUrl(SubsonicUrls.PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SubsonicApi::class.java)
}
