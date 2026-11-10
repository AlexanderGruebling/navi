package dev.gruebling.navi.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.component.TrackRow
import dev.gruebling.navi.core.ui.component.collectionSummary
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.image.CoverMosaic
import dev.gruebling.navi.core.ui.R as UiR

/** Album / playlist page: large cover header, play + shuffle, track list. The bar title fades in on scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionScreen(
    state: CollectionUiState,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onShuffle: () -> Unit,
    onShowNewer: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val barAlpha by animateFloatAsState(if (headerGone) 1f else 0f, label = "barAlpha")

    LaunchedEffect(listState) {
        snapshotFlowScrolled(listState) { onUserInteraction() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "header", contentType = "header") {
                Header(state, onPlay = { onPlay(0) }, onShuffle = onShuffle)
            }
            if (state.hasNewerContent) {
                item(key = "newer", contentType = "chip") {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        FilledTonalButton(onClick = onShowNewer) { Text(stringResource(R.string.detail_reshuffled)) }
                    }
                }
            }
            if (state.refreshFailed) {
                item(key = "offline", contentType = "banner") {
                    Text(
                        stringResource(R.string.detail_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (state.isLoading && state.tracks.isEmpty()) {
                item(key = "loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            itemsIndexed(state.tracks, key = { _, t -> t.key }, contentType = { _, _ -> "track" }) { index, track ->
                TrackRow(
                    title = track.title,
                    subtitle = track.subtitle,
                    durationMs = track.durationMs,
                    coverArtId = track.coverArtId,
                    trackNumber = track.trackNumber,
                    isCurrent = track.songId == state.currentSongId,
                    isStarred = track.starred,
                    onClick = { onPlay(index) },
                )
            }
        }
        TopAppBar(
            title = {
                Text(
                    state.header?.title.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = barAlpha },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(UiR.drawable.ic_arrow_back), contentDescription = stringResource(UiR.string.cd_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = barAlpha),
            ),
        )
    }
}

@Composable
private fun Header(state: CollectionUiState, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val header = state.header
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 56.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val coverModifier = Modifier.size(240.dp)
        if (header?.coverArtId == null && header != null && header.mosaic.size >= 4) {
            CoverMosaic(header.mosaic, 240.dp, coverModifier, shape = MaterialTheme.shapes.large)
        } else {
            CoverArt(header?.coverArtId, 240.dp, coverModifier, shape = MaterialTheme.shapes.large)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            header?.title.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        header?.subtitle?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        header?.comment?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (header != null) {
                Text(
                    collectionSummary(header.songCount, header.durationSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onShuffle, enabled = state.tracks.isNotEmpty()) {
                Icon(painterResource(UiR.drawable.ic_shuffle), contentDescription = stringResource(UiR.string.cd_shuffle))
            }
            FilledIconButton(
                onClick = onPlay,
                enabled = state.tracks.isNotEmpty(),
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    painterResource(UiR.drawable.ic_play),
                    contentDescription = stringResource(UiR.string.cd_play),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private suspend fun snapshotFlowScrolled(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onScrolled: () -> Unit,
) {
    androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
        .collect { if (it) onScrolled() }
}
