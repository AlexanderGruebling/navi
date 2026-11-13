package dev.gruebling.navi.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.ui.component.CoverTile
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.ScreenTitle
import dev.gruebling.navi.core.ui.component.TrackRow
import dev.gruebling.navi.core.ui.component.TrackRowInset
import dev.gruebling.navi.core.ui.theme.NaviColors
import kotlinx.coroutines.flow.filter
import dev.gruebling.navi.core.ui.R as UiR
import dev.gruebling.navi.core.ui.component.SongActionsSheet
import dev.gruebling.navi.core.model.EnqueuePosition
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

@Composable
fun SearchScreen(
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onPlaySong = viewModel::playSong,
        onEnqueueSong = viewModel::enqueueSong,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        modifier = modifier,
    )
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onPlaySong: (Int) -> Unit,
    onEnqueueSong: (Int, EnqueuePosition) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Opening the tab with an empty field means "I want to type": focus it and bring up the keyboard.
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) focus.requestFocus()
    }
    // Scrolling through results hides the keyboard, so more of them are visible.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { keyboard?.hide() }
    }

    Column(modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.search_title))
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = { keyboard?.hide() },
            onClear = { onQueryChange(""); focus.requestFocus() },
            modifier = Modifier
                .padding(horizontal = ScreenPadding, vertical = 16.dp)
                .focusRequester(focus),
        )
        // Thin line while the server is still searching; reserves its height so nothing jumps.
        Box(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 32.dp)) {
            if (state.isSearching && state.query.isNotBlank()) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = NaviColors.Outline)
            }
        }

        when {
            state.query.isBlank() -> Message(stringResource(R.string.search_empty_hint))
            !state.hasResults && !state.isSearching -> Message(
                stringResource(if (state.remoteFailed) R.string.search_offline_no_results else R.string.search_no_results, state.query.trim()),
            )
            else -> Results(state, listState, onPlaySong, onEnqueueSong, onOpenAlbum, onOpenArtist)
        }
    }
}

@Composable
private fun Results(
    state: SearchUiState,
    listState: LazyListState,
    onPlaySong: (Int) -> Unit,
    onEnqueueSong: (Int, EnqueuePosition) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    var actionsFor by remember { mutableStateOf<Int?>(null) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.remoteFailed) {
            item(key = "offline", contentType = "banner") {
                Text(
                    stringResource(R.string.search_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = NaviColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
                )
            }
        }
        if (state.artists.isNotEmpty()) {
            item(key = "artists-header", contentType = "header") { MonoHeader(stringResource(R.string.search_artists)) }
            item(key = "artists", contentType = "row") {
                LazyRow(contentPadding = PaddingValues(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.artists, key = { it.id.value }) { artist ->
                        CoverTile(
                            title = artist.name,
                            subtitle = null,
                            coverArtId = artist.coverArt?.value,
                            size = 110.dp,
                            shape = CircleShape,
                            textAlign = TextAlign.Center,
                            onClick = { onOpenArtist(artist.id.value) },
                        )
                    }
                }
            }
        }
        if (state.albums.isNotEmpty()) {
            item(key = "albums-header", contentType = "header") { MonoHeader(stringResource(R.string.search_albums)) }
            item(key = "albums", contentType = "row") {
                LazyRow(contentPadding = PaddingValues(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.albums, key = { it.id.value }) { album ->
                        CoverTile(
                            title = album.name,
                            subtitle = album.artist,
                            coverArtId = album.coverArt?.value,
                            subtitleLines = 1,
                            onClick = { onOpenAlbum(album.id.value) },
                        )
                    }
                }
            }
        }
        if (state.songs.isNotEmpty()) {
            item(key = "songs-header", contentType = "header") { MonoHeader(stringResource(R.string.search_songs)) }
            itemsIndexed(state.songs, key = { _, s -> "song-${s.id.value}" }, contentType = { _, _ -> "track" }) { index, song ->
                TrackRow(
                    modifier = Modifier.padding(horizontal = TrackRowInset),
                    title = song.title,
                    subtitle = listOfNotNull(song.artist, song.album).joinToString(" · "),
                    durationMs = song.durationSec * 1_000L,
                    coverArtId = song.coverArt?.value,
                    isCurrent = song.id.value == state.currentSongId,
                    isStarred = song.starred,
                    onClick = { onPlaySong(index) },
                    onLongClick = { actionsFor = index },
                )
            }
        }
    }
    actionsFor?.let { index ->
        state.songs.getOrNull(index)?.let { song ->
            SongActionsSheet(
                title = song.title,
                subtitle = song.artist,
                coverArtId = song.coverArt?.value,
                onEnqueue = { position -> onEnqueueSong(index, position) },
                onDismiss = { actionsFor = null },
                songId = song.id.value,
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = NaviColors.TextSecondary)
    }
}

/** Mono caps section label ("ARTISTS"), as the design's search sections. */
@Composable
private fun MonoHeader(text: String) {
    MonoLabel(text, Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 12.dp))
}

/** White pill search field with black text, as in the design. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NaviColors.Black),
        cursorBrush = SolidColor(NaviColors.Black),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(NaviColors.White, CircleShape)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(UiR.drawable.ic_search), contentDescription = null, tint = NaviColors.Black)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = Color(0xFF6B6B6B), maxLines = 1)
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.cd_clear_search), tint = NaviColors.Black)
                    }
                }
            }
        },
    )
}
