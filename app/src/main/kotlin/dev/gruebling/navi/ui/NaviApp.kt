package dev.gruebling.navi.ui

import androidx.activity.compose.LocalActivity
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.gruebling.navi.BuildConfig
import dev.gruebling.navi.R
import dev.gruebling.navi.core.navigation.AlbumRoute
import dev.gruebling.navi.core.navigation.ArtistRoute
import dev.gruebling.navi.core.navigation.HomeRoute
import dev.gruebling.navi.core.navigation.LibraryRoute
import dev.gruebling.navi.core.navigation.PlaylistRoute
import dev.gruebling.navi.core.navigation.SearchRoute
import dev.gruebling.navi.core.navigation.SettingsRoute
import dev.gruebling.navi.core.navigation.TopLevelDestination
import dev.gruebling.navi.core.playback.PlaybackController
import dev.gruebling.navi.core.ui.theme.NaviColors
import dev.gruebling.navi.core.ui.theme.NaviType
import dev.gruebling.navi.core.ui.component.LocalAddToPlaylist
import dev.gruebling.navi.feature.addtoplaylist.AddToPlaylist
import dev.gruebling.navi.feature.detail.AlbumScreen
import dev.gruebling.navi.feature.detail.ArtistScreen
import dev.gruebling.navi.feature.detail.PlaylistScreen
import dev.gruebling.navi.feature.home.HomeScreen
import dev.gruebling.navi.feature.library.LibraryScreen
import dev.gruebling.navi.feature.player.FullPlayerHost
import dev.gruebling.navi.feature.player.MiniPlayer
import dev.gruebling.navi.feature.search.SearchScreen
import dev.gruebling.navi.feature.settings.SettingsScreen
import dev.gruebling.navi.core.download.Downloads
import dev.gruebling.navi.core.playback.PlaybackMessage
import dev.gruebling.navi.core.playback.PlaybackMessages
import dev.gruebling.navi.core.ui.R as UiR

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PlaybackEntryPoint {
    fun playbackController(): PlaybackController
    fun downloads(): Downloads
    fun playbackMessages(): PlaybackMessages
}

@Composable
fun NaviApp(appState: NaviAppState = rememberNaviAppState()) {
    val activity = LocalActivity.current

    // Connect to the playback service after the first frame: restores the last queue into the mini player
    // without delaying startup.
    LaunchedEffect(Unit) {
        activity?.let {
            val entryPoint = EntryPointAccessors.fromApplication(it.applicationContext, PlaybackEntryPoint::class.java)
            entryPoint.playbackController().connect()
            // Picks up changed playlists and finishes interrupted downloads.
            entryPoint.downloads().sync()
        }
    }
    PlaybackMessageToasts()

    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    // Songs waiting for the "Add to playlist" sheet; any screen's song menu can ask for it.
    var addToPlaylist by rememberSaveable { mutableStateOf<List<String>?>(null) }

    // Remembered: a new lambda would recompose everything below the (static) provider.
    val requestAddToPlaylist = remember { { songIds: List<String> -> addToPlaylist = songIds } }

    CompositionLocalProvider(LocalAddToPlaylist provides requestAddToPlaylist) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = NaviColors.Black,
        // Screens handle the status bar themselves (headers draw behind it); the bottom bar handles the nav bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                MiniPlayer(onExpand = { playerExpanded = true })
                NaviNavigationBar(current = appState.currentTab, onSelect = appState::selectTab)
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = appState.currentBackStack,
            onBack = { if (!appState.goBack()) activity?.finish() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        onOpenPlaylist = { appState.navigate(PlaylistRoute(it)) },
                        onOpenAlbum = { appState.navigate(AlbumRoute(it)) },
                        onOpenSettings = { appState.navigate(SettingsRoute) },
                    )
                }
                entry<SearchRoute> {
                    SearchScreen(
                        onOpenAlbum = { appState.navigate(AlbumRoute(it)) },
                        onOpenArtist = { appState.navigate(ArtistRoute(it)) },
                    )
                }
                entry<LibraryRoute> {
                    LibraryScreen(
                        onOpenPlaylist = { appState.navigate(PlaylistRoute(it)) },
                        onOpenAlbum = { appState.navigate(AlbumRoute(it)) },
                        onOpenArtist = { appState.navigate(ArtistRoute(it)) },
                    )
                }
                entry<PlaylistRoute> { PlaylistScreen(it.playlistId, onBack = { appState.goBack() }) }
                entry<AlbumRoute> { AlbumScreen(it.albumId, onBack = { appState.goBack() }) }
                entry<ArtistRoute> {
                    ArtistScreen(
                        it.artistId,
                        onBack = { appState.goBack() },
                        onOpenAlbum = { id -> appState.navigate(AlbumRoute(id)) },
                        onOpenArtist = { id -> appState.navigate(ArtistRoute(id)) },
                    )
                }
                entry<SettingsRoute> {
                    SettingsScreen(
                        versionName = BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (debug)" else "",
                        onBack = { appState.goBack() },
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
    FullPlayerHost(
        expanded = playerExpanded,
        onCollapse = { playerExpanded = false },
        onOpenAlbum = { appState.navigate(AlbumRoute(it)) },
        onOpenArtist = { appState.navigate(ArtistRoute(it)) },
    )
    AddToPlaylist(songIds = addToPlaylist, onDismiss = { addToPlaylist = null })
    }
    }
}

/**
 * Bottom navigation as in the design: black bar with a hairline on top; icon, mono caps label, and a red dot under
 * the selected tab (the dot, not only the white tint, marks it).
 */
@Composable
private fun NaviNavigationBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(NaviColors.Black)
            .drawBehind { drawLine(NaviColors.Hairline, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(76.dp).selectableGroup()) {
            TopLevelDestination.entries.forEach { tab ->
                val selected = tab == current
                val color = if (selected) NaviColors.White else NaviColors.TextTertiary
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                        .testTag("nav_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null, tint = color)
                    Text(stringResource(tab.label).uppercase(), style = NaviType.monoTiny, color = color, maxLines = 1)
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                    )
                }
            }
        }
    }
}

private val TopLevelDestination.label: Int
    get() = when (this) {
        TopLevelDestination.Home -> R.string.nav_home
        TopLevelDestination.Search -> R.string.nav_search
        TopLevelDestination.Library -> R.string.nav_library
    }

private val TopLevelDestination.icon: Int
    get() = when (this) {
        TopLevelDestination.Home -> UiR.drawable.ic_home
        TopLevelDestination.Search -> UiR.drawable.ic_search
        TopLevelDestination.Library -> UiR.drawable.ic_library
    }

/** Toasts for what the playback service reports, on every screen. */
@Composable
private fun PlaybackMessageToasts() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val messages = EntryPointAccessors.fromApplication(context.applicationContext, PlaybackEntryPoint::class.java)
            .playbackMessages().messages
        messages.collect { message ->
            val text = when (message) {
                is PlaybackMessage.SkippedNotDownloaded ->
                    context.resources.getQuantityString(UiR.plurals.skipped_not_downloaded, message.count, message.count)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}
