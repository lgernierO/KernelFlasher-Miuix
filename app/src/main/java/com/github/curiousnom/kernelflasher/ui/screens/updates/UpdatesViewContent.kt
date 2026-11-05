package com.github.curiousnom.kernelflasher.ui.screens.updates

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.common.types.room.updates.DateSerializer
import com.github.curiousnom.kernelflasher.common.types.room.updates.Update
import com.github.curiousnom.kernelflasher.ui.components.DataCard
import com.github.curiousnom.kernelflasher.ui.components.DataRow
import com.github.curiousnom.kernelflasher.ui.components.SectionTitle
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@ExperimentalSerializationApi
@Composable
fun ColumnScope.UpdatesViewContent(
    viewModel: UpdatesViewModel,
    navController: NavController,
    currentUpdate: Update?
) {
    val context = LocalContext.current
    currentUpdate?.let { currentUpdate ->
        DataCard(
            title = currentUpdate.kernelName,
            icon = Icons.Rounded.SystemUpdate
        ) {
            DataRow(stringResource(R.string.version), currentUpdate.kernelVersion)
            DataRow(
                stringResource(R.string.date_released),
                DateSerializer.formatter.format(currentUpdate.kernelDate)
            )
            currentUpdate.lastUpdated?.let { updated ->
                DataRow(
                    label = stringResource(R.string.last_updated),
                    value = UpdatesViewModel.lastUpdatedFormatter.format(updated),
                    labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    valueColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            minHeight = 48.dp,
            enabled = !viewModel.isRefreshing,
            colors = ButtonDefaults.buttonColorsPrimary(),
            onClick = { viewModel.downloadKernel(context) }
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.download),
                style = MiuixTheme.textStyles.button
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle(text = stringResource(R.string.actions))
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.changelog),
                enabled = !viewModel.isRefreshing,
                startAction = {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = if (viewModel.isRefreshing) {
                            MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        } else {
                            MiuixTheme.colorScheme.onSurfaceContainer
                        },
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = {
                    viewModel.downloadChangelog {
                        navController.navigate("updates/view/${currentUpdate.id}/changelog")
                    }
                }
            )
            BasicComponent(
                title = stringResource(R.string.check_for_updates),
                enabled = !viewModel.isRefreshing,
                role = Role.Button,
                startAction = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = if (viewModel.isRefreshing) {
                            MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        } else {
                            MiuixTheme.colorScheme.onSurfaceContainer
                        },
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = { viewModel.update() }
            )
            BasicComponent(
                title = stringResource(R.string.delete),
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
                enabled = !viewModel.isRefreshing,
                role = Role.Button,
                startAction = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = if (viewModel.isRefreshing) {
                            MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        } else {
                            MiuixTheme.colorScheme.error
                        },
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = { viewModel.delete { navController.popBackStack() } }
            )
        }
    }
}
