package dev.gruebling.navi.core.playback.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.playback.AudioCacheCleaner
import dev.gruebling.navi.core.playback.StreamResolver
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AudioDataSource
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ArtworkDataSource

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    private const val AUDIO_CACHE_BYTES = 500L * 1024 * 1024

    /** Streamed audio cache. Later offline downloads will get their own, non-evicting cache. */
    @Provides
    @Singleton
    fun audioCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )

    /** navi:// → resolver (auth URL + cache key) → cache → shared OkHttpClient. */
    @Provides
    @AudioDataSource
    fun audioDataSourceFactory(client: OkHttpClient, cache: SimpleCache, resolver: StreamResolver): DataSource.Factory =
        ResolvingDataSource.Factory(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(client))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
            resolver,
        )

    /** Notification / lock screen artwork; not cached on disk here (Coil's cache comes with the UI). */
    @Provides
    @ArtworkDataSource
    fun artworkDataSourceFactory(client: OkHttpClient, resolver: StreamResolver): DataSource.Factory =
        ResolvingDataSource.Factory(OkHttpDataSource.Factory(client), resolver)
}

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackBindings {
    @Binds abstract fun cache(impl: SimpleCache): Cache

    @Binds @IntoSet abstract fun audioCacheCleaner(impl: AudioCacheCleaner): AccountCache
}
