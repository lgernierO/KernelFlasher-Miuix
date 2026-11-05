package com.github.curiousnom.kernelflasher.ui.screens.reboot

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.curiousnom.kernelflasher.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RebootContent(
    viewModel: RebootViewModel,
) {
    val context = LocalContext.current
    val enabled = !viewModel.isRefreshing
    @Suppress("DEPRECATION")
    val isUserspaceSupported =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.getSystemService(PowerManager::class.java)?.isRebootingUserspaceSupported == true

    Card(Modifier.fillMaxWidth()) {
        RebootItem(
            title = stringResource(R.string.reboot),
            icon = Icons.Rounded.RestartAlt,
            enabled = enabled,
            onClick = viewModel::rebootSystem,
        )
        if (isUserspaceSupported) {
            RebootItem(
                title = stringResource(R.string.reboot_userspace),
                icon = Icons.Rounded.Cached,
                enabled = enabled,
                onClick = viewModel::rebootUserspace,
            )
        }
        RebootItem(
            title = stringResource(R.string.reboot_recovery),
            icon = Icons.Rounded.SettingsBackupRestore,
            enabled = enabled,
            onClick = viewModel::rebootRecovery,
        )
        RebootItem(
            title = stringResource(R.string.reboot_bootloader),
            icon = Icons.Rounded.DeveloperMode,
            enabled = enabled,
            onClick = viewModel::rebootBootloader,
        )
        RebootItem(
            title = stringResource(R.string.reboot_download),
            icon = Icons.Rounded.Download,
            enabled = enabled,
            onClick = viewModel::rebootDownload,
        )
        RebootItem(
            title = stringResource(R.string.reboot_edl),
            icon = Icons.Rounded.FlashOn,
            enabled = enabled,
            onClick = viewModel::rebootEdl,
        )
    }
}

@Composable
private fun RebootItem(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        enabled = enabled,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MiuixTheme.colorScheme.onSurfaceContainer
                else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            )
        },
        onClick = onClick,
    )
}
