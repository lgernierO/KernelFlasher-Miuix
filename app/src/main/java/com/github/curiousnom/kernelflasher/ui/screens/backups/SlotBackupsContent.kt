package com.github.curiousnom.kernelflasher.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.common.PartitionUtil
import com.github.curiousnom.kernelflasher.ui.components.FlashList
import com.github.curiousnom.kernelflasher.ui.components.SectionTitle
import com.github.curiousnom.kernelflasher.ui.components.SlotCard
import com.github.curiousnom.kernelflasher.ui.screens.slot.SlotViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ColumnScope.SlotBackupsContent(
    slotViewModel: SlotViewModel,
    backupsViewModel: BackupsViewModel,
    slotSuffix: String,
    navController: NavController,
    currentRoute: String,
    backupId: String?,
) {
    val context = LocalContext.current
    val currentBackup = backupsViewModel.backups[backupId]

    when {
        !currentRoute.contains("/backups/{backupId}/restore") -> {
            SlotCard(
                title = stringResource(
                    if (slotSuffix == "_a") R.string.slot_a
                    else if (slotSuffix == "_b") R.string.slot_b
                    else R.string.slot
                ),
                viewModel = slotViewModel,
                navController = navController,
                isSlotScreen = true,
                showDlkm = false,
            )
            Spacer(Modifier.height(12.dp))
            if (backupId != null && currentBackup != null) {
                BackupInfoCard(backupId, currentBackup)
                AnimatedVisibility(!slotViewModel.isRefreshing) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (currentBackup.type == "raw") {
                            TextButton(
                                text = stringResource(R.string.restore),
                                onClick = {
                                    navController.navigate("slot$slotSuffix/backups/$backupId/restore")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minHeight = 48.dp,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                enabled = !slotViewModel.isRefreshing,
                            )
                        } else if (currentBackup.type == "ak3") {
                            TextButton(
                                text = stringResource(R.string.flash),
                                onClick = {
                                    slotViewModel.flashAk3(context, backupId, currentBackup.filename!!)
                                    navController.navigate("slot$slotSuffix/backups/$backupId/flash/ak3") {
                                        popUpTo("slot$slotSuffix")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minHeight = 48.dp,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                enabled = !slotViewModel.isRefreshing,
                            )
                        }
                        DeleteBackupButton(enabled = !backupsViewModel.isRefreshing) {
                            backupsViewModel.delete(context) { navController.popBackStack() }
                        }
                    }
                }
            } else {
                val matchingBackups = backupsViewModel.backups.filter {
                    it.value.bootSha1.equals(slotViewModel.sha1) || it.value.type == "ak3"
                }
                BackupList(
                    backups = matchingBackups,
                    enabled = !slotViewModel.isRefreshing,
                    onSelect = { id -> navController.navigate("slot$slotSuffix/backups/$id") },
                )
            }
        }
        currentRoute.endsWith("/backups/{backupId}/restore") -> {
            if (backupId == null || currentBackup == null) return
            SectionTitle(stringResource(R.string.select_restore_partitions))
            val hashes = currentBackup.hashes
            if (hashes != null) {
                Card(Modifier.fillMaxWidth()) {
                    for (partitionName in PartitionUtil.PartitionNames) {
                        if (hashes.get(partitionName) != null) {
                            CheckboxPreference(
                                title = partitionName,
                                checked = backupsViewModel.backupPartitions[partitionName] == true,
                                onCheckedChange = { selected ->
                                    backupsViewModel.backupPartitions[partitionName] = selected
                                },
                                checkboxLocation = CheckboxLocation.End,
                                enabled = !backupsViewModel.isRefreshing,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.partition_selection_unavailable),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            Spacer(Modifier.height(12.dp))
            val hasValidSelection = hashes == null ||
                (PartitionUtil.PartitionNames.none {
                    hashes.get(it) != null && backupsViewModel.backupPartitions[it] == null
                } && backupsViewModel.backupPartitions.any { it.value })
            TextButton(
                text = stringResource(R.string.restore),
                onClick = {
                    backupsViewModel.restore(context, slotSuffix)
                    navController.navigate("slot$slotSuffix/backups/$backupId/restore/restore") {
                        popUpTo("slot$slotSuffix")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = !backupsViewModel.isRefreshing && hasValidSelection,
            )
        }
        else -> {
            val isRunning = backupsViewModel.isRefreshing || backupsViewModel.wasRestored == null
            val isSuccess = backupsViewModel.wasRestored
            FlashList(
                cardTitle = stringResource(R.string.restore),
                output = backupsViewModel.restoreOutput,
                isRunning = isRunning,
                isSuccess = isSuccess,
            ) {
                AnimatedVisibility(!isRunning && isSuccess != null) {
                    if (backupsViewModel.wasRestored != false) {
                        TextButton(
                            text = stringResource(R.string.reboot),
                            onClick = { navController.navigate("reboot") },
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 48.dp,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = !backupsViewModel.isRefreshing,
                        )
                    }
                }
            }
        }
    }
}
