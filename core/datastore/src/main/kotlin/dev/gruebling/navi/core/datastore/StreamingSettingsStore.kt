package dev.gruebling.navi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.gruebling.navi.core.model.ReplayGainMode
import dev.gruebling.navi.core.model.StreamQuality
import dev.gruebling.navi.core.model.StreamingSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class StreamingSettingsStore @Inject constructor(
    @SettingsPreferences private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<StreamingSettings> = dataStore.data.map { p ->
        StreamingSettings(
            unmetered = StreamQuality(p[WIFI_BITRATE] ?: 0, p[WIFI_FORMAT]),
            metered = StreamQuality(p[MOBILE_BITRATE] ?: 0, p[MOBILE_FORMAT]),
        )
    }.distinctUntilChanged()

    /** Volume levelling; [ReplayGainMode.Auto] until the user picks something else. */
    val replayGainMode: Flow<ReplayGainMode> = dataStore.data.map { p ->
        p[REPLAY_GAIN]?.let { name -> ReplayGainMode.entries.firstOrNull { it.name == name } } ?: ReplayGainMode.Auto
    }.distinctUntilChanged()

    suspend fun setReplayGainMode(mode: ReplayGainMode) {
        dataStore.edit { it[REPLAY_GAIN] = mode.name }
    }

    suspend fun update(settings: StreamingSettings) {
        dataStore.edit { p ->
            p[WIFI_BITRATE] = settings.unmetered.maxBitRate
            p.setOrRemove(WIFI_FORMAT, settings.unmetered.format)
            p[MOBILE_BITRATE] = settings.metered.maxBitRate
            p.setOrRemove(MOBILE_FORMAT, settings.metered.format)
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) { if (value == null) remove(key) else this[key] = value }

    private companion object {
        val WIFI_BITRATE = intPreferencesKey("stream_wifi_bitrate")
        val WIFI_FORMAT = stringPreferencesKey("stream_wifi_format")
        val MOBILE_BITRATE = intPreferencesKey("stream_mobile_bitrate")
        val MOBILE_FORMAT = stringPreferencesKey("stream_mobile_format")
        val REPLAY_GAIN = stringPreferencesKey("replay_gain_mode")
    }
}
