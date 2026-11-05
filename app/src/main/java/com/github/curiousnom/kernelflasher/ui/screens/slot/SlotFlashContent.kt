package com.github.curiousnom.kernelflasher.ui.screens.slot

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.MainActivity
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.common.PartitionUtil
import com.github.curiousnom.kernelflasher.ui.components.FlashButton
import com.github.curiousnom.kernelflasher.ui.components.FlashList
import com.github.curiousnom.kernelflasher.ui.components.SectionTitle
import com.github.curiousnom.kernelflasher.ui.components.SlotCard
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(
    ExperimentalSerializationApi::class,
)
@Composable
fun ColumnScope.SlotFlashContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController,
    currentRoute: String,
) {
    val context = LocalContext.current
    val mainActivity = LocalActivity.current as MainActivity
    var pendingAk3Uri by remember { mutableStateOf<Uri?>(null) }
    val lifecycleState by mainActivity.lifecycle.currentStateFlow.collectAsState()
    val ak3Launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingAk3Uri = uri
        if (uri == null) mainActivity.isAwaitingResult = false
    }
    LaunchedEffect(pendingAk3Uri, lifecycleState) {
        val uri = pendingAk3Uri
        if (uri != null && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Defer work until resume so automatic refresh cannot race the flash.
            pendingAk3Uri = null
            try {
                navController.navigate("slot$slotSuffix/flash/ak3") {
                    popUpTo("slot$slotSuffix")
                }
                viewModel.flashAk3(context, uri)
            } finally {
                mainActivity.isAwaitingResult = false
            }
        }
    }
    val isOperationPage = listOf("/flash/ak3", "/flash/image/flash", "/backup/backup")
        .any(currentRoute::endsWith)

    if (!isOperationPage) {
        SlotCard(
            title = stringResource(
                if (slotSuffix == "_a") R.string.slot_a
                else if (slotSuffix == "_b") R.string.slot_b
                else R.string.slot
            ),
            viewModel = viewModel,
            navController = navController,
            isSlotScreen = true,
            showDlkm = false,
        )
        Spacer(Modifier.height(12.dp))

        when {
            currentRoute.endsWith("/flash") -> {
                Card(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.flash_ak3_zip),
                        summary = stringResource(R.string.flash_ak3_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.FolderZip,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (viewModel.isRefreshing) MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                else MiuixTheme.colorScheme.onSurfaceContainer,
                            )
                        },
                        enabled = !viewModel.isRefreshing,
                        onClick = {
                            mainActivity.isAwaitingResult = true
                            ak3Launcher.launch("*/*")
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.flash_partition_image),
                        summary = stringResource(R.string.flash_image_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (viewModel.isRefreshing) MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                else MiuixTheme.colorScheme.onSurfaceContainer,
                            )
                        },
                        enabled = !viewModel.isRefreshing,
                        onClick = { navController.navigate("slot$slotSuffix/flash/image") },
                    )
                }
            }
            currentRoute.endsWith("/flash/image") -> {
                SectionTitle(stringResource(R.string.flash_partition_image))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (partitionName in PartitionUtil.AvailablePartitions) {
                        FlashButton(
                            buttonText = partitionName,
                            enabled = !viewModel.isRefreshing,
                            callback = { uri ->
                                navController.navigate("slot$slotSuffix/flash/image/flash") {
                                    popUpTo("slot$slotSuffix")
                                }
                                viewModel.flashImage(context, uri, partitionName)
                            },
                        )
                    }
                }
            }
            currentRoute.endsWith("/backup") -> {
                SectionTitle(stringResource(R.string.select_backup_partitions))
                Card(Modifier.fillMaxWidth()) {
                    for (partitionName in PartitionUtil.AvailablePartitions) {
                        CheckboxPreference(
                            title = partitionName,
                            checked = viewModel.backupPartitions[partitionName] == true,
                            onCheckedChange = { selected ->
                                viewModel.backupPartitions[partitionName] = selected
                            },
                            checkboxLocation = CheckboxLocation.End,
                            enabled = !viewModel.isRefreshing,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = stringResource(R.string.backup),
                    onClick = {
                        viewModel.backup(context)
                        navController.navigate("slot$slotSuffix/backup/backup") {
                            popUpTo("slot$slotSuffix")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = !viewModel.isRefreshing && viewModel.backupPartitions.any { it.value },
                )
            }
        }
    } else {
        val isRunning = viewModel.isRefreshing || viewModel.wasFlashSuccess == null
        val isSuccess = viewModel.wasFlashSuccess
        FlashList(
            cardTitle = stringResource(
                if (currentRoute.endsWith("/backup/backup")) R.string.backup else R.string.flash
            ),
            output = if (currentRoute.contains("ak3")) viewModel.uiPrintedOutput else viewModel.flashOutput,
            isRunning = isRunning,
            isSuccess = isSuccess,
        ) {
            AnimatedVisibility(visible = !isRunning && isSuccess != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (currentRoute.contains("ak3")) {
                        TextButton(
                            text = stringResource(R.string.save_ak3_log),
                            onClick = { viewModel.saveLog(context) },
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 48.dp,
                            enabled = !isRunning,
                        )
                        if (!currentRoute.endsWith("/backups/{backupId}/flash/ak3") &&
                            viewModel.wasFlashSuccess != false
                        ) {
                            TextButton(
                                text = stringResource(R.string.save_ak3_zip_as_backup),
                                onClick = {
                                    viewModel.backupZip(context) {
                                        navController.navigate("slot$slotSuffix/backups") {
                                            popUpTo("slot$slotSuffix")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minHeight = 48.dp,
                                enabled = !isRunning,
                            )
                        }
                    }
                    if (viewModel.wasFlashSuccess != false && currentRoute.endsWith("/backup/backup")) {
                        TextButton(
                            text = stringResource(R.string.back),
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 48.dp,
                            enabled = !isRunning,
                        )
                    } else {
                        TextButton(
                            text = stringResource(R.string.reboot),
                            onClick = { navController.navigate("reboot") },
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 48.dp,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = !isRunning,
                        )
                    }
                }
            }
        }
    }
}
