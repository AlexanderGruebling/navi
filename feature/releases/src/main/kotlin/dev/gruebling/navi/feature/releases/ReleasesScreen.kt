package dev.gruebling.navi.feature.releases

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.NewRelease
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.NaviChip
import dev.gruebling.navi.core.ui.component.ReleaseCover
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.StatusDot
import dev.gruebling.navi.core.ui.component.dashedOutline
import dev.gruebling.navi.core.ui.component.dotGrid
import dev.gruebling.navi.core.ui.component.releaseMeta
import dev.gruebling.navi.core.ui.component.releaseTypeName
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviType
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun ReleasesScreen(
    onBack: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: ReleasesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReleasesContent(
        state = state,
        onBack = onBack,
        onRefresh = { viewModel.refresh() },
        onFilter = viewModel::setFilter,
        onMarkAllSeen = viewModel::markAllSeen,
        onOpen = { release ->
            // In the library by now: straight to the real album.
            val albumId = release.libraryAlbumId
            if (release.inLibrary && albumId != null) onOpenAlbum(albumId.value) else onOpenRelease(release.id)
        },
        onToggleSaved = viewModel::toggleSaved,
        onPlay = viewModel::play,
    )
}

@Composable
internal fun ReleasesContent(
    state: ReleasesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (ReleaseFilter) -> Unit,
    onMarkAllSeen: () -> Unit,
    onOpen: (NewRelease) -> Unit,
    onToggleSaved: (NewRelease) -> Unit,
    onPlay: (NewRelease) -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "top", contentType = "top") {
                TopRow(unseen = state.unseenCount, onBack = onBack, onMarkAllSeen = onMarkAllSeen)
            }
            item(key = "title", contentType = "title") {
                Column(
                    Modifier.padding(horizontal = ScreenPadding).padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.releases_title), style = NaviType.dotTitle)
                    MonoLabel(stringResource(R.string.releases_label))
                }
            }
            item(key = "info", contentType = "info") { InfoBox() }
            item(key = "filters", contentType = "filters") { Filters(state.filter, onFilter) }
            if (state.refreshFailed) {
                item(key = "failed", contentType = "banner") {
                    Note(stringResource(if (state.feedEmpty) R.string.releases_failed_empty else R.string.releases_failed))
                }
            }
            when {
                state.isLoadingFirstTime -> item(key = "loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.feedEmpty && !state.refreshFailed -> item(key = "empty", contentType = "banner") {
                    Note(stringResource(R.string.releases_empty))
                }
                state.thisWeek.isEmpty() && state.earlier.isEmpty() && !state.feedEmpty -> item(key = "empty-filter", contentType = "banner") {
                    Note(stringResource(R.string.releases_empty_filter))
                }
            }
            section("week", R.string.releases_this_week, state.thisWeek, onOpen, onToggleSaved, onPlay)
            section("earlier", R.string.releases_earlier, state.earlier, onOpen, onToggleSaved, onPlay)
        }
    }
}

private fun LazyListScope.section(
    key: String,
    title: Int,
    releases: List<NewRelease>,
    onOpen: (NewRelease) -> Unit,
    onToggleSaved: (NewRelease) -> Unit,
    onPlay: (NewRelease) -> Unit,
) {
    if (releases.isEmpty()) return
    item(key = "header-$key", contentType = "sectionHeader") {
        MonoLabel(
            stringResource(title),
            Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 18.dp, bottom = 4.dp),
        )
    }
    items(releases, key = { it.id }, contentType = { "release" }) { release ->
        ReleaseRow(release, onOpen = { onOpen(release) }, onToggleSaved = { onToggleSaved(release) }, onPlay = { onPlay(release) })
    }
}

@Composable
private fun TopRow(unseen: Int, onBack: () -> Unit, onMarkAllSeen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(UiR.drawable.ic_arrow_back), contentDescription = stringResource(UiR.string.cd_back))
        }
        Spacer(Modifier.weight(1f))
        if (unseen > 0) {
            Row(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onMarkAllSeen)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(UiR.drawable.ic_check), null, tint = NaviColors.TextSecondary, modifier = Modifier.size(18.dp))
                MonoLabel(stringResource(R.string.releases_mark_all_seen))
            }
        }
    }
}

@Composable
private fun InfoBox() {
    Row(
        Modifier
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 18.dp)
            .fillMaxWidth()
            .dashedOutline(16.dp, color = NaviColors.Outline, width = 1.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).dotGrid(background = NaviColors.Black).dashedOutline(8.dp))
        Text(
            stringResource(R.string.releases_info),
            style = MaterialTheme.typography.bodySmall,
            color = NaviColors.TextSecondary,
        )
    }
}

@Composable
private fun Filters(selected: ReleaseFilter, onFilter: (ReleaseFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReleaseFilter.entries.forEach { filter ->
            NaviChip(label = stringResource(filter.label), selected = filter == selected, onClick = { onFilter(filter) })
        }
    }
}

private val ReleaseFilter.label: Int
    get() = when (this) {
        ReleaseFilter.All -> R.string.releases_filter_all
        ReleaseFilter.Albums -> R.string.releases_filter_albums
        ReleaseFilter.Eps -> R.string.releases_filter_eps
        ReleaseFilter.Singles -> R.string.releases_filter_singles
        ReleaseFilter.Saved -> R.string.releases_filter_saved
    }

@Composable
private fun ReleaseRow(release: NewRelease, onOpen: () -> Unit, onToggleSaved: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(80.dp).padding(start = ScreenPadding, end = ScreenPadding - 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReleaseCover(release.coverUrl, inLibrary = release.inLibrary, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        release.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!release.seen) {
                        val unseen = stringResource(R.string.releases_unseen)
                        StatusDot(Modifier.semantics { contentDescription = unseen })
                    }
                }
                Text(
                    release.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NaviColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (release.inLibrary) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusDot(size = 5.dp)
                        MonoLabel(
                            stringResource(R.string.releases_now_in_library) + " · " + releaseTypeName(release.type),
                            color = NaviColors.White,
                        )
                    }
                } else {
                    MonoLabel(releaseMeta(release), color = NaviColors.TextTertiary)
                }
            }
        }
        if (release.inLibrary) {
            // White play circle, as in the design (the accent stays for status dots).
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NaviColors.White)
                    .clickable(role = Role.Button, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(UiR.drawable.ic_play),
                    contentDescription = stringResource(UiR.string.cd_play),
                    tint = NaviColors.Black,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            SaveButton(saved = release.saved, onToggle = onToggleSaved)
        }
    }
}

@Composable
private fun SaveButton(saved: Boolean, onToggle: () -> Unit) {
    val label = stringResource(if (saved) R.string.releases_unsave else R.string.releases_save)
    IconButton(onClick = onToggle, modifier = Modifier.semantics { stateDescription = label }) {
        Icon(
            painterResource(if (saved) UiR.drawable.ic_bookmark_filled else UiR.drawable.ic_bookmark),
            contentDescription = label,
            tint = if (saved) NaviColors.White else NaviColors.TextSecondary,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = NaviColors.TextSecondary,
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
    )
}
