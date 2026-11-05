package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    // The page already supplies the library example's 12dp outer margin.
    // Match the 16dp inset of the following card's content.
    SmallTitle(
        text = text,
        modifier = modifier,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    )
}
