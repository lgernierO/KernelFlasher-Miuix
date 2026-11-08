package com.github.curiousnom.kernelflasher.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.ui.screens.main.MainViewModel
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@ExperimentalSerializationApi
@Composable
fun RefreshableScreen(
    viewModel: MainViewModel,
    navController: NavController,
    swipeEnabled: Boolean = false,
    title: String? = null,
    showBackButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    MiuixScreen(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.refresh(context) },
        onBack = { navController.popBackStack() },
        swipeEnabled = swipeEnabled,
        title = title,
        showBackButton = showBackButton,
        content = content,
    )
}

@Composable
fun MiuixScreen(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    swipeEnabled: Boolean = false,
    title: String? = null,
    showBackButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val compactHeight = with(LocalDensity.current) { windowSize.height.toDp() } < 480.dp
    val scrollBehavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()
    val refreshState = rememberPullToRefreshState()
    val pageTitle = title ?: stringResource(R.string.app_name)
    val refreshTexts = listOf(
        stringResource(R.string.pull_to_refresh),
        stringResource(R.string.release_to_refresh),
        stringResource(R.string.refreshing),
        stringResource(R.string.refresh_complete)
    )
    val navigationIcon: @Composable () -> Unit = {
        if (showBackButton) {
            IconButton(
                onClick = { if (!isRefreshing) onBack() },
                enabled = !isRefreshing,
                minWidth = 48.dp,
                minHeight = 48.dp
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = if (isRefreshing) {
                        MiuixTheme.colorScheme.disabledOnSurface
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            val actions: @Composable RowScope.() -> Unit = {
                if (isRefreshing && !swipeEnabled) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InfiniteProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (compactHeight) {
                SmallTopAppBar(
                    title = pageTitle,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior
                )
            } else {
                TopAppBar(
                    title = pageTitle,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { innerPadding ->
        val pageContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .scrollEndHaptic()
                        .then(
                            if (swipeEnabled) Modifier
                            else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                        )
                        .verticalScroll(scrollState)
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    content = content
                )
            }
        }
        if (swipeEnabled) {
            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = { if (!isRefreshing) onRefresh() },
                modifier = Modifier.fillMaxSize(),
                pullToRefreshState = refreshState,
                contentPadding = innerPadding,
                topAppBarScrollBehavior = scrollBehavior,
                refreshTexts = refreshTexts,
                content = pageContent
            )
        } else {
            pageContent()
        }
    }
}
