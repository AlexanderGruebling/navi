package dev.gruebling.navi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The new-releases feed: its URL and what the user marked. Here and not in the Room cache, which schema changes wipe.
 * Saved releases keep a copy of their data ([saved] values, encoded by the repository), so they outlive the feed's
 * time window.
 */
@Singleton
class NewReleaseStore @Inject constructor(
    @SettingsPreferences private val dataStore: DataStore<Preferences>,
) {
    /** Feed URL; blank = feature off. Unset = [DEFAULT_FEED_URL]. */
    val feedUrl: Flow<String> = dataStore.data.map { it[FEED_URL] ?: DEFAULT_FEED_URL }.distinctUntilChanged()

    val seen: Flow<Set<String>> = dataStore.data.map { it[SEEN].orEmpty() }.distinctUntilChanged()

    /** Release id → saved copy of the release. */
    val saved: Flow<Map<String, String>> = dataStore.data.map { decode(it[SAVED].orEmpty()) }.distinctUntilChanged()

    suspend fun setFeedUrl(url: String) {
        dataStore.edit { it[FEED_URL] = url.trim() }
    }

    suspend fun setSeen(ids: Collection<String>, seen: Boolean) {
        dataStore.edit { p ->
            val all = p[SEEN].orEmpty()
            p[SEEN] = if (seen) all + ids else all - ids.toSet()
        }
    }

    suspend fun save(id: String, payload: String) = editSaved { it[id] = payload }

    suspend fun unsave(id: String) = editSaved { it.remove(id) }

    /** Updates the copies of saved releases that are still in the feed. */
    suspend fun refreshSaved(payloads: Map<String, String>) = editSaved { all ->
        payloads.forEach { (id, payload) -> if (id in all) all[id] = payload }
    }

    /** Forgets "seen" marks of releases that are neither in the feed nor saved any more. */
    suspend fun retainSeen(ids: Set<String>) {
        dataStore.edit { p -> p[SEEN] = p[SEEN].orEmpty() intersect ids }
    }

    /** Logout: the marks belong to the account. The feed URL is a device setting and stays. */
    suspend fun clearMarks() {
        dataStore.edit {
            it.remove(SEEN)
            it.remove(SAVED)
        }
    }

    private suspend fun editSaved(change: (MutableMap<String, String>) -> Unit) {
        dataStore.edit { p ->
            val all = decode(p[SAVED].orEmpty()).toMutableMap()
            change(all)
            val entries = all.map { (id, payload) -> URLEncoder.encode(id, UTF8) + "\t" + payload }.toSet()
            if (entries.isEmpty()) p.remove(SAVED) else p[SAVED] = entries
        }
    }

    companion object {
        const val DEFAULT_FEED_URL = "https://releases.gruebling.dev/releases.json"
        private val FEED_URL = stringPreferencesKey("new_releases_feed_url")
        private val SEEN = stringSetPreferencesKey("new_releases_seen")
        private val SAVED = stringSetPreferencesKey("new_releases_saved")
        private const val UTF8 = "UTF-8"

        /** `id<TAB>payload`; the id is URL-encoded and the payload is JSON, which escapes tabs, so one tab splits. */
        private fun decode(entries: Set<String>): Map<String, String> = entries.mapNotNull { entry ->
            val tab = entry.indexOf('\t')
            if (tab < 0) null else URLDecoder.decode(entry.substring(0, tab), UTF8) to entry.substring(tab + 1)
        }.toMap()
    }
}
