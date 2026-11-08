package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.curiousnom.kernelflasher.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ColumnScope.FlashList(
    cardTitle: String,
    output: List<String>,
    isRunning: Boolean = false,
    isSuccess: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    var isFollowingOutput by remember { mutableStateOf(true) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged, listState.isScrollInProgress, listState.canScrollForward) {
        if (isDragged) {
            isFollowingOutput = false
        } else if (!listState.isScrollInProgress && !listState.canScrollForward) {
            isFollowingOutput = true
        }
    }
    LaunchedEffect(output.size) {
        if (output.isNotEmpty() && isFollowingOutput && !isDragged) {
            listState.scrollToItem(output.lastIndex)
        }
    }
    val status = when {
        isRunning -> stringResource(R.string.operation_running)
        isSuccess == true -> stringResource(R.string.operation_completed)
        isSuccess == false -> stringResource(R.string.operation_failed)
        else -> null
    }
    val hasFailed = !isRunning && isSuccess == false
    val windowSize = LocalWindowInfo.current.containerSize
    val windowHeight = with(LocalDensity.current) { windowSize.height.toDp() }
    val logHeight = (windowHeight * 0.45f).coerceIn(220.dp, 480.dp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(text = cardTitle)
            if (status != null) {
                Row(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        isRunning -> Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator(
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                size = 20.dp,
                            )
                        }
                        hasFailed -> Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    }
                    if (isRunning || hasFailed) Spacer(Modifier.width(8.dp))
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.body2,
                        color = if (hasFailed) MiuixTheme.colorScheme.error
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (isRunning) LinearProgressIndicator()
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(logHeight),
            state = listState,
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(output) { _, message ->
                SelectionContainer {
                    Text(
                        text = message,
                        color = if (message.contains("error", ignoreCase = true) ||
                            message.contains("fail", ignoreCase = true)
                        ) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainer,
                        style = MiuixTheme.textStyles.footnote1,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    content()
}
