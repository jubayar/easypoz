package net.redngreen.easypos.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.redngreen.easypos.core.print.PrintState
import net.redngreen.easypos.ui.settings.PrinterSetupViewModel

/**
 * First-run wizard overlay (task 11.2). Covers the app until finished or
 * skipped; the sell screen sits underneath only once onboarding is done.
 */
@Composable
fun OnboardingWizard(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::finish) { Text(stringResource(R.string.onboarding_skip)) }
            }
            HorizontalDivider()

            when (state.step) {
                OnboardingStep.PROFILE -> ProfileStep(state, viewModel)
                OnboardingStep.PRINTER -> PrinterStep(onContinue = viewModel::toProducts)
                OnboardingStep.PRODUCTS -> ProductsStep(state, viewModel)
            }

            state.message?.let { msg ->
                Text(
                    text = when (msg) {
                        OnboardingViewModel.MESSAGE_NAME -> stringResource(R.string.onboarding_error_name)
                        OnboardingViewModel.MESSAGE_PRODUCT -> stringResource(R.string.onboarding_error_product)
                        else -> msg
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProfileStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_profile_title), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        label = { Text(stringResource(R.string.settings_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(0.7f),
    )
    OutlinedTextField(
        value = state.address,
        onValueChange = viewModel::setAddress,
        label = { Text(stringResource(R.string.settings_address)) },
        modifier = Modifier.fillMaxWidth(0.7f),
    )
    OutlinedTextField(
        value = state.phone,
        onValueChange = viewModel::setPhone,
        label = { Text(stringResource(R.string.settings_phone)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(0.7f),
    )
    Button(
        onClick = viewModel::saveProfileNext,
        enabled = !state.saving,
        modifier = Modifier.fillMaxWidth(0.4f),
    ) { Text(stringResource(R.string.onboarding_continue)) }
}

@Composable
private fun PrinterStep(
    onContinue: () -> Unit,
    viewModel: PrinterSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Text(stringResource(R.string.onboarding_printer_title), style = MaterialTheme.typography.titleMedium)
    if (!state.bluetoothAvailable) {
        Text(stringResource(R.string.printer_bt_unavailable), color = MaterialTheme.colorScheme.error)
    }
    if (state.devices.isEmpty()) {
        Text(
            stringResource(R.string.printer_no_devices),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        state.devices.forEach { device ->
            val selected = state.mac == device.address
            Card(
                onClick = { viewModel.select(device.address) },
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, modifier = Modifier.weight(1f))
                    if (selected) {
                        Text(stringResource(R.string.printer_selected), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
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
    OutlinedButton(onClick = viewModel::testPrint, enabled = state.mac != null) {
        Text(stringResource(R.string.printer_test))
    }
    when (val ps = state.printState) {
        is PrintState.Printing -> CircularProgressIndicator()
        is PrintState.Error -> Text(
            text = stringResource(R.string.printer_error),
            color = MaterialTheme.colorScheme.error,
        )
        PrintState.Idle -> Unit
    }
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(0.4f)) {
        Text(stringResource(R.string.onboarding_continue))
    }
}

@Composable
private fun ProductsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_products_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.onboarding_products_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.productName,
            onValueChange = viewModel::setProductName,
            label = { Text(stringResource(R.string.quick_sell_name_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.productPrice,
            onValueChange = viewModel::setProductPrice,
            label = { Text(stringResource(R.string.quick_sell_price_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(0.6f),
        )
        OutlinedButton(onClick = viewModel::addProduct) {
            Text(stringResource(R.string.onboarding_add_product))
        }
    }
    if (state.addedProducts > 0) {
        Text(
            text = stringResource(R.string.onboarding_products_added, state.addedProducts),
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = viewModel::finish, modifier = Modifier.fillMaxWidth(0.4f)) {
        Text(stringResource(R.string.onboarding_finish))
    }
}
