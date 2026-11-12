package dev.gruebling.navi.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.data.session.SessionState
import dev.gruebling.navi.core.datastore.StreamingSettingsStore
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistSettings
import dev.gruebling.navi.core.model.ReplayGainMode
import dev.gruebling.navi.core.model.StreamingSettings
import dev.gruebling.navi.core.playback.SignOut
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PlaylistGroupUi(val group: PlaylistGroup, val playlists: ImmutableList<Playlist>)

@Immutable
data class SettingsUiState(
    val serverUrl: String? = null,
    val username: String? = null,
    val wifiQuality: QualityPresetUi = QualityPresetUi(QualityPreset.Original),
    val mobileQuality: QualityPresetUi = QualityPresetUi(QualityPreset.Original),
    val replayGainMode: ReplayGainMode = ReplayGainMode.Auto,
    val playlistGroups: ImmutableList<PlaylistGroupUi> = persistentListOf(),
)

/** Wrapper so the enum stays internal while the state class is public. */
@Immutable
data class QualityPresetUi internal constructor(internal val preset: QualityPreset)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    session: SessionManager,
    private val streaming: StreamingSettingsStore,
    private val playlists: PlaylistRepository,
    private val signOut: SignOut,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        session.state,
        streaming.settings,
        streaming.replayGainMode,
        playlists.observePlaylists(),
    ) { s, quality, replayGain, list ->
        val loggedIn = s as? SessionState.LoggedIn
        SettingsUiState(
            serverUrl = loggedIn?.serverUrl,
            username = loggedIn?.username,
            wifiQuality = QualityPresetUi(QualityPreset.of(quality.unmetered)),
            mobileQuality = QualityPresetUi(QualityPreset.of(quality.metered)),
            replayGainMode = replayGain,
            // Same order as on Home: group, pinned first, manual order.
            playlistGroups = list.groupBy { it.group }
                .toSortedMap(compareBy { it.ordinal })
                .map { (group, items) -> PlaylistGroupUi(group, items.toImmutableList()) }
                .toImmutableList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    internal fun setWifiQuality(preset: QualityPreset) = updateStreaming { it.copy(unmetered = preset.quality) }
    internal fun setMobileQuality(preset: QualityPreset) = updateStreaming { it.copy(metered = preset.quality) }

    fun setReplayGainMode(mode: ReplayGainMode) {
        viewModelScope.launch { streaming.setReplayGainMode(mode) }
    }

    private fun updateStreaming(change: (StreamingSettings) -> StreamingSettings) {
        viewModelScope.launch { streaming.update(change(streaming.settings.first())) }
    }

    /** null = back to automatic grouping. */
    fun setGroup(playlist: Playlist, group: PlaylistGroup?) {
        viewModelScope.launch {
            // A different group has its own order; the manual position doesn't carry over.
            playlists.updateSettings(playlist.id, playlist.settings().copy(groupOverride = group, sortOrder = null))
        }
    }

    fun setPinned(playlist: Playlist, pinned: Boolean) {
        viewModelScope.launch { playlists.updateSettings(playlist.id, playlist.settings().copy(pinned = pinned)) }
    }

    /** Moves a playlist one step up (-1) or down (+1) within its group, as shown. */
    fun move(playlist: Playlist, direction: Int) {
        val group = state.value.playlistGroups.firstOrNull { it.group == playlist.group }?.playlists ?: return
        val from = group.indexOf(playlist)
        val to = from + direction
        // Pinned playlists always come first on Home, so moving only makes sense among the same kind.
        if (from < 0 || to !in group.indices || group[to].pinned != playlist.pinned) return
        val reordered = group.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { playlists.reorder(reordered.map { it.id }) }
    }

    /** Saves a dragged order: [ordered] is one group's playlists as they now appear. */
    fun reorder(ordered: List<Playlist>) {
        viewModelScope.launch { playlists.reorder(ordered.map { it.id }) }
    }

    fun logout() {
        signOut()
    }

    private fun Playlist.settings() = PlaylistSettings(groupOverride = groupOverride, pinned = pinned, sortOrder = sortOrder)
}
