package com.github.curiousnom.kernelflasher.ui.screens.updates

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@ExperimentalSerializationApi
@Composable
fun ColumnScope.UpdatesAddContent(
    viewModel: UpdatesViewModel,
    navController: NavController
) {
    var url by rememberSaveable { mutableStateOf("") }
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }
    val normalizedUrl = url.trim()
    val parsedUrl = remember(normalizedUrl) { normalizedUrl.toHttpUrlOrNull() }
    val showError = attemptedSubmit && parsedUrl == null
    val invalidMessage = stringResource(R.string.invalid_update_url)
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSubmit = normalizedUrl.isNotEmpty() && !viewModel.isRefreshing
    val submit: () -> Unit = {
        if (canSubmit) {
            attemptedSubmit = true
            if (parsedUrl != null) {
                keyboardController?.hide()
                viewModel.add(normalizedUrl) { updateId ->
                    navController.navigate("updates/view/$updateId") { popUpTo("updates") }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp)
    ) {
        TextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.url),
            enabled = !viewModel.isRefreshing,
            colors = if (showError) {
                TextFieldDefaults.textFieldColors(
                    labelColor = MiuixTheme.colorScheme.error,
                    borderColor = MiuixTheme.colorScheme.error
                )
            } else {
                TextFieldDefaults.textFieldColors()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 12.dp)
                        .size(24.dp)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (showError) error(invalidMessage) }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (showError) invalidMessage else stringResource(R.string.updates_source_hint),
            color = if (showError) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    Spacer(Modifier.height(12.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        minHeight = 48.dp,
        enabled = canSubmit,
        colors = ButtonDefaults.buttonColorsPrimary(),
        onClick = submit
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.add),
            style = MiuixTheme.textStyles.button
        )
    }
}
