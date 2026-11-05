package com.github.curiousnom.kernelflasher.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.common.PartitionUtil
import com.github.curiousnom.kernelflasher.common.types.backups.Backup
import com.github.curiousnom.kernelflasher.ui.components.CardTitle
import com.github.curiousnom.kernelflasher.ui.components.CopyButton
import com.github.curiousnom.kernelflasher.ui.components.DataRow
import com.github.curiousnom.kernelflasher.ui.components.DataSet
import com.github.curiousnom.kernelflasher.ui.components.SectionTitle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Suppress("DEPRECATION")
@Composable
fun ColumnScope.BackupsContent(
    viewModel: BackupsViewModel,
    navController: NavController,
    backupId: String?,
) {
    val context = LocalContext.current
    val currentBackup = viewModel.backups[backupId]

    if (backupId != null && currentBackup != null) {
        BackupInfoCard(backupId, currentBackup)
        AnimatedVisibility(!viewModel.isRefreshing) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))
                DeleteBackupButton(enabled = !viewModel.isRefreshing) {
                    viewModel.delete(context) { navController.popBackStack() }
                }
            }
        }
    } else {
        if (viewModel.needsMigration) {
            TextButton(
                text = stringResource(R.string.migrate),
                onClick = { viewModel.migrate(context) },
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp,
                enabled = !viewModel.isRefreshing,
            )
            Spacer(Modifier.height(12.dp))
        }
        BackupList(
            backups = viewModel.backups,
            enabled = !viewModel.isRefreshing,
            onSelect = { id -> navController.navigate("backups/$id") },
        )
    }
}

@Composable
internal fun BackupInfoCard(backupId: String, backup: Backup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            CardTitle(text = backupId)
            Spacer(Modifier.height(12.dp))
            DataRow(stringResource(R.string.backup_type), backup.type.uppercase())
            DataRow(stringResource(R.string.kernel_version), backup.kernelVersion, clickable = true)
        }
        if (backup.type == "raw") {
            val sha1 = backup.bootSha1
            if (sha1 != null) {
                BasicComponent(
                    insideMargin = PaddingValues(vertical = 8.dp),
                    endActions = {
                        CopyButton(
                            value = sha1,
                            label = stringResource(R.string.boot_sha1),
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.boot_sha1),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = sha1,
                        style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            backup.hashes?.let { hashes ->
                Column(Modifier.fillMaxWidth()) {
                    DataSet(stringResource(R.string.hashes)) {
                        for (partitionName in PartitionUtil.PartitionNames) {
                            val hash = hashes.get(partitionName)
                            if (hash != null) {
                                DataRow(
                                    label = partitionName,
                                    value = hash,
                                    valueStyle = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
                                    clickable = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackupList(
    backups: Map<String, Backup>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    if (backups.isEmpty()) {
        EmptyBackups()
    } else {
        SectionTitle(stringResource(R.string.backups))
        Card(Modifier.fillMaxWidth()) {
            for (id in backups.keys.sortedDescending()) {
                key(id) {
                    val backup = backups.getValue(id)
                    ArrowPreference(
                        title = id,
                        summary = "${backup.type.uppercase()} · ${backup.kernelVersion}",
                        startAction = {
                            Icon(
                                imageVector = if (backup.type == "raw") Icons.Rounded.Image else Icons.Rounded.FolderZip,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (enabled) MiuixTheme.colorScheme.onSurfaceContainer
                                else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                            )
                        },
                        enabled = enabled,
                        onClick = { onSelect(id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyBackups() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_backups_found),
            style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun DeleteBackupButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        text = stringResource(R.string.delete),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 48.dp,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            color = MiuixTheme.colorScheme.errorContainer,
            textColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    )
}
