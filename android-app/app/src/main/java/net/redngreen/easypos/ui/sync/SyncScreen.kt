package net.redngreen.easypos.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.core.sync.SyncPhase

/** Sync & account screen (tasks 12.4/12.5): status, manual sync, sign-in/restore, conflicts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state)
            state.message?.let { message ->
                Text(messageText(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp))

            if (!state.signedIn) {
                Text(stringResource(R.string.sync_not_signed_in), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.sync_restore_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = state.identifier,
                    onValueChange = viewModel::setIdentifier,
                    label = { Text(stringResource(R.string.sync_identifier_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::requestCode,
                    enabled = !state.busy && state.identifier.isNotBlank(),
                ) { Text(stringResource(R.string.sync_send_code)) }

                if (state.awaitingCode) {
                    state.devCode?.let {
                        Text(
                            stringResource(R.string.sync_dev_code, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = viewModel::setCode,
                        label = { Text(stringResource(R.string.sync_code_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::signIn,
                        enabled = !state.busy && state.code.isNotBlank(),
                    ) { Text(stringResource(R.string.sync_sign_in)) }
                }
            } else {
                state.shopId?.let {
                    Text(
                        stringResource(R.string.sync_shop_id, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = viewModel::syncNow, enabled = !state.busy) {
                    Text(stringResource(R.string.sync_now))
                }
                OutlinedButton(onClick = viewModel::signOut, enabled = !state.busy) {
                    Text(stringResource(R.string.sync_sign_out))
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.sync_conflicts_title), style = MaterialTheme.typography.titleMedium)
            if (conflicts.isEmpty()) {
                Text(stringResource(R.string.sync_conflicts_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().height((conflicts.size.coerceAtMost(5) * 56).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(conflicts) { conflict ->
                        Text(
                            stringResource(
                                R.string.sync_conflict_row,
                                conflict.entity,
                                conflict.resolution,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: SyncUiState) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(phaseLabel(state.status.phase), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.sync_pending, state.status.pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.status.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun messageText(message: SyncMessage): String = when (message) {
    is SyncMessage.Pushed -> stringResource(R.string.sync_now_done, message.pushed, message.pulled)
    is SyncMessage.Restored -> stringResource(R.string.sync_restored, message.count)
    SyncMessage.SendFailed -> stringResource(R.string.sync_send_failed)
    is SyncMessage.Error -> message.text
}

@Composable
private fun phaseLabel(phase: SyncPhase): String = stringResource(
    when (phase) {
        SyncPhase.SYNCED -> R.string.sync_synced
        SyncPhase.PENDING -> R.string.sync_pending_label
        SyncPhase.SYNCING -> R.string.sync_syncing
        SyncPhase.OFFLINE -> R.string.sync_offline_label
        SyncPhase.ERROR -> R.string.sync_error
    },
)
