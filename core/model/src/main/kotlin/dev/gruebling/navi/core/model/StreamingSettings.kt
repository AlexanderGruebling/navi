package dev.gruebling.navi.core.model

/** One quality setting. [maxBitRate] 0 = no limit; [format] null = original file (no transcoding). */
data class StreamQuality(val maxBitRate: Int = 0, val format: String? = null) {
    val isOriginal: Boolean get() = maxBitRate == 0 && format == null

    companion object {
        val Original = StreamQuality()
    }
}

/** Separate quality for unmetered (Wi-Fi) and metered (mobile data) networks. */
data class StreamingSettings(
    val unmetered: StreamQuality = StreamQuality.Original,
    val metered: StreamQuality = StreamQuality.Original,
)

/** Volume levelling with ReplayGain tags. */
enum class ReplayGainMode {
    Off,

    /** Every song at the same loudness. */
    Track,

    /** Songs keep their loudness relative to the rest of their album. */
    Album,

    /** Album gain while the queue plays an album in order, track gain otherwise (mixes, playlists). */
    Auto,
}
