package dev.gruebling.navi.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.gruebling.navi.core.ui.R

/** "3:07" */
fun formatTrackTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "40 Titel · 2 Std. 31 Min." */
@Composable
fun collectionSummary(songCount: Int, durationSec: Int): String {
    val songs = pluralStringResource(R.plurals.song_count, songCount, songCount)
    if (durationSec <= 0) return songs
    val minutes = durationSec / 60
    val duration = if (minutes >= 60) {
        stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
    }
    return "$songs · $duration"
}
