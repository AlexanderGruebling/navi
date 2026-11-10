package dev.gruebling.navi.feature.home

import android.text.format.DateUtils
import androidx.activity.compose.ReportDrawnWhen
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.AlbumListType
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.ui.component.CoverTile
import dev.gruebling.navi.core.ui.component.NewBadge
import dev.gruebling.navi.core.ui.component.SectionHeader
import dev.gruebling.navi.core.ui.image.CoverArt
import java.time.LocalTime
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun HomeScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // "Fully drawn" = playlists visible, not just the first frame (startup benchmarks measure this).
    ReportDrawnWhen { !state.isLoadingFirstTime }
    HomeContent(
        state = state,
        onRefresh = { viewModel.refresh() },
        onOpenPlaylist = onOpenPlaylist,
        onPlayPlaylist = viewModel::playPlaylist,
        onOpenAlbum = onOpenAlbum,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (PlaylistId) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header", contentType = "header") {
                HomeHeader(onOpenSettings)
            }
            if (state.refreshFailed) {
                item(key = "offline", contentType = "banner") {
                    Text(
                        stringResource(R.string.home_offline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (state.isLoadingFirstTime) {
                item(key = "loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            state.playlistSections.forEach { section ->
                item(key = "group-${section.group}", contentType = "sectionHeader") {
                    SectionHeader(stringResource(section.group.title))
                }
                if (section.group == PlaylistGroup.Charts) {
                    // Charts as wide cards: a new chart week has to be impossible to miss.
                    items(section.playlists, key = { "chart-${it.id.value}" }, contentType = { "chartCard" }) { playlist ->
                        ChartCard(
                            playlist = playlist,
                            onOpen = { onOpenPlaylist(playlist.id.value) },
                            onPlay = { onPlayPlaylist(playlist.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    item(key = "row-${section.group}", contentType = "playlistRow") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(section.playlists, key = { it.id.value }, contentType = { "playlistTile" }) { playlist ->
                                CoverTile(
                                    title = playlist.name,
                                    subtitle = playlist.comment,
                                    coverArtId = playlist.coverArt?.value,
                                    isNew = playlist.hasNewContent,
                                    onClick = { onOpenPlaylist(playlist.id.value) },
                                )
                            }
                        }
                    }
                }
            }
            state.albumSections.forEach { section ->
                item(key = "albums-header-${section.type}", contentType = "sectionHeader") {
                    SectionHeader(stringResource(section.type.title))
                }
                item(key = "albums-${section.type}", contentType = "albumRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.albums, key = { it.id.value }, contentType = { "albumTile" }) { album ->
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
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..10 -> R.string.greeting_morning
            in 11..17 -> R.string.greeting_day
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(greeting),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(UiR.drawable.ic_settings), contentDescription = stringResource(UiR.string.cd_settings))
        }
    }
}

@Composable
private fun ChartCard(playlist: Playlist, onOpen: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(playlist.coverArt?.value, 96.dp, Modifier.size(96.dp), shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (playlist.hasNewContent) {
                    NewBadge()
                    Spacer(Modifier.height(4.dp))
                }
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                playlist.comment?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                playlist.changed?.let { changed ->
                    val relative = remember(changed) {
                        DateUtils.getRelativeTimeSpanString(changed.toEpochMilliseconds()).toString()
                    }
                    Text(
                        stringResource(R.string.home_updated, relative),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(UiR.drawable.ic_play), contentDescription = stringResource(UiR.string.cd_play))
            }
        }
    }
}

private val PlaylistGroup.title: Int
    get() = when (this) {
        PlaylistGroup.Charts -> R.string.group_charts
        PlaylistGroup.ForYou -> R.string.group_for_you
        PlaylistGroup.Mine -> R.string.group_mine
    }

private val AlbumListType.title: Int
    get() = when (this) {
        AlbumListType.RecentlyPlayed -> R.string.section_recently_played
        AlbumListType.RecentlyAdded -> R.string.section_recently_added
        AlbumListType.MostPlayed -> R.string.section_most_played
        AlbumListType.Random -> R.string.section_random
    }
