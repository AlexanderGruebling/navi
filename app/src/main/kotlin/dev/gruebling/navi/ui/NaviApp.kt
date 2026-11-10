package dev.gruebling.navi.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
import dev.gruebling.navi.core.ui.PlaceholderScreen
import dev.gruebling.navi.feature.detail.AlbumScreen
import dev.gruebling.navi.feature.detail.PlaylistScreen
import dev.gruebling.navi.feature.home.HomeScreen
import dev.gruebling.navi.feature.library.LibraryScreen
import dev.gruebling.navi.feature.player.MiniPlayer
import dev.gruebling.navi.feature.search.SearchScreen
import dev.gruebling.navi.core.ui.R as UiR

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PlaybackEntryPoint {
    fun playbackController(): PlaybackController
}

@Composable
fun NaviApp(appState: NaviAppState = rememberNaviAppState()) {
    val activity = LocalActivity.current

    // Connect to the playback service after the first frame: restores the last queue into the mini player
    // without delaying startup.
    LaunchedEffect(Unit) {
        activity?.let {
            EntryPointAccessors.fromApplication(it.applicationContext, PlaybackEntryPoint::class.java)
                .playbackController().connect()
        }
    }

    Scaffold(
        // Screens handle the status bar themselves (headers draw behind it); the bottom bar handles the nav bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                MiniPlayer(onExpand = { /* full player: next step */ })
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
                entry<SearchRoute> { SearchScreen() }
                entry<LibraryRoute> { LibraryScreen() }
                entry<PlaylistRoute> { PlaylistScreen(it.playlistId, onBack = { appState.goBack() }) }
                entry<AlbumRoute> { AlbumScreen(it.albumId, onBack = { appState.goBack() }) }
                entry<ArtistRoute> { PlaceholderScreen("Artist") }
                entry<SettingsRoute> { PlaceholderScreen("Settings") }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}

@Composable
private fun NaviNavigationBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon(selected)), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

private val TopLevelDestination.label: Int
    get() = when (this) {
        TopLevelDestination.Home -> R.string.nav_home
        TopLevelDestination.Search -> R.string.nav_search
        TopLevelDestination.Library -> R.string.nav_library
    }

private fun TopLevelDestination.icon(selected: Boolean): Int = when (this) {
    TopLevelDestination.Home -> if (selected) UiR.drawable.ic_home_filled else UiR.drawable.ic_home_outlined
    TopLevelDestination.Search -> UiR.drawable.ic_search
    TopLevelDestination.Library -> if (selected) UiR.drawable.ic_library_filled else UiR.drawable.ic_library_outlined
}
