package dev.gruebling.navi.core.playback

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gruebling.navi.core.data.di.ApplicationScope
import dev.gruebling.navi.core.datastore.StreamingSettingsStore
import dev.gruebling.navi.core.model.StreamQuality
import dev.gruebling.navi.core.model.StreamingSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** Picks the Wi-Fi or mobile-data quality for the network the next track is fetched over. Synchronous for ExoPlayer. */
@Singleton
class StreamQualityProvider @Inject constructor(
    @ApplicationContext context: Context,
    store: StreamingSettingsStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val settings = store.settings.stateIn(scope, SharingStarted.Eagerly, StreamingSettings())

    fun current(): StreamQuality {
        val s = settings.value
        // A VPN (WireGuard) reports the metered state of the network underneath, which is what we want.
        return if (connectivity.isActiveNetworkMetered) s.metered else s.unmetered
    }
}
