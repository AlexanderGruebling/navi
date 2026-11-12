package dev.gruebling.navi.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gruebling.navi.core.network.BuildConfig
import dev.gruebling.navi.core.network.NoHttpsDowngradeInterceptor
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.SubsonicClientFactory
import dev.gruebling.navi.core.network.SubsonicRequestInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = SubsonicClientFactory.json

    /**
     * The one OkHttpClient of the app. Coil and ExoPlayer get it too, so all traffic shares one connection pool
     * (warm HTTP/2 connection to the server).
     */
    @Provides
    @Singleton
    fun okHttpClient(subsonic: SubsonicRequestInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(subsonic)
            .addNetworkInterceptor(NoHttpsDowngradeInterceptor())
            .apply {
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactQueryParams("u", "t", "s", "p", "apiKey")
                        },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    fun subsonicApi(client: OkHttpClient, json: Json): SubsonicApi = SubsonicClientFactory.createApi(client, json)
}
