package dev.gruebling.navi.feature.releases

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gruebling.navi.core.model.NewRelease
import dev.gruebling.navi.core.ui.component.AccentPlayButton
import dev.gruebling.navi.core.ui.component.MonoLabel
import dev.gruebling.navi.core.ui.component.ReleaseCover
import dev.gruebling.navi.core.ui.component.ScreenPadding
import dev.gruebling.navi.core.ui.component.dashedOutline
import dev.gruebling.navi.core.ui.component.formatTrackTime
import dev.gruebling.navi.core.ui.component.releaseMeta
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviType
import dev.gruebling.navi.core.ui.R as UiR

@Composable
fun ReleaseDetailScreen(releaseId: String, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val viewModel = hiltViewModel<ReleaseDetailViewModel, ReleaseDetailViewModel.Factory>(key = "release-$releaseId") {
        it.create(releaseId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    ReleaseDetailContent(
        state = state,
        onBack = onBack,
        onOpenExternal = { link ->
            viewModel.markSeen()
            runCatching { uriHandler.openUri(link) }
        },
        onToggleSaved = viewModel::toggleSaved,
        onToggleSeen = viewModel::toggleSeen,
        onPlay = viewModel::play,
        onOpenAlbum = onOpenAlbum,
    )
}

@Composable
internal fun ReleaseDetailContent(
    state: ReleaseDetailUiState?,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleSeen: () -> Unit,
    onPlay: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val release = state?.release
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(link = release?.link?.ifBlank { null }, onBack = onBack, onOpenExternal = onOpenExternal)
        when {
            release != null -> ReleaseBody(release, state.serverHost, onToggleSaved, onToggleSeen, onPlay, onOpenAlbum)
            state?.missing == true -> Text(
                stringResource(R.string.release_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = NaviColors.TextSecondary,
                modifier = Modifier.padding(ScreenPadding),
            )
        }
    }
}

@Composable
private fun TopBar(link: String?, onBack: () -> Unit, onOpenExternal: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(UiR.drawable.ic_arrow_back), contentDescription = stringResource(UiR.string.cd_back))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            MonoLabel(stringResource(R.string.release_label))
        }
        if (link != null) {
            IconButton(onClick = { onOpenExternal(link) }) {
                Icon(painterResource(UiR.drawable.ic_open_external), contentDescription = stringResource(R.string.release_open_external))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun ReleaseBody(
    release: NewRelease,
    serverHost: String?,
    onToggleSaved: () -> Unit,
    onToggleSeen: () -> Unit,
    onPlay: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            ReleaseCover(release.coverUrlLarge, inLibrary = release.inLibrary, size = 220.dp, cornerRadius = 24.dp)
            if (!release.inLibrary) {
                MonoLabel(
                    stringResource(R.string.release_info_only),
                    color = NaviColors.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 12.dp)
                        .background(NaviColors.Black, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(release.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp), fontWeight = FontWeight.Bold)
            Text(release.artist, style = MaterialTheme.typography.bodyLarge, color = NaviColors.TextSecondary)
            MonoLabel(releaseMeta(release, withYear = true), color = NaviColors.TextTertiary)
        }

        if (release.inLibrary) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentPlayButton(onClick = onPlay, size = 48.dp)
                release.libraryAlbumId?.let { id ->
                    PillButton(
                        text = stringResource(R.string.release_open_album),
                        selected = false,
                        onClick = { onOpenAlbum(id.value) },
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = stringResource(if (release.saved) R.string.release_saved else R.string.releases_save),
                    icon = if (release.saved) UiR.drawable.ic_bookmark_filled else UiR.drawable.ic_bookmark,
                    selected = release.saved,
                    onClick = onToggleSaved,
                    strongOutline = true,
                )
                PillButton(
                    text = stringResource(if (release.seen) R.string.release_seen else R.string.release_mark_seen),
                    icon = UiR.drawable.ic_check,
                    selected = false,
                    dimmed = release.seen,
                    onClick = onToggleSeen,
                    toggled = release.seen,
                )
            }
        }

        Notice(
            if (release.inLibrary) {
                stringResource(R.string.release_in_library)
            } else if (serverHost != null) {
                stringResource(R.string.release_not_in_library, serverHost)
            } else {
                stringResource(R.string.release_not_in_library_no_host)
            },
        )

        if (release.tracks.isNotEmpty()) {
            Column {
                MonoLabel(stringResource(R.string.release_tracklist), Modifier.padding(bottom = 6.dp))
                release.tracks.forEachIndexed { index, track ->
                    Row(
                        Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            style = NaviType.dotNumber,
                            color = NaviColors.TextTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            // #BDBDBD-ish grey of the design: info only, not playable.
                            color = NaviColors.White.copy(alpha = 0.74f),
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (track.durationSec > 0) {
                            MonoLabel(formatTrackTime(track.durationSec * 1000L), color = NaviColors.TextTertiary)
                        }
                    }
                    HorizontalDivider(color = NaviColors.Hairline)
                }
            }
        }
        release.label?.let { MonoLabel("℗ $it", color = NaviColors.TextTertiary) }
    }
}

/** 48 dp pill with an optional leading icon; white when [selected]. */
@Composable
private fun PillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: Int? = null,
    dimmed: Boolean = false,
    strongOutline: Boolean = false,
    toggled: Boolean? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val fg = when {
        selected -> NaviColors.Black
        dimmed -> NaviColors.TextSecondary
        else -> NaviColors.White
    }
    Row(
        Modifier
            .height(48.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(NaviColors.White, shape) else Modifier)
            .border(BorderStroke(1.dp, if (selected || strongOutline) NaviColors.White else NaviColors.Outline), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (toggled != null) {
                    Modifier.semantics { toggleableState = ToggleableState(toggled); stateDescription = text }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp)) }
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = NaviColors.White.copy(alpha = 0.74f),
        modifier = Modifier
            .fillMaxWidth()
            .dashedOutline(16.dp, color = NaviColors.Outline, width = 1.dp)
            .padding(14.dp),
    )
}
