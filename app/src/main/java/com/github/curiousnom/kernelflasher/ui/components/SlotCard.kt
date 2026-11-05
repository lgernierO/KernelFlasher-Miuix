package com.github.curiousnom.kernelflasher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.ui.screens.slot.SlotViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SlotCard(
    title: String,
    viewModel: SlotViewModel,
    navController: NavController,
    isSlotScreen: Boolean = false,
    showDlkm: Boolean = true,
) {
    Card(Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = if (viewModel.isActive) stringResource(R.string.active) else null,
            summaryColor = BasicComponentDefaults.summaryColor(
                color = MiuixTheme.colorScheme.primary,
            ),
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.size(24.dp),
                )
            },
            endActions = if (!isSlotScreen && !viewModel.hasError && !viewModel.isRefreshing) {
                {
                    CompactActionButton(
                        text = stringResource(R.string.view),
                        enabled = !viewModel.isRefreshing,
                        onClick = { navController.navigate("slot${viewModel.slotSuffix}") },
                    )
                }
            } else null,
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (!viewModel.hasError) {
                val hashLabel = stringResource(R.string.boot_sha1)
                BasicComponent(
                    insideMargin = PaddingValues(0.dp),
                    endActions = { CopyButton(value = viewModel.sha1, label = hashLabel) },
                ) {
                    Text(
                        text = hashLabel,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = viewModel.sha1,
                        style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (showDlkm && viewModel.hasVendorDlkm) {
                    Spacer(Modifier.height(8.dp))
                    DataRow(
                        label = stringResource(R.string.vendor_dlkm),
                        value = stringResource(
                            if (!viewModel.isVendorDlkmMapped) R.string.not_found
                            else if (viewModel.isVendorDlkmMounted) R.string.mounted
                            else R.string.unmounted
                        ),
                    )
                }
                if (isSlotScreen && viewModel.kernelVersion != null) {
                    DataRow(
                        label = stringResource(R.string.kernel_version),
                        value = viewModel.kernelVersion.orEmpty(),
                        clickable = true,
                    )
                }
            } else {
                Text(
                    text = viewModel.error,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
    }
}
