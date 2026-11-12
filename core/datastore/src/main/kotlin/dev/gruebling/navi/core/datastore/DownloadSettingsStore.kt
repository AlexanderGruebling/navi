package dev.gruebling.navi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.gruebling.navi.core.model.DownloadTarget
import dev.gruebling.navi.core.model.StreamQuality
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Offline downloads: which albums and playlists to keep, and how. Kept here and not in the Room cache, which is
 * rebuilt from scratch on schema changes.
 */
@Singleton
class DownloadSettingsStore @Inject constructor(
    @SettingsPreferences private val dataStore: DataStore<Preferences>,
) {
    val targets: Flow<Set<DownloadTarget>> = dataStore.data.map { p ->
        p[TARGETS].orEmpty().mapNotNull(DownloadTarget::parse).toSet()
    }.distinctUntilChanged()

    /** Original until the user picks a preset. */
    val quality: Flow<StreamQuality> = dataStore.data.map { p ->
        StreamQuality(p[BITRATE] ?: 0, p[FORMAT])
    }.distinctUntilChanged()

    /** Off by default: downloads wait for Wi-Fi. */
    val allowMobileData: Flow<Boolean> = dataStore.data.map { it[MOBILE_DATA] ?: false }.distinctUntilChanged()

    suspend fun add(target: DownloadTarget) {
        dataStore.edit { it[TARGETS] = it[TARGETS].orEmpty() + target.key }
    }

    suspend fun remove(target: DownloadTarget) {
        dataStore.edit { it[TARGETS] = it[TARGETS].orEmpty() - target.key }
    }

    suspend fun clearTargets() {
        dataStore.edit { it.remove(TARGETS) }
    }

    suspend fun setQuality(quality: StreamQuality) {
        dataStore.edit { p ->
            p[BITRATE] = quality.maxBitRate
            if (quality.format == null) p.remove(FORMAT) else p[FORMAT] = quality.format!!
        }
    }

    suspend fun setAllowMobileData(allow: Boolean) {
        dataStore.edit { it[MOBILE_DATA] = allow }
    }

    private companion object {
        val TARGETS = stringSetPreferencesKey("download_targets")
        val BITRATE = intPreferencesKey("download_bitrate")
        val FORMAT = stringPreferencesKey("download_format")
        val MOBILE_DATA = booleanPreferencesKey("download_mobile_data")
    }
}
