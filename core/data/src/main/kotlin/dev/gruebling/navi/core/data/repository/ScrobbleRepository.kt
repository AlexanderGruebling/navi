package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.data.util.suspendRunCatching
import dev.gruebling.navi.core.database.dao.PlaybackDao
import dev.gruebling.navi.core.database.entity.PendingScrobbleEntity
import dev.gruebling.navi.core.model.SongId
import dev.gruebling.navi.core.network.SubsonicApi
import dev.gruebling.navi.core.network.SubsonicException
import dev.gruebling.navi.core.network.ok
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reports plays to the server; Navidrome forwards them to ListenBrainz / Last.fm.
 * Plays that fail because of the network go into an outbox and are sent later with their original time.
 */
@Singleton
class ScrobbleRepository @Inject constructor(
    private val api: SubsonicApi,
    private val dao: PlaybackDao,
) {
    private val flushLock = Mutex()

    /** "Now playing". Best effort: never queued, a stale now-playing is worthless. */
    suspend fun nowPlaying(id: SongId) {
        suspendRunCatching { api.scrobble(id.value, submission = false).ok() }
    }

    /** A completed play. Sends older pending plays first so the server sees them in order. */
    suspend fun submit(id: SongId, playedAtMs: Long) {
        dao.insertScrobble(PendingScrobbleEntity(songId = id.value, playedAt = playedAtMs))
        flush()
    }

    /** Sends queued plays; stops at the first network error and keeps the rest. */
    suspend fun flush() = flushLock.withLock {
        while (true) {
            val batch = dao.pendingScrobbles(limit = 20)
            if (batch.isEmpty()) return@withLock
            for (scrobble in batch) {
                val result = suspendRunCatching {
                    api.scrobble(scrobble.songId, submission = true, time = scrobble.playedAt).ok()
                }
                val error = result.exceptionOrNull()
                when {
                    error == null -> dao.deleteScrobble(scrobble.id)
                    error is IOException -> return@withLock // offline: retry later
                    error is SubsonicException -> dao.deleteScrobble(scrobble.id) // rejected (e.g. song deleted): drop
                    else -> return@withLock
                }
            }
        }
    }
}
