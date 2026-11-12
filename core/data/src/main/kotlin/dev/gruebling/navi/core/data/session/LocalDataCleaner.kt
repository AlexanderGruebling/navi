package dev.gruebling.navi.core.data.session

import dev.gruebling.navi.core.data.di.IoDispatcher
import dev.gruebling.navi.core.database.NaviDatabase
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A cache outside the database that holds the account's data (audio, covers, lyrics). Its keys don't name the server,
 * so it must be emptied on logout: otherwise the next account could get this one's files for colliding ids.
 * Contribute implementations with `@Binds @IntoSet`.
 */
interface AccountCache {
    suspend fun clear()
}

/** Deletes everything cached for the current account: the database (library, queue, scrobble outbox) and every [AccountCache]. */
@Singleton
class LocalDataCleaner @Inject constructor(
    private val db: NaviDatabase,
    // Provider: some caches (images) depend on the login, which depends on this.
    private val caches: Provider<Set<@JvmSuppressWildcards AccountCache>>,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun clear() {
        withContext(io) { db.clearAllTables() }
        caches.get().forEach { it.clear() }
    }
}
