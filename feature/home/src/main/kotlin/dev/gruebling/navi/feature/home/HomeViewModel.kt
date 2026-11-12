package dev.gruebling.navi.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gruebling.navi.core.data.repository.AlbumRepository
import dev.gruebling.navi.core.data.repository.PlaylistRepository
import dev.gruebling.navi.core.model.Album
import dev.gruebling.navi.core.model.AlbumListType
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.data.session.SessionManager
import dev.gruebling.navi.core.data.session.SessionState
import dev.gruebling.navi.core.playback.PlaybackController
import dev.gruebling.navi.core.data.util.NetworkMonitor
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PlaylistSection(val group: PlaylistGroup, val playlists: ImmutableList<Playlist>)

@Immutable
data class AlbumSection(val type: AlbumListType, val albums: ImmutableList<Album>)

@Immutable
data class HomeUiState(
    val playlistSections: ImmutableList<PlaylistSection> = persistentListOf(),
    val albumSections: ImmutableList<AlbumSection> = persistentListOf(),
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /** Nothing cached yet (first start): show a spinner instead of an empty page. */
    val isLoadingFirstTime: Boolean = true,
    /** Login name, for the greeting and the avatar. */
    val username: String? = null,
    /** Host name of the server ("music.example.com"), shown in the status line. */
    val serverHost: String? = null,
    /** Whether the last refresh reached the server; null until the first one has finished. False without network. */
    val serverReachable: Boolean? = null,
    /** The device has no network at all (not just an unreachable server). */
    val noNetwork: Boolean = false,
)

private val HOME_ALBUM_LISTS = listOf(AlbumListType.RecentlyPlayed, AlbumListType.RecentlyAdded, AlbumListType.MostPlayed)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val albums: AlbumRepository,
    private val playback: PlaybackController,
    private val session: SessionManager,
    network: NetworkMonitor,
) : ViewModel() {
    private val hasNetwork = network.hasNetwork.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val refreshing = MutableStateFlow(false)
    /** Null until the first refresh has finished. */
    private val refreshFailed = MutableStateFlow<Boolean?>(null)

    private val albumSections = combine(HOME_ALBUM_LISTS.map { type -> albums.observeAlbumList(type) }) { lists ->
        HOME_ALBUM_LISTS.zip(lists.toList()) { type, list -> AlbumSection(type, list.toImmutableList()) }
            .filter { it.albums.isNotEmpty() }
            .toImmutableList()
    }

    val state: StateFlow<HomeUiState> = combine(
        playlists.observePlaylists(),
        albumSections,
        combine(refreshing, refreshFailed, hasNetwork, ::Triple),
        session.state,
    ) { playlistList, albumList, (isRefreshing, failed, online), sessionState ->
        val login = sessionState as? SessionState.LoggedIn
        val sections = playlistList.groupBy { it.group }
            .toSortedMap(compareBy { it.ordinal })
            .map { (group, items) -> PlaylistSection(group, items.toImmutableList()) }
            .toImmutableList()
        HomeUiState(
            playlistSections = sections,
            albumSections = albumList,
            isRefreshing = isRefreshing,
            refreshFailed = online && failed == true,
            isLoadingFirstTime = sections.isEmpty() && albumList.isEmpty() && failed != true,
            username = login?.username,
            serverHost = login?.serverUrl?.let(::hostOf),
            // Without network the answer is known right away, whatever the last refresh said.
            serverReachable = if (!online) false else failed?.not(),
            noNetwork = !online,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh(userInitiated = false)
        // Back online: reload, so the status and the lists catch up without a pull.
        viewModelScope.launch {
            hasNetwork.drop(1).filter { it }.collect { refresh(userInitiated = false) }
        }
    }

    fun refresh(userInitiated: Boolean = true) {
        if (refreshing.value) return
        viewModelScope.launch {
            if (userInitiated) refreshing.value = true
            val results = buildList {
                add(async { playlists.refreshPlaylists() })
                HOME_ALBUM_LISTS.forEach { add(async { albums.refreshAlbumList(it) }) }
            }.awaitAll()
            refreshFailed.value = results.any { it.isFailure }
            refreshing.value = false
        }
    }

    /** Play button on a playlist card: uses cached tracks, loads them first if there are none. */
    fun playPlaylist(id: PlaylistId) {
        viewModelScope.launch {
            var songs = playlists.observePlaylist(id).first()?.songs.orEmpty()
            if (songs.isEmpty()) {
                playlists.refreshPlaylist(id)
                songs = playlists.observePlaylist(id).first()?.songs.orEmpty()
            }
            if (songs.isNotEmpty()) playback.playSongs(songs)
        }
    }
}

/** "https://music.example.com:4533/navidrome" → "music.example.com". Plain string work: never fails, keeps any host. */
internal fun hostOf(serverUrl: String): String? =
    serverUrl.substringAfter("://").substringBefore('/').substringBefore('?').substringAfterLast('@')
        .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
        .ifBlank { null }
