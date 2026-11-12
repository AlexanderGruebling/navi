package dev.gruebling.navi.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import dev.gruebling.navi.core.data.di.IoDispatcher
import dev.gruebling.navi.core.data.session.AccountCache
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Empties the streamed-audio cache on logout. Playback is already stopped by then ([SignOut]). */
@OptIn(UnstableApi::class)
class AudioCacheCleaner @Inject constructor(
    private val cache: Cache,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AccountCache {
    override suspend fun clear() = withContext(io) {
        for (key in cache.keys.toList()) cache.removeResource(key)
    }
}
