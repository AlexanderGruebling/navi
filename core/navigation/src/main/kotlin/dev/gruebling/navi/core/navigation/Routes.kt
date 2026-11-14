package dev.gruebling.navi.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The three bottom-navigation tabs. Each tab keeps its own back stack. */
enum class TopLevelDestination(val route: NavKey) {
    Home(HomeRoute),
    Search(SearchRoute),
    Library(LibraryRoute),
}

@Serializable data object HomeRoute : NavKey
@Serializable data object SearchRoute : NavKey
@Serializable data object LibraryRoute : NavKey

@Serializable data class AlbumRoute(val albumId: String) : NavKey
@Serializable data class ArtistRoute(val artistId: String) : NavKey
@Serializable data class PlaylistRoute(val playlistId: String) : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object NewReleasesRoute : NavKey
@Serializable data class NewReleaseRoute(val releaseId: String) : NavKey
