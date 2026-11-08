package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.curiousnom.kernelflasher.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class VendorDlkmConfirmationState {
    var isConfirming by mutableStateOf(false)
        private set
    var isSubmitted by mutableStateOf(false)
        private set

    fun onUnmountClick(enabled: Boolean, onUnmount: () -> Unit) {
        if (!enabled) {
            clearConfirmation()
            return
        }
        if (isSubmitted) return
        if (isConfirming) {
            clearConfirmation()
            isSubmitted = true
            onUnmount()
        } else {
            isConfirming = true
        }
    }

    fun completeSubmission() {
        isSubmitted = false
    }

    fun clearConfirmation() {
        isConfirming = false
    }
}

@Composable
fun VendorDlkmCard(
    isMounted: Boolean,
    isMapped: Boolean,
    busy: Boolean,
    onUnmount: suspend () -> Unit,
    onMount: () -> Unit,
    onUnmap: () -> Unit,
    onMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val confirmation = remember(busy, isMounted, isMapped, lifecycleOwner) {
        VendorDlkmConfirmationState()
    }
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner, confirmation) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) confirmation.clearConfirmation()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            confirmation.clearConfirmation()
        }
    }

    val enabled = !busy && resumed && !confirmation.isSubmitted
    val confirming = enabled && isMounted && confirmation.isConfirming
    val colors = MiuixTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (confirming) colors.error else colors.surfaceContainer,
        animationSpec = tween(220),
        label = "vendorDlkmBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (confirming) colors.onError else colors.onSurfaceContainer,
        animationSpec = tween(220),
        label = "vendorDlkmForeground",
    )
    val confirmationText = stringResource(R.string.unmount_vendor_dlkm_confirm)
    val canAct = {
        enabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(tween(220)),
        colors = CardDefaults.defaultColors(color = background, contentColor = foreground),
    ) {
        if (isMounted) {
            BasicComponent(
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (!enabled) disabled()
                },
                title = stringResource(R.string.unmount_vendor_dlkm),
                titleColor = BasicComponentDefaults.titleColor(color = foreground),
                summary = if (confirming) confirmationText else null,
                summaryColor = BasicComponentDefaults.summaryColor(color = foreground),
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.Regular.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (enabled) foreground else colors.disabledOnSecondaryVariant,
                    )
                },
                onClick = {
                    confirmation.onUnmountClick(canAct()) {
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                onUnmount()
                            } finally {
                                confirmation.completeSubmission()
                            }
                        }
                    }
                },
                onClickLabel = if (confirming) confirmationText else null,
                role = Role.Button,
                enabled = enabled,
            )
        } else if (isMapped) {
            ArrowPreference(
                title = stringResource(R.string.mount_vendor_dlkm),
                enabled = enabled,
                onClick = { if (canAct()) onMount() },
            )
            ArrowPreference(
                title = stringResource(R.string.unmap_vendor_dlkm),
                enabled = enabled,
                onClick = { if (canAct()) onUnmap() },
            )
        } else {
            ArrowPreference(
                title = stringResource(R.string.map_vendor_dlkm),
                enabled = enabled,
                onClick = { if (canAct()) onMap() },
            )
        }
    }
}
