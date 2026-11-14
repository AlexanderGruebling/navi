package dev.gruebling.navi.feature.releases

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.data.repository.NewReleaseRepository
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.data.session.SessionState
import dev.gruebling.navi.core.model.NewRelease
import dev.gruebling.navi.core.playback.PlaybackController
import java.net.URI
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ReleaseDetailUiState(
    val release: NewRelease? = null,
    /** True once the feed is loaded and has no such release (it dropped out and wasn't saved). */
    val missing: Boolean = false,
    /** "music.example.com", for "Not on … yet". */
    val serverHost: String? = null,
)

@HiltViewModel(assistedFactory = ReleaseDetailViewModel.Factory::class)
class ReleaseDetailViewModel @AssistedInject constructor(
    @Assisted private val releaseId: String,
    private val releases: NewReleaseRepository,
    private val albums: AlbumRepository,
    private val playback: PlaybackController,
    session: SessionManager,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(releaseId: String): ReleaseDetailViewModel
    }

    val state: StateFlow<ReleaseDetailUiState?> = combine(
        releases.observeRelease(releaseId),
        session.state,
    ) { release, s ->
        ReleaseDetailUiState(
            release = release,
            missing = release == null,
            serverHost = (s as? SessionState.LoggedIn)?.serverUrl?.let(::hostOf),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleSaved() {
        val release = state.value?.release ?: return
        viewModelScope.launch { releases.setSaved(release, !release.saved) }
    }

    fun toggleSeen() {
        val release = state.value?.release ?: return
        viewModelScope.launch { releases.setSeen(listOf(release.id), !release.seen) }
    }

    /** Opening the release page counts as having seen it. */
    fun markSeen() {
        val release = state.value?.release ?: return
        if (!release.seen) viewModelScope.launch { releases.setSeen(listOf(release.id), seen = true) }
    }

    fun play() {
        val release = state.value?.release ?: return
        val id = release.libraryAlbumId ?: return
        viewModelScope.launch {
            playAlbum(albums, playback, id)
            releases.setSeen(listOf(release.id), seen = true)
        }
    }
}

private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()?.ifBlank { null }
