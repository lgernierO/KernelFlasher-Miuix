package com.github.curiousnom.kernelflasher.ui.navigation

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

/**
 * Business ViewModels retain their controller, so the UI must retain that same controller too.
 * A detached host pauses its lifecycle without destroying the retained route stack.
 */
class FlasherNavigationViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application), LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.CREATED
    }
    override val lifecycle: Lifecycle get() = registry

    val navigator = MiuixNavigator()
    val navController = NavHostController(application).apply {
        navigatorProvider.addNavigator(navigator)
        setLifecycleOwner(this@FlasherNavigationViewModel)
        savedStateHandle.get<Bundle>(STATE_KEY)?.let(::restoreState)
    }

    init {
        savedStateHandle.setSavedStateProvider(STATE_KEY) {
            navController.saveState() ?: Bundle()
        }
    }

    fun updateHostState(state: Lifecycle.State) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = if (state.isAtLeast(Lifecycle.State.CREATED)) {
            state
        } else {
            Lifecycle.State.CREATED
        }
    }

    override fun onCleared() {
        registry.currentState = Lifecycle.State.DESTROYED
    }

    private companion object {
        const val STATE_KEY = "flasher_navigation"
    }
}

@Composable
fun rememberFlasherNavigation(): FlasherNavigationViewModel {
    val navigation: FlasherNavigationViewModel = viewModel()
    val host = LocalLifecycleOwner.current
    DisposableEffect(navigation, host) {
        val observer = LifecycleEventObserver { _, _ ->
            navigation.updateHostState(host.lifecycle.currentState)
        }
        host.lifecycle.addObserver(observer)
        navigation.updateHostState(host.lifecycle.currentState)
        onDispose {
            host.lifecycle.removeObserver(observer)
            navigation.updateHostState(Lifecycle.State.CREATED)
        }
    }
    return navigation
}
