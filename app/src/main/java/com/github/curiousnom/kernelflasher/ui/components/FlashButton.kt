package com.github.curiousnom.kernelflasher.ui.components

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.github.curiousnom.kernelflasher.MainActivity
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(
    ExperimentalSerializationApi::class,
)
@Composable
fun FlashButton(
    buttonText: String,
    callback: (uri: Uri) -> Unit,
    enabled: Boolean = true,
) {
    val mainActivity = LocalActivity.current as MainActivity
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val lifecycleState by mainActivity.lifecycle.currentStateFlow.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingUri = uri
        if (uri == null) mainActivity.isAwaitingResult = false
    }
    LaunchedEffect(pendingUri, lifecycleState) {
        val uri = pendingUri
        if (uri != null && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Keep resume refresh blocked until the picker result has been consumed.
            pendingUri = null
            try {
                callback(uri)
            } finally {
                mainActivity.isAwaitingResult = false
            }
        }
    }

    Button(
        onClick = {
            mainActivity.isAwaitingResult = true
            launcher.launch("*/*")
        },
        modifier = Modifier.fillMaxWidth(),
        minHeight = 48.dp,
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.Rounded.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = buttonText, style = MiuixTheme.textStyles.button)
    }
}
