package dev.gruebling.navi.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gruebling.navi.core.ui.component.AccentPlayButton
import dev.gruebling.navi.core.ui.component.NaviChip
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.StatusLabel
import dev.gruebling.navi.core.model.EnqueuePosition
import dev.gruebling.navi.core.ui.component.SongActionsSheet
import dev.gruebling.navi.core.ui.component.TrackRow
import dev.gruebling.navi.core.ui.component.TrackRowInset
import dev.gruebling.navi.core.ui.component.collectionSummary
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.image.CoverMosaic
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.R as UiR

/**
 * Album / playlist page: centered cover, left-aligned title block with a mono summary line, shuffle + red play
 * button, then the tracks. The top bar shows [kindLabel] ("PLAYLIST") and swaps it for the title on scroll.
 */
@Composable
internal fun CollectionScreen(
    state: CollectionUiState,
    kindLabel: String,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onShuffle: () -> Unit,
    onEnqueue: (index: Int, EnqueuePosition) -> Unit,
    onShowNewer: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var actionsFor by remember { mutableStateOf<Int?>(null) }
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
                        NaviChip(stringResource(R.string.detail_reshuffled), selected = true, onClick = onShowNewer, role = Role.Button)
                    }
                }
            }
            if (state.refreshFailed) {
                item(key = "offline", contentType = "banner") {
                    Text(
                        stringResource(R.string.detail_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = NaviColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
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
                    modifier = Modifier.padding(horizontal = TrackRowInset),
                    title = track.title,
                    subtitle = track.subtitle,
                    durationMs = track.durationMs,
                    coverArtId = track.coverArtId,
                    trackNumber = track.trackNumber,
                    isCurrent = track.songId == state.currentSongId,
                    isStarred = track.starred,
                    onClick = { onPlay(index) },
                    onLongClick = { actionsFor = index },
                )
            }
        }
        actionsFor?.let { index ->
            state.tracks.getOrNull(index)?.let { track ->
                SongActionsSheet(
                    title = track.title,
                    subtitle = track.subtitle,
                    // Album tracks have no own cover in the list; the album's is the same.
                    coverArtId = track.coverArtId ?: state.header?.coverArtId,
                    onEnqueue = { position -> onEnqueue(index, position) },
                    onDismiss = { actionsFor = null },
                )
            }
        }
        DetailTopBar(
            label = kindLabel,
            title = state.header?.title.orEmpty(),
            scrolled = { barAlpha },
            onBack = onBack,
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
            .padding(top = 56.dp, start = ScreenPadding, end = ScreenPadding, bottom = 8.dp),
    ) {
        val coverModifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally)
        if (header?.coverArtId == null && header != null && header.mosaic.size >= 4) {
            CoverMosaic(header.mosaic, 220.dp, coverModifier, shape = MaterialTheme.shapes.large)
        } else {
            CoverArt(header?.coverArtId, 220.dp, coverModifier, shape = MaterialTheme.shapes.large)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            header?.title.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        header?.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = NaviColors.White,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        header?.comment?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = NaviColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (header != null) {
            StatusLabel(collectionSummary(header.songCount, header.durationSec), Modifier.padding(top = 8.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onShuffle, enabled = state.tracks.isNotEmpty(), modifier = Modifier.offset(x = (-12).dp)) {
                Icon(
                    painterResource(UiR.drawable.ic_shuffle),
                    contentDescription = stringResource(UiR.string.cd_shuffle),
                    tint = NaviColors.TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            AccentPlayButton(onClick = onPlay, enabled = state.tracks.isNotEmpty())
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
