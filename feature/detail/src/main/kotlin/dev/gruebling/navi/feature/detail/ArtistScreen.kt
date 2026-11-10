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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.ui.component.CoverTile
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun ArtistScreen(artistId: String, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val viewModel = hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = "artist-$artistId") {
        it.create(artistId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArtistContent(state, onBack, onOpenAlbum, onShuffle = viewModel::shuffleAll, onToggleStar = viewModel::toggleStar)
}

/** Artist page: round image, name, star + shuffle all, then the albums (oldest first) as a grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistContent(
    state: ArtistUiState,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onShuffle: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val headerGone by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    val barAlpha by animateFloatAsState(if (headerGone) 1f else 0f, label = "barAlpha")
    val artist = state.artist

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 56.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoverArt(artist?.coverArt?.value, 200.dp, Modifier.size(200.dp), shape = CircleShape)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        artist?.name.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val count = artist?.albumCount ?: 0
                        Text(
                            pluralStringResource(R.plurals.detail_album_count, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val starred = artist?.starred == true
                        IconToggleButton(checked = starred, onCheckedChange = { onToggleStar() }, enabled = artist != null) {
                            Icon(
                                painterResource(if (starred) UiR.drawable.ic_star else UiR.drawable.ic_star_outline),
                                stringResource(if (starred) UiR.string.cd_unfavorite else UiR.string.cd_favorite),
                                tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledIconButton(
                            onClick = onShuffle,
                            enabled = state.albums.isNotEmpty() && !state.isPreparingPlayback,
                            modifier = Modifier.size(56.dp),
                        ) {
                            if (state.isPreparingPlayback) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(painterResource(UiR.drawable.ic_shuffle), stringResource(UiR.string.cd_shuffle), Modifier.size(28.dp))
                            }
                        }
                    }
                    if (state.refreshFailed) {
                        Text(
                            stringResource(R.string.detail_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
            if (state.isLoading && state.albums.isEmpty()) {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            items(state.albums, key = { it.id.value }, contentType = { "album" }) { album ->
                CoverTile(
                    title = album.name,
                    subtitle = album.year?.toString(),
                    coverArtId = album.coverArt?.value,
                    onClick = { onOpenAlbum(album.id.value) },
                    size = 200.dp,
                    fillWidth = true,
                    subtitleLines = 1,
                )
            }
        }
        TopAppBar(
            title = {
                Text(
                    artist?.name.orEmpty(),
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
