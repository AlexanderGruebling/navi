package dev.gruebling.navi.feature.releases

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.data.repository.NewReleaseRepository
import dev.gruebling.navi.core.model.AlbumId
import dev.gruebling.navi.core.model.NewRelease
import dev.gruebling.navi.core.model.ReleaseType
import dev.gruebling.navi.core.playback.PlaybackController
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The feed's filter chips. [Saved] is the way back to what was saved for later. */
enum class ReleaseFilter { All, Albums, Eps, Singles, Saved }

@Immutable
data class ReleasesUiState(
    val thisWeek: ImmutableList<NewRelease> = persistentListOf(),
    val earlier: ImmutableList<NewRelease> = persistentListOf(),
    val filter: ReleaseFilter = ReleaseFilter.All,
    /** Unseen releases in the whole feed, whatever the filter. */
    val unseenCount: Int = 0,
    /** Nothing at all in the feed (not just nothing for this filter). */
    val feedEmpty: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /** Nothing cached and the first download still running. */
    val isLoadingFirstTime: Boolean = true,
)

/** Releases from the last 7 days (and announced ones) go to "This week". */
internal const val THIS_WEEK_DAYS = 7L

@HiltViewModel
class ReleasesViewModel @Inject constructor(
    private val releases: NewReleaseRepository,
    private val albums: AlbumRepository,
    private val playback: PlaybackController,
) : ViewModel() {
    private val filter = MutableStateFlow(ReleaseFilter.All)
    private val refreshing = MutableStateFlow(false)
    /** Null until the first refresh has finished. */
    private val refreshFailed = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<ReleasesUiState> = combine(
        releases.observeReleases(),
        filter,
        refreshing,
        refreshFailed,
    ) { all, f, isRefreshing, failed ->
        val shown = all.filter { it.matches(f) }
        val weekStart = LocalDate.now().minusDays(THIS_WEEK_DAYS - 1)
        val (week, earlier) = shown.partition { !it.releaseDate.isBefore(weekStart) }
        ReleasesUiState(
            thisWeek = week.toImmutableList(),
            earlier = earlier.toImmutableList(),
            filter = f,
            unseenCount = all.count { !it.seen },
            feedEmpty = all.isEmpty(),
            isRefreshing = isRefreshing,
            refreshFailed = failed == true,
            isLoadingFirstTime = all.isEmpty() && failed == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReleasesUiState())

    init {
        refresh(userInitiated = false)
    }

    fun refresh(userInitiated: Boolean = true) {
        if (refreshing.value) return
        viewModelScope.launch {
            if (userInitiated) refreshing.value = true
            refreshFailed.value = releases.refresh().isFailure
            refreshing.value = false
        }
    }

    fun setFilter(value: ReleaseFilter) {
        filter.value = value
    }

    fun toggleSaved(release: NewRelease) {
        viewModelScope.launch { releases.setSaved(release, !release.saved) }
    }

    fun markAllSeen() {
        viewModelScope.launch {
            val unseen = releases.observeReleases().first().filter { !it.seen }.map { it.id }
            if (unseen.isNotEmpty()) releases.setSeen(unseen, seen = true)
        }
    }

    /** Play button of a release that is in the library by now. */
    fun play(release: NewRelease) {
        val id = release.libraryAlbumId ?: return
        viewModelScope.launch {
            playAlbum(albums, playback, id)
            releases.setSeen(listOf(release.id), seen = true)
        }
    }
}

private fun NewRelease.matches(filter: ReleaseFilter): Boolean = when (filter) {
    ReleaseFilter.All -> true
    ReleaseFilter.Albums -> type == ReleaseType.Album
    ReleaseFilter.Eps -> type == ReleaseType.Ep
    ReleaseFilter.Singles -> type == ReleaseType.Single
    ReleaseFilter.Saved -> saved
}

/** Plays an album from the cache; loads it from the server first if it isn't cached. */
internal suspend fun playAlbum(albums: AlbumRepository, playback: PlaybackController, id: AlbumId) {
    var songs = albums.observeAlbum(id).first()?.songs.orEmpty()
    if (songs.isEmpty()) {
        albums.refreshAlbum(id)
        songs = albums.observeAlbum(id).first()?.songs.orEmpty()
    }
    if (songs.isNotEmpty()) playback.playSongs(songs)
}
