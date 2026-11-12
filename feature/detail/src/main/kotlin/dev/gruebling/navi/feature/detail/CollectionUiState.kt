package dev.gruebling.navi.feature.detail

import androidx.compose.runtime.Immutable
import dev.gruebling.navi.core.model.DownloadStatus
import dev.gruebling.navi.core.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class CollectionHeader(
    val title: String,
    val subtitle: String?,
    val songCount: Int,
    val durationSec: Int,
    val comment: String?,
    val coverArtId: String?,
    val mosaic: ImmutableList<String> = persistentListOf(),
)

@Immutable
data class TrackUi(
    /** Unique per row (a playlist may contain a song twice). */
    val key: String,
    val songId: String,
    val title: String,
    val subtitle: String?,
    val durationMs: Long,
    val coverArtId: String?,
    val trackNumber: Int?,
    val starred: Boolean,
)

@Immutable
data class CollectionUiState(
    val header: CollectionHeader? = null,
    val tracks: ImmutableList<TrackUi> = persistentListOf(),
    val currentSongId: String? = null,
    val refreshFailed: Boolean = false,
    /** Smart playlist re-evaluated on the server while the user was looking at the old mix. */
    val hasNewerContent: Boolean = false,
    val isLoading: Boolean = true,
    val download: DownloadUi = DownloadUi(),
)

@Immutable
data class DownloadUi(val status: DownloadStatus = DownloadStatus.None, val allowMobileData: Boolean = false)

internal fun List<Song>.toTrackUi(showCovers: Boolean): ImmutableList<TrackUi> =
    mapIndexed { index, song ->
        TrackUi(
            key = "$index:${song.id.value}",
            songId = song.id.value,
            title = song.title,
            subtitle = song.artist,
            durationMs = song.durationSec * 1000L,
            coverArtId = if (showCovers) song.coverArt?.value else null,
            trackNumber = if (showCovers) null else song.track ?: (index + 1),
            starred = song.starred,
        )
    }.toImmutableList()
