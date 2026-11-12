package dev.gruebling.navi.core.model

/**
 * Lyrics of one song. [synced] lyrics have a start time on every line; unsynced ones are plain text split into lines.
 * Start times already include the server's offset, so they compare directly with the playback position.
 */
data class Lyrics(
    val synced: Boolean,
    val lines: List<LyricsLine>,
    val lang: String? = null,
) {
    /** Index of the line being sung at [positionMs], or -1 before the first line (and always for unsynced lyrics). */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if ((lines[mid].startMs ?: 0) <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

data class LyricsLine(
    /** Null for unsynced lyrics. */
    val startMs: Long?,
    val text: String,
)
