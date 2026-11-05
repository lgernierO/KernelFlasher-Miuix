package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CompactActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Leave room for the expanded Android touch target around the 40dp Miuix button.
    Box(
        modifier = modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            textStyle = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
        )
    }
}
