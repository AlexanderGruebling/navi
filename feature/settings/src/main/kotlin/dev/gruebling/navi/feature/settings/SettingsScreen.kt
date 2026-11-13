package dev.gruebling.navi.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.ReplayGainMode
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun SettingsScreen(
    versionName: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { ViewModelActions(viewModel) }
    SettingsContent(state, versionName, onBack, actions)
}

/** Everything the settings screen can change. Kept apart from the ViewModel so the content can be previewed. */
internal interface SettingsActions {
    fun setQuality(which: QualityDialog, preset: QualityPreset)
    fun setReplayGainMode(mode: ReplayGainMode)
    fun setDownloadOverMobileData(allow: Boolean)
    fun removeAllDownloads()
    fun setGroup(playlist: Playlist, group: PlaylistGroup?)
    fun setPinned(playlist: Playlist, pinned: Boolean)
    fun move(playlist: Playlist, direction: Int)
    fun reorder(ordered: List<Playlist>)
    fun logout()
}

private class ViewModelActions(private val vm: SettingsViewModel) : SettingsActions {
    override fun setQuality(which: QualityDialog, preset: QualityPreset) = when (which) {
        QualityDialog.Wifi -> vm.setWifiQuality(preset)
        QualityDialog.Mobile -> vm.setMobileQuality(preset)
        QualityDialog.Download -> vm.setDownloadQuality(preset)
    }
    override fun setReplayGainMode(mode: ReplayGainMode) = vm.setReplayGainMode(mode)
    override fun setDownloadOverMobileData(allow: Boolean) = vm.setDownloadOverMobileData(allow)
    override fun removeAllDownloads() = vm.removeAllDownloads()
    override fun setGroup(playlist: Playlist, group: PlaylistGroup?) = vm.setGroup(playlist, group)
    override fun setPinned(playlist: Playlist, pinned: Boolean) = vm.setPinned(playlist, pinned)
    override fun move(playlist: Playlist, direction: Int) = vm.move(playlist, direction)
    override fun reorder(ordered: List<Playlist>) = vm.reorder(ordered)
    override fun logout() = vm.logout()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(state: SettingsUiState, versionName: String, onBack: () -> Unit, actions: SettingsActions) {
    var qualityDialog by rememberSaveable { mutableStateOf<QualityDialog?>(null) }
    var replayGainDialog by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveDownloads by rememberSaveable { mutableStateOf(false) }
    var playlistDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val reorder = rememberPlaylistReorder(state.playlistGroups, listState, actions::reorder)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(UiR.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            accountSection(state, onLogout = { confirmLogout = true })
            streamingSection(state, onQuality = { qualityDialog = it }, onReplayGain = { replayGainDialog = true })
            downloadsSection(
                state,
                onQuality = { qualityDialog = QualityDialog.Download },
                onMobileData = actions::setDownloadOverMobileData,
                onRemoveAll = { confirmRemoveDownloads = true },
            )
            playlistsSection(reorder, onOpen = { playlistDialog = it.id.value }, onMove = actions::move)
            aboutSection(versionName)
        }
    }

    qualityDialog?.let { which ->
        QualityChooser(
            title = stringResource(which.title),
            selected = which.selected(state),
            onSelect = {
                actions.setQuality(which, it)
                qualityDialog = null
            },
            onDismiss = { qualityDialog = null },
        )
    }

    if (replayGainDialog) {
        ReplayGainChooser(
            selected = state.replayGainMode,
            onSelect = {
                actions.setReplayGainMode(it)
                replayGainDialog = false
            },
            onDismiss = { replayGainDialog = false },
        )
    }

    if (confirmRemoveDownloads) {
        ConfirmRemoveDownloads(
            onConfirm = {
                confirmRemoveDownloads = false
                actions.removeAllDownloads()
            },
            onDismiss = { confirmRemoveDownloads = false },
        )
    }

    playlistDialog?.let { id ->
        val playlist = state.playlistGroups.flatMap { it.playlists }.firstOrNull { it.id.value == id }
        if (playlist == null) {
            playlistDialog = null
        } else {
            PlaylistOptions(
                playlist = playlist,
                onGroup = { actions.setGroup(playlist, it) },
                onPinned = { actions.setPinned(playlist, it) },
                onDismiss = { playlistDialog = null },
            )
        }
    }

    if (confirmLogout) {
        ConfirmLogout(
            onConfirm = {
                confirmLogout = false
                actions.logout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}
