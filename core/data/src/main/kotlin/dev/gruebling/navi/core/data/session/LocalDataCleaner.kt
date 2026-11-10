package dev.gruebling.navi.core.data.session

import dev.gruebling.navi.core.data.di.IoDispatcher
import dev.gruebling.navi.core.database.NaviDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Deletes everything cached for the current account (library, playlists, favorites, queue, scrobble outbox). */
@Singleton
class LocalDataCleaner @Inject constructor(
    private val db: NaviDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun clear() = withContext(io) { db.clearAllTables() }
}
