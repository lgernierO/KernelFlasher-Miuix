package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DataRow(
    label: String,
    value: String,
    labelColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    labelStyle: TextStyle = MiuixTheme.textStyles.body2,
    valueColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    valueStyle: TextStyle = MiuixTheme.textStyles.body2,
    clickable: Boolean = false,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (maxWidth < 360.dp || density.fontScale > 1.2f) {
            Column {
                Text(text = label, color = labelColor, style = labelStyle)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    DataValue(value, valueColor, valueStyle, clickable)
                }
            }
        } else {
            val labelWidth = maxWidth * 0.34f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.width(labelWidth),
                    text = label,
                    color = labelColor,
                    style = labelStyle
                )
                Spacer(Modifier.width(8.dp))
                DataValue(value, valueColor, valueStyle, clickable)
            }
        }
    }
}
