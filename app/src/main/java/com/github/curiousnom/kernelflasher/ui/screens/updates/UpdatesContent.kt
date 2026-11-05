package com.github.curiousnom.kernelflasher.ui.screens.updates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.curiousnom.kernelflasher.R
import com.github.curiousnom.kernelflasher.common.types.room.updates.DateSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@ExperimentalSerializationApi
@Composable
fun ColumnScope.UpdatesContent(
    viewModel: UpdatesViewModel,
    navController: NavController
) {
    if (viewModel.updates.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_updates_tracked),
                style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.updates_empty_summary),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        for (update in viewModel.updates.sortedByDescending { it.kernelDate }) {
            key(update.id) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = update.kernelName,
                        summary = "${stringResource(R.string.version)}: ${update.kernelVersion}\n" +
                            "${stringResource(R.string.date_released)}: " +
                            DateSerializer.formatter.format(update.kernelDate),
                        startAction = {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = if (viewModel.isRefreshing) {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceContainer
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        bottomAction = {
                            update.lastUpdated?.let { updated ->
                                Text(
                                    text = "${stringResource(R.string.last_updated)}: " +
                                        UpdatesViewModel.lastUpdatedFormatter.format(updated),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = if (viewModel.isRefreshing) {
                                        MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                    modifier = Modifier.padding(start = 32.dp),
                                )
                            }
                        },
                        enabled = !viewModel.isRefreshing,
                        onClick = { navController.navigate("updates/view/${update.id}") }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !viewModel.isRefreshing,
        minHeight = 48.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
        onClick = { navController.navigate("updates/add") }
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
