package dev.gruebling.navi.core.playback

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.gruebling.navi.core.model.StreamQuality

/**
 * Makes transcoded streams seekable.
 *
 * A transcoded stream has no known length, so ExoPlayer marks it unseekable and drops
 * COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM. Media controllers then ignore seeks silently. This player:
 * - always offers that command for the app's streams,
 * - seeks in a transcoded song by restarting it from the server at that point (OpenSubsonic `transcodeOffset`): the
 *   queue item gets a `navi://stream/<id>?offset=…` URI, the shuffle order is kept,
 * - adds the offset to every position it reports, so UI, notification, scrobbling and queue saving see song time,
 * - puts the plain URI back once that item stops being current, so previous / repeat start the song from 0.
 *
 * Original files are seekable by byte range and pass straight through.
 */
@OptIn(UnstableApi::class)
internal class TranscodeSeekingPlayer(
    private val exo: ExoPlayer,
    private val qualities: StreamQualities,
) : ForwardingPlayer(exo) {
    private val handler = Handler(exo.applicationLooper)
    private val wrappedListeners = HashMap<Player.Listener, Player.Listener>()

    init {
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // PLAYLIST_CHANGED with an offset item current = our own replacement; anything else leaves it.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && offsetMs() > 0) return
                val includeCurrent = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                // Not inside the callback: the player is still dispatching this transition.
                handler.post { restorePlainUris(includeCurrent) }
            }
        })
    }

    // Positions: stream time + where the stream started.

    private fun offsetMs(): Long = NaviUris.offsetSec(exo.currentMediaItem?.localConfiguration?.uri) * 1_000

    override fun getCurrentPosition(): Long = super.getCurrentPosition() + offsetMs()
    override fun getContentPosition(): Long = super.getContentPosition() + offsetMs()
    override fun getBufferedPosition(): Long = super.getBufferedPosition() + offsetMs()
    override fun getContentBufferedPosition(): Long = super.getContentBufferedPosition() + offsetMs()
    override fun getDuration(): Long = songDuration(super.getDuration())
    override fun getContentDuration(): Long = songDuration(super.getContentDuration())

    /** A transcoded stream often doesn't know its length; the song's length from the server always applies. */
    private fun songDuration(streamDuration: Long): Long {
        val offset = offsetMs()
        if (offset == 0L && streamDuration != C.TIME_UNSET) return streamDuration
        return exo.currentMediaItem?.mediaMetadata?.durationMs
            ?: if (streamDuration == C.TIME_UNSET) C.TIME_UNSET else streamDuration + offset
    }

    // Commands: seeking is always possible in our streams.
    //
    // ExoPlayer treats a stream with unknown length and duration as live: besides seeking it then drops "previous"
    // when there is no previous item (so "restart this song" would be ignored too).

    private fun currentIsStream() = NaviUris.isStream(exo.currentMediaItem?.localConfiguration?.uri)

    private fun withSeek(commands: Player.Commands): Player.Commands {
        if (!currentIsStream() || ALWAYS_FOR_STREAMS.all { commands.contains(it) }) return commands
        return commands.buildUpon().addAll(*ALWAYS_FOR_STREAMS).build()
    }

    override fun getAvailableCommands(): Player.Commands = withSeek(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean =
        (command in ALWAYS_FOR_STREAMS && currentIsStream()) || super.isCommandAvailable(command)

    override fun isCurrentMediaItemSeekable(): Boolean = currentIsStream() || super.isCurrentMediaItemSeekable()

    override fun isCurrentMediaItemLive(): Boolean = !currentIsStream() && super.isCurrentMediaItemLive()

    override fun addListener(listener: Player.Listener) {
        val wrapped = CommandsListener(listener)
        wrappedListeners[listener] = wrapped
        super.addListener(wrapped)
    }

    override fun removeListener(listener: Player.Listener) {
        wrappedListeners.remove(listener)?.let { super.removeListener(it) }
    }

    /**
     * Media sessions take the commands from this callback, not from [getAvailableCommands]. Every callback is
     * forwarded by hand: Kotlin's `by` delegation skips Java default methods, which all of these are.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private inner class CommandsListener(private val d: Player.Listener) : Player.Listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            d.onAvailableCommandsChanged(withSeek(availableCommands))

        override fun onEvents(player: Player, events: Player.Events) = d.onEvents(player, events)
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = d.onTimelineChanged(timeline, reason)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = d.onMediaItemTransition(mediaItem, reason)
        override fun onTracksChanged(tracks: Tracks) = d.onTracksChanged(tracks)
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = d.onMediaMetadataChanged(mediaMetadata)
        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = d.onPlaylistMetadataChanged(mediaMetadata)
        override fun onIsLoadingChanged(isLoading: Boolean) = d.onIsLoadingChanged(isLoading)
        override fun onLoadingChanged(isLoading: Boolean) = d.onLoadingChanged(isLoading)
        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = d.onTrackSelectionParametersChanged(parameters)
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = d.onPlayerStateChanged(playWhenReady, playbackState)
        override fun onPlaybackStateChanged(playbackState: Int) = d.onPlaybackStateChanged(playbackState)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = d.onPlayWhenReadyChanged(playWhenReady, reason)
        override fun onPlaybackSuppressionReasonChanged(reason: Int) = d.onPlaybackSuppressionReasonChanged(reason)
        override fun onIsPlayingChanged(isPlaying: Boolean) = d.onIsPlayingChanged(isPlaying)
        override fun onRepeatModeChanged(repeatMode: Int) = d.onRepeatModeChanged(repeatMode)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = d.onShuffleModeEnabledChanged(shuffleModeEnabled)
        override fun onPlayerError(error: PlaybackException) = d.onPlayerError(error)
        override fun onPlayerErrorChanged(error: PlaybackException?) = d.onPlayerErrorChanged(error)
        override fun onPositionDiscontinuity(reason: Int) = d.onPositionDiscontinuity(reason)
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
            d.onPositionDiscontinuity(oldPosition, newPosition, reason)
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = d.onPlaybackParametersChanged(playbackParameters)
        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = d.onSeekBackIncrementChanged(seekBackIncrementMs)
        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = d.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
            d.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        override fun onAudioSessionIdChanged(audioSessionId: Int) = d.onAudioSessionIdChanged(audioSessionId)
        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) = d.onAudioAttributesChanged(audioAttributes)
        override fun onVolumeChanged(volume: Float) = d.onVolumeChanged(volume)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = d.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = d.onDeviceInfoChanged(deviceInfo)
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = d.onDeviceVolumeChanged(volume, muted)
        override fun onVideoSizeChanged(videoSize: VideoSize) = d.onVideoSizeChanged(videoSize)
        override fun onSurfaceSizeChanged(width: Int, height: Int) = d.onSurfaceSizeChanged(width, height)
        override fun onRenderedFirstFrame() = d.onRenderedFirstFrame()
        override fun onCues(cues: List<Cue>) = d.onCues(cues)
        override fun onCues(cueGroup: CueGroup) = d.onCues(cueGroup)
        override fun onMetadata(metadata: Metadata) = d.onMetadata(metadata)

        // ForwardingPlayer compares wrapped listeners; keep identity tied to the original.
        override fun equals(other: Any?) = other is CommandsListener && other.d == d
        override fun hashCode() = d.hashCode()
    }

    // Starting mid-song (a restored queue, playback resumption): until prepare(), ExoPlayer holds the song position
    // with a plain URI. Whether the song will be transcoded is only decided now, with the network, setting and
    // downloads of this moment; a transcoded one then streams from that position, since ExoPlayer can't seek in it.

    override fun prepare() {
        if (exo.playbackState == STATE_IDLE) startMidSongIfTranscoded()
        super.prepare()
    }

    private fun startMidSongIfTranscoded() {
        val item = exo.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri
        if (!NaviUris.isStream(uri) || NaviUris.offsetSec(uri) > 0) return
        val positionMs = exo.currentPosition
        val durationMs = item.mediaMetadata.durationMs
        // A position at the very end would ask the server for a stream past the song; let it start over instead.
        if (positionMs < 1_000 || (durationMs != null && positionMs >= durationMs - 1_000)) return
        val quality = qualities.qualityForNewStream(item.mediaId).takeIf { !it.isOriginal } ?: return
        restartAt(exo.currentMediaItemIndex, positionMs, quality)
    }

    // Seeking.

    override fun seekTo(positionMs: Long) = seekTo(currentMediaItemIndex, positionMs)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val quality = transcodedQualityAt(mediaItemIndex)
        if (mediaItemIndex == currentMediaItemIndex && quality != null) {
            restartAt(mediaItemIndex, positionMs, quality)
        } else {
            super.seekTo(mediaItemIndex, positionMs)
        }
    }

    override fun seekToDefaultPosition() {
        if (offsetMs() > 0) restartAt(currentMediaItemIndex, 0, null) else super.seekToDefaultPosition()
    }

    override fun seekToPrevious() {
        // ExoPlayer would compare its stream position, not the song position, with the "restart track" threshold.
        if (offsetMs() > 0 && currentPosition > maxSeekToPreviousPosition) {
            restartAt(currentMediaItemIndex, 0, null)
        } else {
            super.seekToPrevious()
        }
    }

    /** The quality the item at [index] is streamed in, if the server transcodes it (and so can't seek by bytes). */
    private fun transcodedQualityAt(index: Int): StreamQuality? {
        val item = exo.getMediaItemAt(index)
        val uri = item.localConfiguration?.uri
        if (!NaviUris.isStream(uri)) return null
        // An offset stream that hasn't been opened yet: the quality is pinned in its URI.
        return (qualities.qualityOf(item.mediaId) ?: NaviUris.pinnedQuality(uri))?.takeIf { !it.isOriginal }
    }

    private fun restartAt(index: Int, positionMs: Long, quality: StreamQuality?) {
        val item = exo.getMediaItemAt(index)
        val offsetSec = (positionMs / 1_000).coerceAtLeast(0)
        val uri = if (offsetSec == 0L || quality == null) NaviUris.stream(item.mediaId) else NaviUris.streamFrom(item.mediaId, offsetSec, quality)
        if (uri != item.localConfiguration?.uri) replaceKeepingShuffle(index, item.buildUpon().setUri(uri).build())
        exo.seekTo(index, 0)
    }

    private fun restorePlainUris(includeCurrent: Boolean) {
        for (i in 0 until exo.mediaItemCount) {
            if (i == exo.currentMediaItemIndex && !includeCurrent) continue
            val item = exo.getMediaItemAt(i)
            if (NaviUris.offsetSec(item.localConfiguration?.uri) > 0) {
                replaceKeepingShuffle(i, item.buildUpon().setUri(NaviUris.stream(item.mediaId)).build())
                if (i == exo.currentMediaItemIndex) exo.seekTo(i, 0)
            }
        }
    }

    /** replaceMediaItem re-inserts the item, which would give it a new random place in the shuffle order. */
    private fun replaceKeepingShuffle(index: Int, item: MediaItem) {
        val order = exo.shuffleOrder
        exo.replaceMediaItem(index, item)
        if (order.length == exo.mediaItemCount) exo.setShuffleOrder(order)
    }

    private companion object {
        val ALWAYS_FOR_STREAMS = intArrayOf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS)
    }
}
