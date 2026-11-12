package dev.gruebling.navi.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.ui.component.AccentPlayButton
import dev.gruebling.navi.core.ui.component.CoverTile
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.StatusLabel
import dev.gruebling.navi.core.ui.component.TrackRow
import dev.gruebling.navi.core.ui.component.TrackRowInset
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviType
import kotlinx.collections.immutable.ImmutableList
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun ArtistScreen(
    artistId: String,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val viewModel = hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = "artist-$artistId") {
        it.create(artistId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArtistContent(
        state = state,
        onBack = onBack,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onShuffle = viewModel::shuffleAll,
        onToggleStar = viewModel::toggleStar,
        onPlayTopSong = viewModel::playTopSong,
    )
}

/**
 * Artist page: round image, name, album count, favorite + red "shuffle all", then top songs, the albums (oldest
 * first), the biography and similar artists. The last three come from Last.fm via the server and are left out
 * when it has none.
 */
@Composable
private fun ArtistContent(
    state: ArtistUiState,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onShuffle: () -> Unit,
    onToggleStar: () -> Unit,
    onPlayTopSong: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val headerGone by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    val barAlpha by animateFloatAsState(if (headerGone) 1f else 0f, label = "barAlpha")
    val artist = state.artist

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = gridState,
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 56.dp, bottom = 8.dp),
                ) {
                    CoverArt(
                        artist?.coverArt?.value,
                        200.dp,
                        Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                        shape = CircleShape,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        artist?.name.orEmpty(),
                        style = NaviType.dotTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val count = artist?.albumCount ?: 0
                        StatusLabel(pluralStringResource(R.plurals.detail_album_count, count, count), Modifier.weight(1f))
                        val starred = artist?.starred == true
                        IconToggleButton(checked = starred, onCheckedChange = { onToggleStar() }, enabled = artist != null) {
                            Icon(
                                painterResource(if (starred) UiR.drawable.ic_heart else UiR.drawable.ic_heart_outline),
                                stringResource(if (starred) UiR.string.cd_unfavorite else UiR.string.cd_favorite),
                                tint = if (starred) MaterialTheme.colorScheme.primary else NaviColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        AccentPlayButton(
                            onClick = onShuffle,
                            enabled = state.albums.isNotEmpty(),
                            busy = state.isPreparingPlayback,
                            icon = UiR.drawable.ic_shuffle,
                            contentDescription = stringResource(UiR.string.cd_shuffle),
                        )
                    }
                    if (state.refreshFailed) {
                        Text(
                            stringResource(R.string.detail_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = NaviColors.TextSecondary,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
            if (state.topSongs.isNotEmpty()) {
                item(key = "top", span = { GridItemSpan(maxLineSpan) }, contentType = "top") {
                    TopSongs(state.topSongs, state.currentSongId, onPlayTopSong)
                }
            }
            if (state.albums.isNotEmpty()) {
                item(key = "albums-label", span = { GridItemSpan(maxLineSpan) }, contentType = "label") {
                    SectionLabel(stringResource(R.string.detail_section_albums))
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
            state.biography?.let { bio ->
                item(key = "about", span = { GridItemSpan(maxLineSpan) }, contentType = "about") {
                    About(bio, state.lastFmUrl)
                }
            }
            if (state.similarArtists.isNotEmpty()) {
                item(key = "similar", span = { GridItemSpan(maxLineSpan) }, contentType = "similar") {
                    SimilarArtists(state.similarArtists, onOpenArtist)
                }
            }
        }
        DetailTopBar(
            label = stringResource(R.string.detail_label_artist),
            title = artist?.name.orEmpty(),
            scrolled = { barAlpha },
            onBack = onBack,
        )
    }
}

/** Collapsed number of top songs; the rest show after "Show all". */
private const val TopSongsCollapsed = 5

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    MonoLabel(text, modifier.padding(top = 16.dp))
}

/** One grid item for all rows: the grid's 16 dp item spacing would pull track rows apart. */
@Composable
private fun TopSongs(songs: ImmutableList<TrackUi>, currentSongId: String?, onPlay: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) songs.size else minOf(songs.size, TopSongsCollapsed)
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.detail_section_top_songs), Modifier.padding(bottom = 8.dp))
        for (index in 0 until shown) {
            val track = songs[index]
            key(track.key) {
                TrackRow(
                    // TrackRow pads itself by 8 dp; let it reach into the screen padding so the text lines up.
                    modifier = Modifier.bleed(ScreenPadding - TrackRowInset),
                    title = track.title,
                    subtitle = track.subtitle,
                    durationMs = track.durationMs,
                    coverArtId = track.coverArtId,
                    isCurrent = track.songId == currentSongId,
                    isStarred = track.starred,
                    onClick = { onPlay(index) },
                )
            }
        }
        if (songs.size > TopSongsCollapsed) {
            MonoLabel(
                stringResource(if (expanded) R.string.detail_show_less else R.string.detail_show_all),
                color = NaviColors.White,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** Biography, a few lines until tapped. */
@Composable
private fun About(biography: String, lastFmUrl: String?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.detail_section_about), Modifier.padding(bottom = 8.dp))
        Text(
            biography,
            style = MaterialTheme.typography.bodyMedium,
            color = NaviColors.TextSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clickable(enabled = overflows || expanded, onClickLabel = stringResource(R.string.detail_show_all)) {
                    expanded = !expanded
                },
        )
        if (lastFmUrl != null && (expanded || !overflows)) {
            MonoLabel(
                stringResource(R.string.detail_read_more_lastfm),
                color = NaviColors.White,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { runCatching { uriHandler.openUri(lastFmUrl) } }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** Horizontal row of round artist images, reaching to the screen edges. */
@Composable
private fun SimilarArtists(artists: ImmutableList<Artist>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.detail_section_similar), Modifier.padding(bottom = 12.dp))
        LazyRow(
            modifier = Modifier.bleed(ScreenPadding),
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(artists, key = { it.id.value }) { artist ->
                Column(
                    Modifier
                        .width(SimilarSize)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpen(artist.id.value) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoverArt(artist.coverArt?.value, SimilarSize, Modifier.size(SimilarSize), shape = CircleShape)
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private val SimilarSize = 96.dp

/** Widens the element by [amount] on both sides, past its parent's padding. */
private fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
