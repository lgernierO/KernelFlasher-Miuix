package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DataSet(
    label: String,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Spacer(Modifier.height(12.dp))
    SmallTitle(
        text = label,
        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        insideMargin = PaddingValues(0.dp),
    )
    Column(Modifier.padding(top = 4.dp)) {
        content()
    }
}
