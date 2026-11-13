package dev.gruebling.navi.core.playback.model

/** One entry of the play queue, as the UI shows it. */
data class QueueEntry(
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val coverArt: String?,
    val durationMs: Long,
)

enum class RepeatMode { Off, All, One }

/**
 * Everything the mini player / full player need, except the playback position: that changes many times per second
 * and is read on demand via PlaybackController.positionMs(), so the whole state isn't rebuilt every frame.
 */
data class PlayerState(
    val current: QueueEntry? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    /** Queue indices in the order they will play (differs from [queue] order while shuffle is on). */
    val playOrder: List<Int> = emptyList(),
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val hasNext: Boolean = false,
    /** Whether "previous" does anything: go to the previous song or back to the start of this one. */
    val hasPrevious: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = current == null
}
