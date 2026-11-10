package dev.gruebling.navi.feature.settings

import androidx.annotation.StringRes
import dev.gruebling.navi.core.model.StreamQuality

/** The choices offered for streaming quality. Opus is used for the lower rates: it sounds better than MP3 there. */
internal enum class QualityPreset(val quality: StreamQuality, @StringRes val label: Int) {
    Original(StreamQuality.Original, R.string.settings_quality_original),
    High(StreamQuality(320, "mp3"), R.string.settings_quality_high),
    Medium(StreamQuality(192, "opus"), R.string.settings_quality_medium),
    Low(StreamQuality(128, "opus"), R.string.settings_quality_low),
    Saver(StreamQuality(96, "opus"), R.string.settings_quality_saver),
    ;

    companion object {
        /** A stored value that matches no preset (older version, manual edit) shows as the closest by bitrate. */
        fun of(quality: StreamQuality): QualityPreset =
            entries.firstOrNull { it.quality == quality }
                ?: if (quality.maxBitRate == 0) Original else entries.drop(1).minBy { kotlin.math.abs(it.quality.maxBitRate - quality.maxBitRate) }
    }
}
