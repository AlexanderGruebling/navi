package dev.gruebling.navi.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import dev.gruebling.navi.core.playback.di.ArtworkDataSource
import dev.gruebling.navi.core.playback.di.AudioDataSource
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Background playback: ExoPlayer inside a MediaLibrarySession. Media3 provides the notification, lock screen
 * controls, Bluetooth / headset buttons and audio focus; the library session is what Android Auto will browse.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject @AudioDataSource lateinit var audioDataSource: DataSource.Factory
    @Inject @ArtworkDataSource lateinit var artworkDataSource: DataSource.Factory
    @Inject lateinit var callback: LibraryCallback
    @Inject lateinit var queuePersistence: QueuePersistence
    @Inject lateinit var scrobbler: Scrobbler
    @Inject lateinit var mixExtender: MixExtender
    @Inject lateinit var streamResolver: StreamResolver

    private val scope = MainScope()
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(audioDataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // Load the next track in the queue ahead of time: instant skips and gapless transitions.
        exoPlayer.preloadConfiguration = ExoPlayer.PreloadConfiguration(/* targetPreloadDurationUs = */ 10_000_000L)
        // Everything else (session, scrobbling, queue saving) sees song time, also in transcoded streams.
        val player = TranscodeSeekingPlayer(exoPlayer, streamResolver)

        queuePersistence.attach(player, scope)
        scrobbler.attach(player, scope)
        mixExtender.attach(player, scope)

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        session = MediaLibrarySession.Builder(this, player, callback)
            .setBitmapLoader(
                CacheBitmapLoader(DataSourceBitmapLoader.Builder(this).setDataSourceFactory(artworkDataSource).build()),
            )
            .apply {
                if (launchIntent != null) {
                    setSessionActivity(
                        PendingIntent.getActivity(this@PlaybackService, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE),
                    )
                }
            }
            .build()

        scope.launch { queuePersistence.restoreInto(player) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.let {
            // Last chance to keep the position; a few ms of blocking IO at shutdown.
            runBlocking { queuePersistence.save() }
            it.player.release()
            it.release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
