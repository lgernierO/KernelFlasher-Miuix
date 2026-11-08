package com.github.curiousnom.kernelflasher.ui.screens.slot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.ui.components.SectionTitle
import com.github.curiousnom.kernelflasher.ui.components.SlotCard
import com.github.curiousnom.kernelflasher.ui.components.VendorDlkmCard
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(
    ExperimentalSerializationApi::class,
)
@Composable
fun ColumnScope.SlotContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val title = stringResource(
        if (slotSuffix == "_a") R.string.slot_a
        else if (slotSuffix == "_b") R.string.slot_b
        else R.string.slot
    )

    SlotCard(
        title = title,
        viewModel = viewModel,
        navController = navController,
        isSlotScreen = true,
    )
    Spacer(Modifier.height(12.dp))
    TextButton(
        text = stringResource(R.string.flash),
        onClick = { navController.navigate("slot$slotSuffix/flash") },
        modifier = Modifier.fillMaxWidth(),
        minHeight = 48.dp,
        colors = ButtonDefaults.textButtonColorsPrimary(),
        enabled = !viewModel.isRefreshing,
    )

    AnimatedVisibility(!viewModel.isRefreshing) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle(stringResource(R.string.actions))
            Card(Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.backup),
                    enabled = !viewModel.isRefreshing,
                    startAction = {
                        Icon(
                            Icons.Rounded.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (viewModel.isRefreshing) MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            else MiuixTheme.colorScheme.onSurfaceContainer,
                        )
                    },
                    onClick = {
                        viewModel.clearFlash(context)
                        navController.navigate("slot$slotSuffix/backup")
                    },
                )
                ArrowPreference(
                    title = stringResource(R.string.restore),
                    enabled = !viewModel.isRefreshing,
                    startAction = {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (viewModel.isRefreshing) MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            else MiuixTheme.colorScheme.onSurfaceContainer,
                        )
                    },
                    onClick = { navController.navigate("slot$slotSuffix/backups") },
                )
                ArrowPreference(
                    title = stringResource(R.string.check_kernel_version),
                    enabled = !viewModel.isRefreshing,
                    startAction = {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (viewModel.isRefreshing) MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            else MiuixTheme.colorScheme.onSurfaceContainer,
                        )
                    },
                    onClick = {
                        if (!viewModel.isRefreshing) viewModel.getKernel(context)
                    },
                )
            }

            if (viewModel.hasVendorDlkm) {
                Spacer(Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.vendor_dlkm))
                key(slotSuffix, viewModel) {
                    VendorDlkmCard(
                        isMounted = viewModel.isVendorDlkmMounted,
                        isMapped = viewModel.isVendorDlkmMapped,
                        busy = viewModel.isRefreshing,
                        onUnmount = {
                            if (!viewModel.isRefreshing && viewModel.isVendorDlkmMounted) {
                                viewModel.unmountVendorDlkm(context).join()
                            }
                        },
                        onMount = { viewModel.mountVendorDlkm(context) },
                        onUnmap = { viewModel.unmapVendorDlkm(context) },
                        onMap = { viewModel.mapVendorDlkm(context) },
                    )
                }
            }
        }
    }
}
