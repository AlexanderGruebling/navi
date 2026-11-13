package dev.gruebling.navi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the logged-in server supports, as last checked; kept so it is known offline and right at startup. */
@Singleton
class ServerFeaturesStore @Inject constructor(
    @SessionPreferences private val dataStore: DataStore<Preferences>,
) {
    /** OpenSubsonic `transcodeOffset`; null until checked. */
    val transcodeOffset: Flow<Boolean?> = dataStore.data.map { it[TRANSCODE_OFFSET] }.distinctUntilChanged()

    suspend fun setTranscodeOffset(supported: Boolean) {
        dataStore.edit { it[TRANSCODE_OFFSET] = supported }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(TRANSCODE_OFFSET) }
    }

    private companion object {
        val TRANSCODE_OFFSET = booleanPreferencesKey("feature_transcode_offset")
    }
}
