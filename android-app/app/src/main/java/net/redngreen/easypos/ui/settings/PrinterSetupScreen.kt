package net.redngreen.easypos.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.print.PrintState

/** Printer setup + test print (task 10.5, shop-settings spec). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrinterSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshDevices() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.printer_title)) },
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
            if (!state.bluetoothAvailable) {
                Text(
                    stringResource(R.string.printer_bt_unavailable),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(stringResource(R.string.printer_devices_title), style = MaterialTheme.typography.titleSmall)
            if (state.devices.isEmpty()) {
                Text(
                    stringResource(R.string.printer_no_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.devices.forEach { device ->
                    val selected = state.mac == device.address
                    Card(
                        onClick = { viewModel.select(device.address) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                Text(
                                    stringResource(R.string.printer_selected),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.printer_pair)) }

            HorizontalDivider()
            Text(stringResource(R.string.printer_width_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.widthMm < 80,
                    onClick = { viewModel.setWidth(58) },
                    label = { Text(stringResource(R.string.printer_width_58)) },
                )
                FilterChip(
                    selected = state.widthMm >= 80,
                    onClick = { viewModel.setWidth(80) },
                    label = { Text(stringResource(R.string.printer_width_80)) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.printer_drawer_kick),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.drawerKick, onCheckedChange = viewModel::setDrawerKick)
            }

            HorizontalDivider()
            Button(
                onClick = viewModel::testPrint,
                enabled = state.mac != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.printer_test)) }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val ps = state.printState) {
                    is PrintState.Printing -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.printer_printing))
                    }

                    is PrintState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.printer_error) + " · " + ps.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row {
                            if (ps.canRetry) {
                                TextButton(onClick = viewModel::testPrint) {
                                    Text(stringResource(R.string.printer_retry))
                                }
                            }
                            TextButton(onClick = viewModel::dismissPrintError) {
                                Text(stringResource(R.string.action_close))
                            }
                        }
                    }

                    PrintState.Idle -> Unit
                }
            }

            state.message?.let {
                Text(
                    text = if (it == PrinterSetupViewModel.MESSAGE_DENIED) {
                        stringResource(R.string.access_denied)
                    } else {
                        it
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
