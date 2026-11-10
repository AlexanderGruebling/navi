package dev.gruebling.navi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import dev.gruebling.navi.core.navigation.HomeRoute
import dev.gruebling.navi.core.navigation.LibraryRoute
import dev.gruebling.navi.core.navigation.SearchRoute
import dev.gruebling.navi.core.navigation.TopLevelDestination

/**
 * Navigation state: one back stack per tab, like Spotify. Switching tabs keeps each tab's history;
 * tapping the selected tab again pops it to its root; back on a tab root returns to Home.
 */
@Stable
class NaviAppState(
    private val stacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
    initialTab: TopLevelDestination,
) {
    var currentTab by mutableStateOf(initialTab)
        private set

    val currentBackStack: NavBackStack<NavKey> get() = stacks.getValue(currentTab)

    fun selectTab(tab: TopLevelDestination) {
        if (tab == currentTab) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            currentTab = tab
        }
    }

    fun navigate(route: NavKey) {
        currentBackStack.add(route)
    }

    /** Returns false when there is nothing left to pop, so the system can close the app. */
    fun goBack(): Boolean {
        val stack = currentBackStack
        return when {
            stack.size > 1 -> { stack.removeAt(stack.lastIndex); true }
            currentTab != TopLevelDestination.Home -> { currentTab = TopLevelDestination.Home; true }
            else -> false
        }
    }
}

@Composable
fun rememberNaviAppState(): NaviAppState {
    val home = rememberNavBackStack(HomeRoute)
    val search = rememberNavBackStack(SearchRoute)
    val library = rememberNavBackStack(LibraryRoute)
    var savedTab by rememberSaveable { mutableStateOf(TopLevelDestination.Home) }
    val state = androidx.compose.runtime.remember(home, search, library) {
        NaviAppState(
            stacks = mapOf(
                TopLevelDestination.Home to home,
                TopLevelDestination.Search to search,
                TopLevelDestination.Library to library,
            ),
            initialTab = savedTab,
        )
    }
    savedTab = state.currentTab
    return state
}
