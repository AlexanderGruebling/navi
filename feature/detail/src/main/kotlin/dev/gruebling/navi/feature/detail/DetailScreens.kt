package dev.gruebling.navi.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(key = "playlist-$playlistId") {
        it.create(playlistId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectionScreen(
        state = state,
        kindLabel = stringResource(R.string.detail_label_playlist),
        onBack = onBack,
        onPlay = viewModel::play,
        onShuffle = viewModel::shuffle,
        onEnqueue = viewModel::enqueue,
        onDownload = viewModel::download,
        onRemoveDownload = viewModel::removeDownload,
        onShowNewer = viewModel::showNewerContent,
        onUserInteraction = viewModel::onUserInteraction,
    )
}

@Composable
fun AlbumScreen(albumId: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(key = "album-$albumId") {
        it.create(albumId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectionScreen(
        state = state,
        kindLabel = stringResource(R.string.detail_label_album),
        onBack = onBack,
        onPlay = viewModel::play,
        onShuffle = viewModel::shuffle,
        onEnqueue = viewModel::enqueue,
        onDownload = viewModel::download,
        onRemoveDownload = viewModel::removeDownload,
    )
}
