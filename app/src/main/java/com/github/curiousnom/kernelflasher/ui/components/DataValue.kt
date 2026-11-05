package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.curiousnom.kernelflasher.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RowScope.DataValue(
    value: String,
    color: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    style: TextStyle = MiuixTheme.textStyles.body2,
    clickable: Boolean = false,
) {
    var expanded by rememberSaveable(value) { mutableStateOf(false) }
    val stateLabel = stringResource(
        if (expanded) R.string.expanded_value else R.string.collapsed_value
    )
    SelectionContainer(Modifier.weight(1f)) {
        val modifier = if (clickable) {
            Modifier
                .heightIn(min = 48.dp)
                .semantics { stateDescription = stateLabel }
                .clickable(role = Role.Button) { expanded = !expanded }
        } else {
            Modifier
        }
        Box(
            modifier = modifier,
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value,
                color = color,
                style = style,
                maxLines = if (clickable && !expanded) 2 else Int.MAX_VALUE,
                overflow = if (clickable && !expanded) TextOverflow.Ellipsis else TextOverflow.Visible,
            )
        }
    }
}
