package dev.gruebling.navi.core.model

import kotlin.time.Instant

/** Home screen groups. Order of the entries = order on Home. */
enum class PlaylistGroup { Charts, ForYou, Mine }

data class Playlist(
    val id: PlaylistId,
    val name: String,
    /** Server comment without grouping tags like "[charts]". */
    val comment: String?,
    val owner: String?,
    val songCount: Int,
    val durationSec: Int,
    val coverArt: CoverArtId?,
    val changed: Instant?,
    /** Navidrome smart playlist (.nsp): content is generated on the server and may change on every load. */
    val isSmart: Boolean,
    val group: PlaylistGroup,
    val pinned: Boolean,
    /** Manual order from settings; null = automatic. */
    val sortOrder: Int?,
    /** The track list changed since the user last opened it (never true for smart playlists). */
    val hasNewContent: Boolean,
)

data class PlaylistDetail(
    val playlist: Playlist,
    val songs: List<Song>,
    /** Up to four distinct album covers, for a generated mosaic when the playlist has no cover of its own. */
    val mosaic: List<CoverArtId>,
)

/** Local, per-playlist settings (group override, pin, manual order). */
data class PlaylistSettings(
    val groupOverride: PlaylistGroup? = null,
    val pinned: Boolean = false,
    val sortOrder: Int? = null,
)
