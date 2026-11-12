package dev.gruebling.navi.image

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.map.Mapper
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.crossfade
import coil3.toUri
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.network.CredentialsSource
import dev.gruebling.navi.core.network.SubsonicUrls
import dev.gruebling.navi.core.ui.image.CoverArtRequest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        credentials: CredentialsSource,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(CoverArtKeys)
            add(CoverArtMapper(credentials))
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("covers").toOkioPath())
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .crossfade(150)
        .build()
}

/** Authenticated URL, built at load time (fresh salt), never stored in the model or the cache key. */
private class CoverArtMapper(private val credentials: CredentialsSource) : Mapper<CoverArtRequest, Uri> {
    override fun map(data: CoverArtRequest, options: Options): Uri? {
        val creds = credentials.current() ?: return null
        return SubsonicUrls.coverArtUrl(creds, data.id, data.sizePx).toString().toUri()
    }
}

/** Memory and disk cache keys without auth parameters, so covers survive across requests and logins. */
private object CoverArtKeys : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data as? CoverArtRequest ?: return chain.proceed()
        val request = chain.request.newBuilder()
            .memoryCacheKey(data.cacheKey)
            .diskCacheKey(data.cacheKey)
            .build()
        return chain.withRequest(request).proceed()
    }
}

/** Empties Coil's memory and disk cache on logout: their keys hold cover ids only, not the server. */
class ImageCacheCleaner @Inject constructor(private val imageLoader: Provider<ImageLoader>) : AccountCache {
    override suspend fun clear() {
        val loader = imageLoader.get()
        loader.memoryCache?.clear()
        withContext(Dispatchers.IO) { loader.diskCache?.clear() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageBindings {
    @Binds @IntoSet abstract fun imageCacheCleaner(impl: ImageCacheCleaner): AccountCache
}
