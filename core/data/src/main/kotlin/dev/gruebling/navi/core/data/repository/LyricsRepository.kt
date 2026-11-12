package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.mapping.parsePlainLyrics
import dev.gruebling.navi.core.data.mapping.pickBest
import dev.gruebling.navi.core.data.mapping.toLyrics
import dev.gruebling.navi.core.data.session.AccountCache
import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.model.Lyrics
import dev.gruebling.navi.core.model.SongId
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.ok
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lyrics from the server, with a small in-memory cache (also of "no lyrics"), so skipping back and forth or reopening
 * the player doesn't ask again.
 *
 * Asks the OpenSubsonic `getLyricsBySongId` first (structured, often synced). If the server doesn't support it, falls
 * back to the classic `getLyrics` by artist and title. Network failures are not cached, so the next try can succeed.
 */
@Singleton
class LyricsRepository @Inject constructor(private val api: SubsonicApi) : AccountCache {
    private val lock = Mutex()
    private val cache = object : LinkedHashMap<String, Lyrics?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics?>) = size > CACHE_SIZE
    }

    /** Lyrics for the song, or null if it has none. Fails only with network errors. */
    suspend fun lyrics(id: SongId, artist: String?, title: String?): Result<Lyrics?> {
        lock.withLock { if (cache.containsKey(id.value)) return Result.success(cache[id.value]) }
        val result = fetch(id, artist, title)
        result.onSuccess { lyrics -> lock.withLock { cache[id.value] = lyrics } }
        return result
    }

    override suspend fun clear() = lock.withLock { cache.clear() }

    private suspend fun fetch(id: SongId, artist: String?, title: String?): Result<Lyrics?> {
        val structured = suspendRunCatching { api.getLyricsBySongId(id.value).ok().lyricsList }
        structured.exceptionOrNull()?.let { if (it is IOException) return Result.failure(it) }
        // A server with the extension answers with a (possibly empty) list; anything else means "not supported".
        structured.getOrNull()?.let { list ->
            return Result.success(list.structuredLyrics.pickBest(Locale.getDefault().language)?.toLyrics())
        }
        if (artist.isNullOrBlank() || title.isNullOrBlank()) return Result.success(null)
        val plain = suspendRunCatching { parsePlainLyrics(api.getLyrics(artist, title).ok().lyrics?.value) }
        // Only network errors are failures; a server error here just means there are no lyrics to get.
        return plain.exceptionOrNull()?.let { if (it is IOException) plain else Result.success(null) } ?: plain
    }

    private companion object {
        const val CACHE_SIZE = 64
    }
}
