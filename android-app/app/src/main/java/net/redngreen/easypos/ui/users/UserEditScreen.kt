package net.redngreen.easypos.ui.users

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.redngreen.easypos.R
import net.redngreen.easypos.core.model.Role
import net.redngreen.easypos.core.security.PinHasher

/**
 * Create/edit a user (task 5.1). Create requires name + role + PIN; edit can
 * also reset the PIN (blank = keep) and deactivate. The last-active-admin
 * guard surfaces as a visible error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditScreen(
    userId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pin by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(userId) { viewModel.load(userId) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.user_create_title else R.string.user_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.denied -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.access_denied),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.user_name_label)) },
                    singleLine = true,
                    isError = state.error == UserEditError.INVALID_NAME,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )

                Text(stringResource(R.string.user_role_label), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Role.entries.forEach { role ->
                        FilterChip(
                            selected = state.role == role,
                            onClick = { viewModel.setRole(role) },
                            label = { Text(roleString(role)) },
                        )
                    }
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(PinHasher.PIN_MAX_LENGTH)
                        viewModel.clearError()
                    },
                    label = {
                        Text(
                            stringResource(
                                if (state.isNew) R.string.user_pin_label
                                else R.string.user_pin_new_label
                            )
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = state.error == UserEditError.INVALID_PIN,
                    supportingText = {
                        val message = when (state.error) {
                            UserEditError.INVALID_PIN -> stringResource(R.string.error_pin_invalid)
                            UserEditError.INVALID_NAME -> stringResource(R.string.error_name_required)
                            UserEditError.LAST_ADMIN -> stringResource(R.string.error_last_admin)
                            UserEditError.UNKNOWN -> stringResource(R.string.error_unknown)
                            null -> ""
                        }
                        if (message.isNotEmpty()) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.6f),
                )

                if (!state.isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = state.active, onCheckedChange = viewModel::setActive)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text(stringResource(R.string.user_active_label))
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (state.saving) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { viewModel.save(pin) },
                        // Create requires a PIN; edit keeps the old one when blank.
                        enabled = state.name.isNotBlank() && (!state.isNew || pin.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth(0.6f),
                    ) {
                        Text(stringResource(R.string.user_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun roleString(role: Role): String = when (role) {
    Role.ADMIN -> stringResource(R.string.role_admin)
    Role.MANAGER -> stringResource(R.string.role_manager)
    Role.CASHIER -> stringResource(R.string.role_cashier)
}
