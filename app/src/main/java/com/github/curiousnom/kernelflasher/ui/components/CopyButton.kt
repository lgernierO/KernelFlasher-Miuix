package com.github.curiousnom.kernelflasher.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.curiousnom.kernelflasher.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CopyButton(
    value: String,
    label: String,
) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.copy_value, label)
    val copiedMessage = stringResource(R.string.value_copied, label)
    IconButton(
        minWidth = 48.dp,
        minHeight = 48.dp,
        onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = copyLabel,
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(24.dp),
        )
    }
}
