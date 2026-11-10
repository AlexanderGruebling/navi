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
