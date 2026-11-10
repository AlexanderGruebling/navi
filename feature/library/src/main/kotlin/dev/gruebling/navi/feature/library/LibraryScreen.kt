package dev.gruebling.navi.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.gruebling.navi.core.data.repository.AlbumSort
import dev.gruebling.navi.core.model.Album
import dev.gruebling.navi.core.model.Artist
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.ui.component.CoverTile
import dev.gruebling.navi.core.ui.component.NewBadge
import dev.gruebling.navi.core.ui.component.SectionHeader
import dev.gruebling.navi.core.ui.component.TrackRow
import dev.gruebling.navi.core.ui.image.CoverArt
import kotlinx.collections.immutable.ImmutableList
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun LibraryScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    LibraryContent(
        state = state,
        albums = albums,
        onSelectTab = viewModel::selectTab,
        onSortAlbums = viewModel::setAlbumSort,
        onRefresh = viewModel::refresh,
        onPlayFavorites = viewModel::playFavorites,
        onOpenPlaylist = onOpenPlaylist,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    state: LibraryUiState,
    albums: LazyPagingItems<Album>,
    onSelectTab: (LibraryTab) -> Unit,
    onSortAlbums: (AlbumSort) -> Unit,
    onRefresh: () -> Unit,
    onPlayFavorites: (startIndex: Int, shuffle: Boolean) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One scroll position per tab, kept while switching between them.
    val playlistsList = rememberLazyListState()
    val albumsGrid = rememberLazyGridState()
    val artistsList = rememberLazyListState()
    val favoritesList = rememberLazyListState()

    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryTab.entries.forEach { tab ->
                FilterChip(
                    selected = tab == state.tab,
                    onClick = { onSelectTab(tab) },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        }
        if (state.refreshFailed) {
            Text(
                stringResource(R.string.library_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        ) {
            when (state.tab) {
                LibraryTab.Playlists -> PlaylistsTab(state.playlists, playlistsList, onOpenPlaylist)
                LibraryTab.Albums -> AlbumsTab(albums, state.albumSort, albumsGrid, onSortAlbums, onOpenAlbum)
                LibraryTab.Artists -> ArtistsTab(state, artistsList, onOpenArtist)
                LibraryTab.Favorites -> FavoritesTab(state, favoritesList, onPlayFavorites, onOpenAlbum, onOpenArtist)
            }
        }
    }
}

@Composable
private fun PlaylistsTab(playlists: ImmutableList<Playlist>, listState: LazyListState, onOpen: (String) -> Unit) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(playlists, key = { it.id.value }, contentType = { "playlist" }) { playlist ->
            ListItem(
                headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            pluralStringResource(UiR.plurals.song_count, playlist.songCount, playlist.songCount),
                            playlist.comment,
                        ).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { CoverArt(playlist.coverArt?.value, 56.dp, Modifier.size(56.dp)) },
                trailingContent = if (playlist.hasNewContent) ({ NewBadge() }) else null,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
                modifier = Modifier.clickable { onOpen(playlist.id.value) },
            )
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: LazyPagingItems<Album>,
    sort: AlbumSort,
    gridState: LazyGridState,
    onSort: (AlbumSort) -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }, contentType = "sort") {
            SortRow(sort, albums.itemCount, onSort)
        }
        items(
            count = albums.itemCount,
            key = albums.itemKey { it.id.value },
            contentType = albums.itemContentType { "album" },
        ) { index ->
            val album = albums[index]
            if (album == null) {
                // Placeholder while the page loads from Room: same shape, no layout jump.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 44.dp)
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
                )
            } else {
                CoverTile(
                    title = album.name,
                    subtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                    coverArtId = album.coverArt?.value,
                    onClick = { onOpen(album.id.value) },
                    size = 200.dp,
                    fillWidth = true,
                    subtitleLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SortRow(sort: AlbumSort, count: Int, onSort: (AlbumSort) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            pluralStringResource(R.plurals.album_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(stringResource(sort.label))
                Icon(painterResource(UiR.drawable.ic_expand_more), contentDescription = null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AlbumSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.label)) },
                        onClick = {
                            menuOpen = false
                            onSort(option)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistsTab(state: LibraryUiState, listState: LazyListState, onOpen: (String) -> Unit) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        state.artistGroups.forEach { group ->
            stickyHeader(key = "letter-${group.letter}", contentType = "letter") {
                Text(
                    group.letter,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            items(group.artists, key = { it.id.value }, contentType = { "artist" }) { artist ->
                ArtistRow(artist, onClick = { onOpen(artist.id.value) })
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(pluralStringResource(R.plurals.album_count, artist.albumCount, artist.albumCount)) },
        leadingContent = { CoverArt(artist.coverArt?.value, 56.dp, Modifier.size(56.dp), shape = CircleShape) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FavoritesTab(
    state: LibraryUiState,
    listState: LazyListState,
    onPlay: (startIndex: Int, shuffle: Boolean) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val empty = state.favoriteSongs.isEmpty() && state.favoriteAlbums.isEmpty() && state.favoriteArtists.isEmpty()
    if (empty && !state.isRefreshing) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                stringResource(R.string.library_favorites_empty),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.favoriteAlbums.isNotEmpty()) {
            item(key = "albums-header", contentType = "header") { SectionHeader(stringResource(R.string.library_tab_albums)) }
            item(key = "albums", contentType = "row") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.favoriteAlbums, key = { it.id.value }) { album ->
                        CoverTile(
                            title = album.name,
                            subtitle = album.artist,
                            coverArtId = album.coverArt?.value,
                            size = 130.dp,
                            subtitleLines = 1,
                            onClick = { onOpenAlbum(album.id.value) },
                        )
                    }
                }
            }
        }
        if (state.favoriteArtists.isNotEmpty()) {
            item(key = "artists-header", contentType = "header") { SectionHeader(stringResource(R.string.library_tab_artists)) }
            item(key = "artists", contentType = "row") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.favoriteArtists, key = { it.id.value }) { artist ->
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
        if (state.favoriteSongs.isNotEmpty()) {
            item(key = "songs-header", contentType = "songsHeader") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                    SectionHeader(stringResource(R.string.library_songs), Modifier.weight(1f))
                    IconButton(onClick = { onPlay(0, true) }) {
                        Icon(painterResource(UiR.drawable.ic_shuffle), stringResource(UiR.string.cd_shuffle))
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(onClick = { onPlay(0, false) }, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(UiR.drawable.ic_play), stringResource(UiR.string.cd_play))
                    }
                }
            }
            itemsIndexed(state.favoriteSongs, key = { _, s -> "song-${s.id.value}" }, contentType = { _, _ -> "track" }) { index, song ->
                TrackRow(
                    title = song.title,
                    subtitle = song.artist,
                    durationMs = song.durationSec * 1_000L,
                    coverArtId = song.coverArt?.value,
                    isCurrent = song.id.value == state.currentSongId,
                    onClick = { onPlay(index, false) },
                )
            }
        }
    }
}

private val LibraryTab.label: Int
    get() = when (this) {
        LibraryTab.Playlists -> R.string.library_tab_playlists
        LibraryTab.Albums -> R.string.library_tab_albums
        LibraryTab.Artists -> R.string.library_tab_artists
        LibraryTab.Favorites -> R.string.library_tab_favorites
    }

private val AlbumSort.label: Int
    get() = when (this) {
        AlbumSort.RecentlyAdded -> R.string.library_sort_recently_added
        AlbumSort.Name -> R.string.library_sort_name
        AlbumSort.Artist -> R.string.library_sort_artist
    }
