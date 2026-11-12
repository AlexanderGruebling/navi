package dev.gruebling.navi.feature.home

import android.text.format.DateUtils
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.AlbumListType
import dev.gruebling.navi.core.model.Playlist
import dev.gruebling.navi.core.model.PlaylistGroup
import dev.gruebling.navi.core.model.PlaylistId
import dev.gruebling.navi.core.ui.component.AccentPlayButton
import dev.gruebling.navi.core.ui.component.CoverTile
import dev.gruebling.navi.core.ui.component.LetterAvatar
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.NewBadge
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.SectionHeader
import dev.gruebling.navi.core.ui.component.ShortcutTile
import dev.gruebling.navi.core.ui.component.StatusLabel
import dev.gruebling.navi.core.ui.image.CoverArt
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviType
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
                HomeHeader(state, onOpenSettings)
            }
            if (state.refreshFailed) {
                item(key = "offline", contentType = "banner") {
                    Text(
                        stringResource(R.string.home_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = NaviColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
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
                    SectionHeader(stringResource(section.group.title), label = section.group.label?.let { stringResource(it) })
                }
                when (section.group) {
                    // Charts as wide cards: a new chart week has to be impossible to miss.
                    PlaylistGroup.Charts -> items(section.playlists, key = { "chart-${it.id.value}" }, contentType = { "chartCard" }) { playlist ->
                        ChartCard(
                            playlist = playlist,
                            onOpen = { onOpenPlaylist(playlist.id.value) },
                            onPlay = { onPlayPlaylist(playlist.id) },
                            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
                        )
                    }
                    // Own playlists as compact two-column shortcuts.
                    PlaylistGroup.Mine -> items(
                        section.playlists.chunked(2),
                        key = { "mine-${it.first().id.value}" },
                        contentType = { "shortcutRow" },
                    ) { pair ->
                        Row(
                            Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { playlist ->
                                ShortcutTile(
                                    title = playlist.name,
                                    coverArtId = playlist.coverArt?.value,
                                    isNew = playlist.hasNewContent,
                                    onClick = { onOpenPlaylist(playlist.id.value) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    PlaylistGroup.ForYou -> item(key = "row-${section.group}", contentType = "playlistRow") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
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
                    SectionHeader(stringResource(section.type.title), label = stringResource(R.string.home_label_albums))
                }
                item(key = "albums-${section.type}", contentType = "albumRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.albums, key = { it.id.value }, contentType = { "albumTile" }) { album ->
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
        }
    }
}

/** Status line ("● MUSIC.HOME · ONLINE"), dot-matrix greeting, and the avatar that opens the settings. */
@Composable
private fun HomeHeader(state: HomeUiState, onOpenSettings: () -> Unit) {
    val greeting = stringResource(
        remember {
            when (LocalTime.now().hour) {
                in 5..10 -> R.string.greeting_morning
                in 11..17 -> R.string.greeting_day
                in 18..22 -> R.string.greeting_evening
                else -> R.string.greeting_night
            }
        },
    )
    val name = state.username?.replaceFirstChar { it.titlecase() }
    val settingsLabel = stringResource(UiR.string.cd_settings)
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Until the first refresh has finished, only the host: claiming "online" would be a guess.
            val status = when (state.serverReachable) {
                true -> stringResource(R.string.home_status_online)
                false -> stringResource(R.string.home_status_offline)
                null -> null
            }
            StatusLabel(
                listOfNotNull(state.serverHost, status).joinToString(" · "),
                dotColor = if (state.serverReachable == true) MaterialTheme.colorScheme.primary else NaviColors.TextTertiary,
            )
            Text(
                if (name != null) stringResource(R.string.home_greeting_name, greeting, name) else greeting,
                style = NaviType.dotTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenSettings)
                // Read as "Settings", not as the letter.
                .clearAndSetSemantics { contentDescription = settingsLabel },
            contentAlignment = Alignment.Center,
        ) {
            if (name != null) {
                LetterAvatar(name)
            } else {
                Icon(painterResource(UiR.drawable.ic_settings), contentDescription = null)
            }
        }
    }
}

/** A chart playlist as a wide card: cover, "NEW" badge, title, comment, last update, and a red play button. */
@Composable
private fun ChartCard(playlist: Playlist, onOpen: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(NaviColors.Surface1)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoverArt(playlist.coverArt?.value, 88.dp, Modifier.size(88.dp), shape = MaterialTheme.shapes.small)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (playlist.hasNewContent) NewBadge()
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playlist.comment?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = NaviColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            playlist.changed?.let { changed ->
                val relative = remember(changed) {
                    DateUtils.getRelativeTimeSpanString(changed.toEpochMilliseconds()).toString()
                }
                MonoLabel(stringResource(R.string.home_updated, relative))
            }
        }
        AccentPlayButton(onClick = onPlay, size = 48.dp)
    }
}

/** Mono label on the right of a group's header. */
private val PlaylistGroup.label: Int?
    get() = when (this) {
        PlaylistGroup.Charts -> R.string.home_label_auto
        PlaylistGroup.ForYou -> R.string.home_label_smart
        PlaylistGroup.Mine -> null
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
