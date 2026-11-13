package dev.gruebling.navi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistSettings
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the user set per playlist (group, pin, manual order), by playlist id. Kept here and not in the Room cache,
 * which is rebuilt from scratch on schema changes. Playlists with default settings have no entry.
 */
@Singleton
class PlaylistSettingsStore @Inject constructor(
    @SettingsPreferences private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<Map<String, PlaylistSettings>> =
        dataStore.data.map { decode(it[SETTINGS].orEmpty()) }.distinctUntilChanged()

    suspend fun set(id: String, settings: PlaylistSettings) = edit { it[id] = settings }

    /** Manual order within a group: [ids] in the wanted order get sortOrder 0, 1, 2, … in one write. */
    suspend fun setOrder(ids: List<String>) = edit { all ->
        ids.forEachIndexed { index, id -> all[id] = (all[id] ?: PlaylistSettings()).copy(sortOrder = index) }
    }

    /** Forgets playlists the server no longer has. */
    suspend fun retainOnly(ids: Set<String>) = edit { it.keys.retainAll(ids) }

    suspend fun clear() {
        dataStore.edit { it.remove(SETTINGS) }
    }

    /**
     * Takes over settings from before they lived here, once. Entries already in this store win. [legacy] runs only
     * the first time.
     */
    suspend fun importOnce(legacy: suspend () -> Map<String, PlaylistSettings>) {
        dataStore.edit { p ->
            if (p[IMPORTED] == true) return@edit
            val merged = legacy() + decode(p[SETTINGS].orEmpty())
            p.write(merged)
            p[IMPORTED] = true
        }
    }

    private suspend fun edit(change: (MutableMap<String, PlaylistSettings>) -> Unit) {
        dataStore.edit { p ->
            val all = decode(p[SETTINGS].orEmpty()).toMutableMap()
            change(all)
            p.write(all)
        }
    }

    private fun MutablePreferences.write(all: Map<String, PlaylistSettings>) {
        val entries = all.filterValues { it != PlaylistSettings() }.map { (id, s) -> encode(id, s) }.toSet()
        if (entries.isEmpty()) remove(SETTINGS) else this[SETTINGS] = entries
    }

    private companion object {
        val SETTINGS = stringSetPreferencesKey("playlist_settings")
        val IMPORTED = booleanPreferencesKey("playlist_settings_imported")
        const val UTF8 = "UTF-8"

        /** `id<TAB>group<TAB>pinned<TAB>sortOrder`; the id is URL-encoded, so it never contains a tab. */
        fun encode(id: String, s: PlaylistSettings): String =
            listOf(URLEncoder.encode(id, UTF8), s.groupOverride?.name.orEmpty(), if (s.pinned) "1" else "0", s.sortOrder?.toString().orEmpty())
                .joinToString("\t")

        fun decode(entries: Set<String>): Map<String, PlaylistSettings> = entries.mapNotNull { entry ->
            val parts = entry.split('\t')
            if (parts.size != 4) return@mapNotNull null
            URLDecoder.decode(parts[0], UTF8) to PlaylistSettings(
                groupOverride = PlaylistGroup.entries.firstOrNull { it.name == parts[1] },
                pinned = parts[2] == "1",
                sortOrder = parts[3].toIntOrNull(),
            )
        }.toMap()
    }
}
