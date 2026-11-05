package com.github.curiousnom.kernelflasher.ui.screens.main

import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.ui.components.CopyButton
import com.github.curiousnom.kernelflasher.ui.components.SectionTitle
import com.github.curiousnom.kernelflasher.ui.components.SlotCard
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@ExperimentalSerializationApi
@Composable
fun ColumnScope.MainContent(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val enabled = !viewModel.isRefreshing
    val kernelLabel = stringResource(R.string.kernel_version)

    Card(Modifier.fillMaxWidth()) {
        BasicComponent(
            title = Build.MODEL,
            summary = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} · ${Build.DEVICE}",
            startAction = { InformationIcon(Icons.Rounded.Smartphone) }
        )
        BasicComponent(
            title = stringResource(R.string.android_version),
            summary = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            startAction = { InformationIcon(Icons.Rounded.Android) }
        )
        BasicComponent(
            title = stringResource(R.string.build_number),
            summary = Build.ID,
            startAction = { InformationIcon(Icons.Rounded.Fingerprint) }
        )
        if (viewModel.isAb) {
            BasicComponent(
                title = stringResource(R.string.slot_suffix),
                summary = viewModel.slotSuffix,
                startAction = { InformationIcon(Icons.Rounded.Layers) }
            )
        }
        BasicComponent(
            title = kernelLabel,
            summary = viewModel.kernelVersion,
            startAction = { InformationIcon(Icons.Rounded.Memory) },
            endActions = {
                CopyButton(value = viewModel.kernelVersion, label = kernelLabel)
            },
        )
    }

    Spacer(Modifier.height(12.dp))
    SlotCard(
        title = stringResource(if (viewModel.isAb) R.string.slot_a else R.string.slot),
        viewModel = viewModel.slotA,
        navController = navController
    )
    if (viewModel.isAb) {
        Spacer(Modifier.height(12.dp))
        SlotCard(
            title = stringResource(R.string.slot_b),
            viewModel = viewModel.slotB!!,
            navController = navController
        )
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle(stringResource(R.string.actions))
    Card(Modifier.fillMaxWidth()) {
        HomeAction(
            title = stringResource(R.string.backups),
            icon = Icons.Rounded.Backup,
            enabled = enabled,
            onClick = { navController.navigate("backups") }
        )
        HomeAction(
            title = stringResource(R.string.updates),
            icon = Icons.Rounded.SystemUpdate,
            enabled = enabled,
            onClick = { navController.navigate("updates") }
        )
        HomeAction(
            title = stringResource(R.string.reboot),
            icon = Icons.Rounded.PowerSettingsNew,
            enabled = enabled,
            onClick = { navController.navigate("reboot") }
        )
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle(stringResource(R.string.diagnostics))
    Card(Modifier.fillMaxWidth()) {
        if (viewModel.hasRamoops) {
            HomeAction(
                title = stringResource(R.string.save_ramoops),
                icon = Icons.Rounded.Memory,
                enabled = enabled,
                onClick = { viewModel.saveRamoops(context) }
            )
        }
        HomeAction(
            title = stringResource(R.string.save_dmesg),
            icon = Icons.Rounded.Description,
            enabled = enabled,
            onClick = { viewModel.saveDmesg(context) }
        )
        HomeAction(
            title = stringResource(R.string.save_logcat),
            icon = Icons.AutoMirrored.Rounded.Article,
            enabled = enabled,
            onClick = { viewModel.saveLogcat(context) }
        )
    }
}

@Composable
private fun HomeAction(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ArrowPreference(
        title = title,
        startAction = { InformationIcon(icon, enabled) },
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun InformationIcon(
    icon: ImageVector,
    enabled: Boolean = true
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = if (enabled) MiuixTheme.colorScheme.onSurfaceContainer
        else MiuixTheme.colorScheme.disabledOnSecondaryVariant
    )
}
