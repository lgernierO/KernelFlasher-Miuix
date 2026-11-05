package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium),
        color = MiuixTheme.colorScheme.onSurfaceContainer,
    )
}
