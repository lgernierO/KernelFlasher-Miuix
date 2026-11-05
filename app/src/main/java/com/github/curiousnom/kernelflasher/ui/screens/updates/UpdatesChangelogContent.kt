package com.github.curiousnom.kernelflasher.ui.screens.updates

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.curiousnom.kernelflasher.common.types.room.updates.Update
import com.github.curiousnom.kernelflasher.ui.components.DataCard
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@ExperimentalSerializationApi
@Composable
fun UpdatesChangelogContent(
    currentUpdate: Update?,
    changelog: String
) {
    currentUpdate?.let { update ->
        DataCard(
            title = update.kernelName,
            icon = Icons.Rounded.Description
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            SelectionContainer {
                Text(
                    text = changelog,
                    style = MiuixTheme.textStyles.paragraph,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
