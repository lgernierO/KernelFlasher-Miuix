package com.github.curiousnom.kernelflasher.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.createGraph
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

/**
 * Keeps the existing NavController as the route owner while Miuix renders the pages.
 * ViewModels retain their navigate/popUpTo calls; no AndroidX transition runs alongside NavDisplay.
 */
@Navigator.Name("miuix")
class MiuixNavigator : Navigator<MiuixNavigator.Destination>() {
    val backStack get() = state.backStack

    override fun createDestination() = Destination(this) {}

    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?,
    ) {
        entries.forEach(state::push)
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.pop(popUpTo, savedState)
    }

    override fun onLaunchSingleTop(backStackEntry: NavBackStackEntry) {
        state.onLaunchSingleTop(backStackEntry)
    }

    class Destination(
        navigator: MiuixNavigator,
        val content: @Composable (NavBackStackEntry) -> Unit,
    ) : NavDestination(navigator)
}

private class MiuixDestinationBuilder(
    navigator: MiuixNavigator,
    route: String,
    private val content: @Composable (NavBackStackEntry) -> Unit,
) : NavDestinationBuilder<MiuixNavigator.Destination>(navigator, route) {
    override fun instantiateDestination() =
        MiuixNavigator.Destination(navigator as MiuixNavigator, content)
}

fun NavGraphBuilder.miuixPage(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    destination(
        MiuixDestinationBuilder(
            provider.getNavigator(MiuixNavigator::class.java),
            route,
            content,
        )
    )
}

private data class PageKey(val entry: NavBackStackEntry) : NavKey {
    // AndroidX persists this ID with the route stack; Miuix uses it for page/saveable identity.
    override fun toString() = entry.id
}

@Composable
fun MiuixNavHost(
    navController: NavHostController,
    navigator: MiuixNavigator,
    startDestination: String,
    isBusy: Boolean,
    builder: NavGraphBuilder.() -> Unit,
) {
    val viewModelOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val busy by rememberUpdatedState(isBusy)
    val graph = remember(navController, startDestination) {
        navController.createGraph(startDestination = startDestination, builder = builder)
    }
    val displayStack = remember(navController) { navBackStackOf() }
    val syncBackStack = {
        Snapshot.withMutableSnapshot {
            val entries = navigator.backStack.value.map(::PageKey)
            if (displayStack == entries) return@withMutableSnapshot
            displayStack.clear()
            displayStack.addAll(entries)
        }
    }
    DisposableEffect(navController, navigator) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ -> syncBackStack() }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    SideEffect {
        navController.setViewModelStore(viewModelOwner.viewModelStore)
        navController.graph = graph
        syncBackStack()
    }
    if (displayStack.isNotEmpty()) {
        val swipeDirection = when {
            isBusy -> NavSwipeDirection.None
            LocalLayoutDirection.current == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            else -> NavSwipeDirection.LeftToRight
        }
        NavDisplay(
            backStack = displayStack,
            modifier = Modifier.fillMaxSize(),
            transition = NavTransitions.MiuixDefault,
            effects = NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
                blockInputDuringTransition = true,
            ),
            onBack = { if (!busy) navController.popBackStack() },
        ) {
            entry<PageKey>(
                contentKey = { it.entry.id },
                swipeDismiss = swipeDirection,
            ) { page ->
                (page.entry.destination as MiuixNavigator.Destination).content(page.entry)
            }
        }
    }
    val busyBackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = busyBackState,
        isBackEnabled = isBusy,
        onBackCompleted = {},
    )
}
