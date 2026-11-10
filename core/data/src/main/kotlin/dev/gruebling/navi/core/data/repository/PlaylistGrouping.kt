package dev.gruebling.navi.core.data.repository

import dev.gruebling.navi.core.model.PlaylistGroup
import java.security.MessageDigest

/**
 * Decides which Home group a playlist belongs to. Priority:
 * 1. manual override from settings,
 * 2. a tag in the server comment: [charts], [foryou] / [für-dich], [mine],
 * 3. smart playlist (validUntil set) → Für dich,
 * 4. name matches [chartsPattern] → Charts,
 * 5. everything else → Meine Playlists.
 *
 * Navidrome marks every file-based playlist (.nsp and .m3u) readonly, so `readonly` is useless here.
 */
class PlaylistGrouping(
    private val chartsPattern: Regex = DEFAULT_CHARTS_PATTERN,
) {
    fun groupOf(name: String, comment: String?, isSmart: Boolean, override: PlaylistGroup?): PlaylistGroup =
        override
            ?: comment?.let(::tagGroup)
            ?: when {
                isSmart -> PlaylistGroup.ForYou
                chartsPattern.containsMatchIn(name) -> PlaylistGroup.Charts
                else -> PlaylistGroup.Mine
            }

    companion object {
        val DEFAULT_CHARTS_PATTERN = Regex("""charts|top \d+|weekly""", RegexOption.IGNORE_CASE)

        private val TAG = Regex("""\[(charts|foryou|for-you|für-dich|fuer-dich|mine)]""", RegexOption.IGNORE_CASE)

        private fun tagGroup(comment: String): PlaylistGroup? =
            when (TAG.find(comment)?.groupValues?.get(1)?.lowercase()) {
                "charts" -> PlaylistGroup.Charts
                "foryou", "for-you", "für-dich", "fuer-dich" -> PlaylistGroup.ForYou
                "mine" -> PlaylistGroup.Mine
                else -> null
            }

        /** The comment as shown in the app: without grouping tags. */
        fun displayComment(comment: String?): String? =
            comment?.replace(TAG, "")?.replace(Regex("""\s{2,}"""), " ")?.trim()?.takeIf { it.isNotEmpty() }

        /** Identifies a track list; equal lists give equal signatures. */
        fun signature(songIds: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-1")
            songIds.forEach { digest.update(it.toByteArray()); digest.update(0) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
